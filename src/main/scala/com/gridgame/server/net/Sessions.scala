package com.gridgame.server.net

import com.gridgame.common.Constants
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol.PacketSigner
import com.gridgame.common.protocol.SessionEndReason
import io.netty.channel.Channel

import java.net.InetAddress
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Who is logged in, and on what: each session's token (which signs every packet after the login),
 * the TCP channel it was issued on, the address it logged in from (UDP is only taken from there),
 * and what each channel has done wrong so far.
 *
 * A token lasts SESSION_TOKEN_LIFETIME_MS. Then a session whose client is still connected is
 * renewed rather than ended ([[renewTokens]]): the client is sent a new token, signed with the one
 * it has, and the server goes on signing with the old one until it first hears the client use the
 * new one ([[verify]]). The old one is still taken from the client for RETIRED_TOKEN_GRACE_MS
 * after that, for what it signed just before it switched. A client that hasn't switched within
 * SESSION_RENEWAL_WINDOW_MS is cut off. Ending every session once its token was an hour old, as was
 * done, threw players out to the login screen in the middle of a match.
 *
 * A session also knows the refresh-token family its login belongs to (account/RefreshTokens), so
 * a logout or a copied token can end it, and a session started by exchanging a refresh token
 * confirms the exchange when its client is first heard on it ([[heardFrom]]). A session ended by
 * the server on purpose is told why first (SESSION_END): logged in elsewhere, or logged out.
 *
 * @param rateLimiter  forgets a channel whose session a new login replaced
 * @param replay       restarts a player's packet numbering when they get a new session
 */
final class Sessions(rateLimiter: RateLimiter, replay: ReplayGuard) {
  // Channel -> the player it logged in as, for identity checks and disconnect handling
  private[server] val channelToPlayer = new ConcurrentHashMap[Channel, UUID]()
  val sessionTokens = new ConcurrentHashMap[UUID, Array[Byte]]()
  val channelToToken = new ConcurrentHashMap[Channel, Array[Byte]]()
  private val secureRandom = new SecureRandom()

  // UDP source address validation: maps player UUID -> TCP connection IP
  val playerTcpAddresses = new ConcurrentHashMap[UUID, InetAddress]()
  // When each session's current token was issued
  val tokenCreationTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  // The token a session is being renewed with, sent to its client and not yet used by it
  private[server] val renewals = new ConcurrentHashMap[UUID, Sessions.Renewal]()
  // The token a renewal replaced, and until when it is still taken from the client
  private[server] val retiredTokens = new ConcurrentHashMap[UUID, Sessions.RetiredToken]()
  // Per-channel auth failure tracking
  val channelAuthFailures = new ConcurrentHashMap[Channel, AtomicInteger]()
  // The name each logged-in player logged in with: the one they go by (GameServer.handleGlobalConnect)
  private[server] val accountNames = new ConcurrentHashMap[UUID, String]()
  // The refresh-token family each session's login belongs to
  private[server] val refreshFamilies = new ConcurrentHashMap[UUID, String]()
  // Sessions started by exchanging a refresh token whose client hasn't been heard on them yet: the
  // exchange is confirmed when it is (heardFrom)
  private[server] val unconfirmedExchanges = new ConcurrentHashMap[UUID, String]()
  // Per-channel malformed packet tracking (disconnect after too many)
  private[server] val malformedPacketCounts = new ConcurrentHashMap[Channel, AtomicInteger]()
  private[server] val MAX_MALFORMED_PACKETS = 3
  // Locks for a player's session (its tokens being issued, renewed, taken up and ended), striped. A
  // lock per player in a map, as there was, stayed there after the player left, one for every
  // account that ever logged in.
  private val sessionLocks = Array.fill(64)(new AnyRef)

  private def lockOf(playerId: UUID): AnyRef = sessionLocks(Math.floorMod(playerId.hashCode, sessionLocks.length))

  /** What to do when a login replaces a session the server still holds open: set by the server,
    * which takes the old session out of whatever it was in. */
  @volatile private[server] var onSessionReplaced: UUID => Unit = _ => ()

  /** Tell a client why the server is ending its session (SESSION_END), on the channel about to be
    * closed: set by the server, which sends it through the Outbox, signed with the channel's token. */
  @volatile private[server] var tellSessionEnded: (Channel, UUID, Byte) => Unit = (_, _, _) => ()

  /** A refresh-token exchange's client was heard on the session it started (the family's id): set
    * by the server, which confirms it in the account database. */
  @volatile private[server] var onExchangeConfirmed: String => Unit = _ => ()

  /** Has this player's session outlived its token, and the time it had to switch to a new one? */
  def isExpired(playerId: UUID, now: Long): Boolean = {
    val createdAt = tokenCreationTime.get(playerId)
    createdAt != null && now - createdAt > Constants.SESSION_TOKEN_LIFETIME_MS + Constants.SESSION_RENEWAL_WINDOW_MS
  }

  /**
   * The payload of a packet `playerId`'s client sent, if it is signed with `current` (the token of
   * the channel or the session it came in by) or with another token of theirs still taken: the one
   * a renewal sent them, whose first use makes it the session's token, or the one a renewal
   * replaced, for a moment after. Null if none of them signed it.
   */
  def verify(playerId: UUID, data: Array[Byte], current: Array[Byte], now: Long): Array[Byte] = {
    val payload = PacketSigner.verify(data, current)
    if (payload != null) return payload
    val renewal = renewals.get(playerId)
    if (renewal != null) {
      val renewed = PacketSigner.verify(data, renewal.token)
      if (renewed != null) {
        takeUp(playerId, renewal, now)
        return renewed
      }
    }
    val retired = retiredTokens.get(playerId)
    if (retired != null && now <= retired.until) PacketSigner.verify(data, retired.token) else null
  }

  /** The client has used the token its session was renewed with: the server signs with it from now
    * on, and the one it replaces is taken for a moment longer. */
  private def takeUp(playerId: UUID, renewal: Sessions.Renewal, now: Long): Unit = lockOf(playerId).synchronized {
    if (renewals.remove(playerId, renewal)) {
      // Unless the session ended meanwhile
      val old = sessionTokens.replace(playerId, renewal.token)
      if (old != null) {
        channelToToken.replace(renewal.channel, renewal.token)
        tokenCreationTime.put(playerId, java.lang.Long.valueOf(now))
        retiredTokens.put(playerId, new Sessions.RetiredToken(old, now + Constants.RETIRED_TOKEN_GRACE_MS))
      }
    }
  }

  /** A failed login on this channel. Too many and the channel is closed. */
  def recordAuthFailure(tcpCh: Channel): Unit = {
    val counter = channelAuthFailures.computeIfAbsent(tcpCh, _ => new AtomicInteger(0))
    if (counter.incrementAndGet() >= Constants.MAX_AUTH_FAILURES_PER_CHANNEL) {
      System.err.println(s"Auth: Too many failures on channel, closing: ${tcpCh.remoteAddress()}")
      tcpCh.close()
    }
  }

  /**
   * A new session for a login on `tcpCh`, replacing any the player has. `family` is the
   * refresh-token family the login belongs to (null if it has none); `exchanged` says the login
   * exchanged one of its tokens, which is confirmed when the client is first heard on the session.
   */
  private[server] def generateSessionToken(playerId: UUID, tcpCh: Channel, family: String = null,
                                           exchanged: Boolean = false): Array[Byte] =
    lockOf(playerId).synchronized {
      val token = generateSessionTokenImpl(playerId, tcpCh)
      if (family != null) refreshFamilies.put(playerId, family) else refreshFamilies.remove(playerId)
      if (family != null && exchanged) unconfirmedExchanges.put(playerId, family) else unconfirmedExchanges.remove(playerId)
      token
    }

  private def generateSessionTokenImpl(playerId: UUID, tcpCh: Channel): Array[Byte] = {
    // Invalidate any existing session for this player (prevents stale channel reuse)
    val oldToken = sessionTokens.get(playerId)
    if (oldToken != null) {
      // Find and close the old channel that held the previous token
      import scala.jdk.CollectionConverters._
      channelToToken.entrySet().asScala.find { e =>
        java.util.Arrays.equals(e.getValue, oldToken)
      }.foreach { e =>
        val oldChannel = e.getKey
        if (oldChannel != tcpCh) {
          // Told first, signed with the token it still has: its client doesn't log back in by
          // itself, or two clients of one account would take it from each other for ever
          tellSessionEnded(oldChannel, playerId, SessionEndReason.REPLACED)
          channelToToken.remove(oldChannel)
          channelToPlayer.remove(oldChannel)
          channelAuthFailures.remove(oldChannel)
          malformedPacketCounts.remove(oldChannel)
          rateLimiter.removeChannel(oldChannel)
          oldChannel.close()
          // The old session ends here as a disconnect would have ended it, since its channel's
          // own disconnect will no longer find the player. It used to end with the channel alone:
          // the player stayed in their match (a target standing still for the rest of it) and in
          // their lobby, so every lobby the new session tried to make or join was refused as
          // "already in a lobby" until that match was over. A new login starts in the browser.
          onSessionReplaced(playerId)
          System.err.println(s"Auth: Closed stale session for ${playerId.toString.substring(0, 8)} on old channel")
        }
      }
    }
    // Nor does a renewal of the old session carry over
    renewals.remove(playerId)
    retiredTokens.remove(playerId)

    // The new session's client counts its packets from zero. When the old channel was still
    // open here, closing it above doesn't clear the old session's sequence numbers (its
    // disconnect no longer finds the player), and every packet of the new session was taken for
    // a replay until its count passed the old one's: logged in, and nothing worked.
    replay.resetSequences(playerId)

    val token = newToken()
    sessionTokens.put(playerId, token)
    tokenCreationTime.put(playerId, System.currentTimeMillis())
    if (tcpCh != null) {
      channelToToken.put(tcpCh, token)
      // Bind channel to player UUID immediately so identity check works from the first packet
      channelToPlayer.put(tcpCh, playerId)
    }
    token
  }

  private def newToken(): Array[Byte] = {
    val token = new Array[Byte](32)
    secureRandom.nextBytes(token)
    token
  }

  /** A packet from `playerId`'s client, checked, on their session's channel. The first after a
    * login that exchanged a refresh token confirms the exchange: the client has the new token. */
  private[server] def heardFrom(playerId: UUID): Unit =
    if (!unconfirmedExchanges.isEmpty) {
      val family = unconfirmedExchanges.remove(playerId)
      if (family != null) onExchangeConfirmed(family)
    }

  /**
   * The refresh-token family's login is over (a logout, or a copied token used): every session of
   * it is told so (SESSION_END, REVOKED) and closed. Their channels' disconnects end the rest.
   */
  private[server] def endFamily(family: String): Unit = {
    channelToPlayer.forEach { (ch: Channel, id: UUID) =>
      if (family.equals(refreshFamilies.get(id))) {
        tellSessionEnded(ch, id, SessionEndReason.REVOKED)
        channelToToken.remove(ch)
        if (ch.isOpen) ch.close()
      }
    }
  }

  /** A TCP channel closed: forget what was kept about it. Returns the player it had logged in as,
    * or null. */
  private[server] def channelClosed(ch: Channel): UUID = {
    channelToToken.remove(ch)
    channelAuthFailures.remove(ch)
    malformedPacketCounts.remove(ch)
    rateLimiter.removeChannel(ch)
    channelToPlayer.remove(ch)
  }

  /** The player's session is over (they disconnected, or went quiet): their tokens, where they
    * logged in from, and their packet numbering go with it. */
  private[server] def endSession(playerId: UUID): Unit = lockOf(playerId).synchronized {
    sessionTokens.remove(playerId)
    tokenCreationTime.remove(playerId)
    playerTcpAddresses.remove(playerId)
    accountNames.remove(playerId)
    renewals.remove(playerId)
    retiredTokens.remove(playerId)
    refreshFamilies.remove(playerId)
    unconfirmedExchanges.remove(playerId)
    replay.removePlayer(playerId)
  }

  /**
   * The sessions whose token has reached its lifetime. One whose client is still connected (on
   * `liveChannelOf(player)`, null if it isn't) is renewed: `renew` sends the client its new token,
   * which takes over when the client first uses it. One that isn't, or whose client hasn't used
   * its new token within SESSION_RENEWAL_WINDOW_MS, ends: its tokens go, and every channel it
   * logged in on is closed, so the client has to log in again.
   */
  private[server] def renewTokens(now: Long, liveChannelOf: UUID => Channel,
                                  renew: (UUID, Channel, Array[Byte]) => Unit): Unit = {
    val tokenIter = tokenCreationTime.entrySet().iterator()
    while (tokenIter.hasNext) {
      val entry = tokenIter.next()
      if (now - entry.getValue > Constants.SESSION_TOKEN_LIFETIME_MS) {
        val playerId = entry.getKey
        lockOf(playerId).synchronized {
          // Re-checked under the lock: a login, or the client taking up its renewal, may have just reset it
          val createdAt = tokenCreationTime.get(playerId)
          if (createdAt != null && now - createdAt > Constants.SESSION_TOKEN_LIFETIME_MS) {
            val live = liveChannelOf(playerId)
            if (live != null && live.isActive && !isExpired(playerId, now)) {
              if (!renewals.containsKey(playerId)) {
                val token = newToken()
                renewals.put(playerId, new Sessions.Renewal(token, live))
                renew(playerId, live, token)
                println(s"Session token renewed: ${playerId.toString.substring(0, 8)}")
              }
            } else {
              expire(playerId)
            }
          }
        }
      }
    }
  }

  /** End a session whose token can't be renewed. Its channels' disconnects end the rest of it. */
  private def expire(playerId: UUID): Unit = {
    tokenCreationTime.remove(playerId)
    sessionTokens.remove(playerId)
    playerTcpAddresses.remove(playerId)
    renewals.remove(playerId)
    retiredTokens.remove(playerId)
    refreshFamilies.remove(playerId)
    unconfirmedExchanges.remove(playerId)
    Metrics.sessionsExpired.add(1L, io.opentelemetry.api.common.Attributes.empty())
    // Every channel it logged in on. Only a player who had joined used to have theirs closed: a
    // client that logged in and never joined kept its connection, and the server went on taking
    // its packets after the token was gone.
    channelToPlayer.forEach { (ch: Channel, id: UUID) =>
      if (id.equals(playerId)) {
        channelToToken.remove(ch)
        if (ch.isOpen) ch.close()
      }
    }
    println(s"Session token expired: ${playerId.toString.substring(0, 8)}")
  }
}

object Sessions {
  /** A new token sent to a session's client on `channel`, not yet used by it. */
  private[server] final class Renewal(val token: Array[Byte], val channel: Channel)

  /** A token a renewal replaced, still taken from the client until `until`. */
  private[server] final class RetiredToken(val token: Array[Byte], val until: Long)
}

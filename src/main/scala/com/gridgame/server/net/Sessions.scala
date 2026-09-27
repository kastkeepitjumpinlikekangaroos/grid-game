package com.gridgame.server.net

import com.gridgame.common.Constants
import com.gridgame.common.observability.Metrics
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
  // Session token creation times for expiration
  val tokenCreationTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  // Per-channel auth failure tracking
  val channelAuthFailures = new ConcurrentHashMap[Channel, AtomicInteger]()
  // The name each logged-in player logged in with: the one they go by (GameServer.handleGlobalConnect)
  private[server] val accountNames = new ConcurrentHashMap[UUID, String]()
  // Per-channel malformed packet tracking (disconnect after too many)
  private[server] val malformedPacketCounts = new ConcurrentHashMap[Channel, AtomicInteger]()
  private[server] val MAX_MALFORMED_PACKETS = 3
  // Per-player locks for session token generation (UUIDs are not interned, so can't synchronize on them directly)
  private[server] val playerLocks = new ConcurrentHashMap[UUID, AnyRef]()

  /** What to do when a login replaces a session the server still holds open: set by the server,
    * which takes the old session out of whatever it was in. */
  @volatile private[server] var onSessionReplaced: UUID => Unit = _ => ()

  /** Has this player's session outlived its token? */
  def isExpired(playerId: UUID, now: Long): Boolean = {
    val createdAt = tokenCreationTime.get(playerId)
    createdAt != null && now - createdAt > Constants.SESSION_TOKEN_LIFETIME_MS
  }

  /** A failed login on this channel. Too many and the channel is closed. */
  def recordAuthFailure(tcpCh: Channel): Unit = {
    val counter = channelAuthFailures.computeIfAbsent(tcpCh, _ => new AtomicInteger(0))
    if (counter.incrementAndGet() >= Constants.MAX_AUTH_FAILURES_PER_CHANNEL) {
      System.err.println(s"Auth: Too many failures on channel, closing: ${tcpCh.remoteAddress()}")
      tcpCh.close()
    }
  }

  private[server] def generateSessionToken(playerId: UUID, tcpCh: Channel): Array[Byte] = {
    val lock = playerLocks.computeIfAbsent(playerId, _ => new AnyRef)
    lock.synchronized {
      generateSessionTokenImpl(playerId, tcpCh)
    }
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

    // The new session's client counts its packets from zero. When the old channel was still
    // open here, closing it above doesn't clear the old session's sequence numbers (its
    // disconnect no longer finds the player), and every packet of the new session was taken for
    // a replay until its count passed the old one's: logged in, and nothing worked.
    replay.resetSequences(playerId)

    val token = new Array[Byte](32)
    secureRandom.nextBytes(token)
    sessionTokens.put(playerId, token)
    tokenCreationTime.put(playerId, System.currentTimeMillis())
    if (tcpCh != null) {
      channelToToken.put(tcpCh, token)
      // Bind channel to player UUID immediately so identity check works from the first packet
      channelToPlayer.put(tcpCh, playerId)
    }
    token
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

  /** The player's session is over (they disconnected, or went quiet): their token, where they
    * logged in from, and their packet numbering go with it. */
  private[server] def endSession(playerId: UUID): Unit = {
    sessionTokens.remove(playerId)
    tokenCreationTime.remove(playerId)
    playerTcpAddresses.remove(playerId)
    accountNames.remove(playerId)
    replay.removePlayer(playerId)
  }

  /**
   * End every session older than a token's lifetime: its token goes and its TCP channel (found by
   * `channelOf`, null if it has none) is closed, so the client has to log in again. Holds the
   * player's lock, so it can't race a login refreshing the token.
   */
  private[server] def expireStaleTokens(now: Long, channelOf: UUID => Channel): Unit = {
    val tokenIter = tokenCreationTime.entrySet().iterator()
    while (tokenIter.hasNext) {
      val entry = tokenIter.next()
      if (now - entry.getValue > Constants.SESSION_TOKEN_LIFETIME_MS) {
        val playerId = entry.getKey
        val lock = playerLocks.computeIfAbsent(playerId, _ => new AnyRef)
        lock.synchronized {
          // Re-check creation time under lock — a concurrent generateSessionToken may have refreshed it
          val currentCreation = tokenCreationTime.get(playerId)
          if (currentCreation != null && now - currentCreation > Constants.SESSION_TOKEN_LIFETIME_MS) {
            tokenCreationTime.remove(playerId)
            sessionTokens.remove(playerId)
            playerTcpAddresses.remove(playerId)
            Metrics.sessionsExpired.add(1L, io.opentelemetry.api.common.Attributes.empty())

            // Close TCP channel to force re-auth
            val ch = channelOf(playerId)
            if (ch != null) {
              channelToToken.remove(ch)
              if (ch.isOpen) ch.close()
            }
            println(s"Session token expired: ${playerId.toString.substring(0, 8)}")
          }
        }
      }
    }
  }
}

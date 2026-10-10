package com.gridgame.server.account

import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol.AuthRules

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID
import scala.collection.mutable.ArrayBuffer
import scala.util.Using

import org.mindrot.jbcrypt.BCrypt

/**
 * Accounts, matches and ratings, in SQLite. Every statement is closed whether or not its query
 * throws (`statement`, `rows`): closed only on the way out of a query that worked, as they were, a
 * failed one (the database locked by another connection, say) left its native statement behind.
 *
 * `match_results` grows by a row for every player in every match, bots included, so it is read
 * only through its indexes: by player (a profile, the leaderboard) and by match. Read without them,
 * every profile, history and leaderboard request scanned all of it, and got slower the longer the
 * server was played on.
 */
class AuthDatabase(dbPath: String = AuthDatabase.resolveDbPath()) {
  private val connection: Connection = {
    Class.forName("org.sqlite.JDBC")
    val conn = DriverManager.getConnection(s"jdbc:sqlite:$dbPath")
    conn.setAutoCommit(true)
    conn
  }
  // All SQLite operations must be synchronized — single connection shared across Netty threads
  private val dbLock = new Object()

  init()

  /** Time an operation and emit `gridgame.db.duration{op=<label>}` (+ error counter on throw). */
  private def timed[T](op: String)(body: => T): T = {
    val start = System.nanoTime()
    val attrs = Attrs.dbOp(op)
    try {
      val out = body
      Metrics.dbDuration.record((System.nanoTime() - start) / 1e6, attrs)
      out
    } catch {
      case e: Throwable =>
        Metrics.dbDuration.record((System.nanoTime() - start) / 1e6, attrs)
        Metrics.dbErrors.add(1L, attrs)
        throw e
    }
  }

  /** A prepared statement for `body`, closed after it whether or not it throws. */
  private def statement[T](sql: String)(body: PreparedStatement => T): T =
    Using.resource(connection.prepareStatement(sql))(body)

  /** Every row a query returns, as `row` reads it; its result set closed after. */
  private def rows[T](stmt: PreparedStatement)(row: ResultSet => T): Seq[T] =
    Using.resource(stmt.executeQuery()) { rs =>
      val out = ArrayBuffer[T]()
      while (rs.next()) out += row(rs)
      out.toSeq
    }

  private def init(): Unit = Using.resource(connection.createStatement()) { stmt =>
    stmt.executeUpdate(
      """CREATE TABLE IF NOT EXISTS accounts(
        |  username TEXT PRIMARY KEY,
        |  password_hash TEXT NOT NULL,
        |  salt TEXT NOT NULL,
        |  created_at INTEGER NOT NULL
        |)""".stripMargin
    )
    // Add elo column if it doesn't exist (safe migration)
    try {
      stmt.executeUpdate("ALTER TABLE accounts ADD COLUMN elo INTEGER DEFAULT 1000")
    } catch {
      case _: Exception => // Column already exists
    }

    // Add uuid column if it doesn't exist (safe migration for O(1) UUID lookups)
    try {
      stmt.executeUpdate("ALTER TABLE accounts ADD COLUMN uuid TEXT")
    } catch {
      case _: Exception => // Column already exists
    }

    // Backfill uuid column for existing accounts
    val usernames = statement("SELECT username FROM accounts WHERE uuid IS NULL")(rows(_)(_.getString("username")))
    if (usernames.nonEmpty) {
      statement("UPDATE accounts SET uuid = ? WHERE username = ?") { updateStmt =>
        usernames.foreach { uname =>
          updateStmt.setString(1, deriveUUID(uname).toString)
          updateStmt.setString(2, uname)
          updateStmt.addBatch()
        }
        updateStmt.executeBatch()
      }
      println(s"AuthDatabase: Backfilled UUID for ${usernames.size} accounts")
    }

    // Create index on uuid column (safe - IF NOT EXISTS)
    try {
      stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_accounts_uuid ON accounts(uuid)")
    } catch {
      case _: Exception =>
    }

    // Migrate legacy SHA-256 hashes to bcrypt (detect by hash length: SHA-256 hex = 64 chars)
    // Cannot migrate without knowing plaintext - mark for re-hash on next login
    val legacyCount = statement("SELECT username, password_hash, salt FROM accounts WHERE length(password_hash) = 64")(rows(_)(_ => ())).size
    if (legacyCount > 0) {
      println(s"AuthDatabase: $legacyCount accounts have legacy SHA-256 hashes (will be upgraded on next login)")
    }

    stmt.executeUpdate(
      """CREATE TABLE IF NOT EXISTS matches(
        |  match_id INTEGER PRIMARY KEY AUTOINCREMENT,
        |  map_index INTEGER NOT NULL,
        |  duration_minutes INTEGER NOT NULL,
        |  played_at INTEGER NOT NULL,
        |  player_count INTEGER NOT NULL
        |)""".stripMargin
    )
    // Add match_type column if it doesn't exist (safe migration)
    try {
      stmt.executeUpdate("ALTER TABLE matches ADD COLUMN match_type INTEGER DEFAULT 0")
    } catch {
      case _: Exception => // Column already exists
    }
    stmt.executeUpdate(
      """CREATE TABLE IF NOT EXISTS match_results(
        |  id INTEGER PRIMARY KEY AUTOINCREMENT,
        |  match_id INTEGER NOT NULL,
        |  player_uuid TEXT NOT NULL,
        |  kills INTEGER NOT NULL,
        |  deaths INTEGER NOT NULL,
        |  rank INTEGER NOT NULL,
        |  FOREIGN KEY(match_id) REFERENCES matches(match_id)
        |)""".stripMargin
    )
    // The character each player played, and the bots' rows beside the humans' (saved since
    // 2026-09-28), so which characters win can be read from here. Earlier rows have neither
    try {
      stmt.executeUpdate("ALTER TABLE match_results ADD COLUMN character_id INTEGER")
    } catch {
      case _: Exception => // Column already exists
    }
    try {
      stmt.executeUpdate("ALTER TABLE match_results ADD COLUMN is_bot INTEGER NOT NULL DEFAULT 0")
    } catch {
      case _: Exception => // Column already exists
    }
    // A player's rows (their profile, their history, the leaderboard's counts), a match's rows, and
    // the accounts best first (the leaderboard). Built once on a database from before them, which
    // takes a moment on a big one
    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_match_results_player ON match_results(player_uuid)")
    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_match_results_match ON match_results(match_id)")
    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_accounts_elo ON accounts(elo DESC, username)")
    println(s"AuthDatabase: Initialized ($dbPath)")
  }

  def register(username: String, password: String): Boolean = timed("register") {
    if (username == null || username.isEmpty || password == null || password.isEmpty) {
      return false
    }
    if (password.length < AuthRules.MinPasswordLength) return false
    // Reject usernames with control chars, emoji, RTL marks, or other unsafe characters
    if (!AuthRules.isValidUsername(username)) return false

    // Compute bcrypt hash outside the lock (slow ~200ms)
    val hash = BCrypt.hashpw(password, BCrypt.gensalt(12))
    val uuid = deriveUUID(username.toLowerCase)

    dbLock.synchronized {
      // Check if username already exists
      val exists = statement("SELECT 1 FROM accounts WHERE username = ?") { checkStmt =>
        checkStmt.setString(1, username.toLowerCase)
        rows(checkStmt)(_ => ()).nonEmpty
      }

      if (exists) return false

      statement("INSERT INTO accounts(username, password_hash, salt, created_at, uuid) VALUES(?, ?, ?, ?, ?)") { insertStmt =>
        insertStmt.setString(1, username.toLowerCase)
        insertStmt.setString(2, hash)
        insertStmt.setString(3, "") // salt is embedded in bcrypt hash
        insertStmt.setLong(4, System.currentTimeMillis())
        insertStmt.setString(5, uuid.toString)
        insertStmt.executeUpdate() > 0
      }
    }
  }

  def authenticate(username: String, password: String): Boolean = timed("authenticate") {
    if (username == null || username.isEmpty || password == null || password.isEmpty) {
      return false
    }

    // Fetch hash from DB inside lock, then verify outside lock (bcrypt is slow)
    val (storedHash, salt) = dbLock.synchronized {
      statement("SELECT password_hash, salt FROM accounts WHERE username = ?") { stmt =>
        stmt.setString(1, username.toLowerCase)
        rows(stmt)(rs => (rs.getString("password_hash"), rs.getString("salt"))).headOption.getOrElse((null, null))
      }
    }

    if (storedHash == null) {
      // Run dummy bcrypt hash to prevent timing-based username enumeration
      BCrypt.hashpw(password, BCrypt.gensalt(12))
      return false
    }

    if (storedHash.length == 64) {
      // Legacy SHA-256 hash: verify with old method, then upgrade to bcrypt
      val computedHash = hashPasswordLegacy(password, salt)
      val matches = MessageDigest.isEqual(
        storedHash.getBytes(StandardCharsets.UTF_8),
        computedHash.getBytes(StandardCharsets.UTF_8)
      )
      if (matches) {
        // Upgrade to bcrypt on successful login
        val bcryptHash = BCrypt.hashpw(password, BCrypt.gensalt(12))
        dbLock.synchronized {
          statement("UPDATE accounts SET password_hash = ?, salt = '' WHERE username = ?") { upgradeStmt =>
            upgradeStmt.setString(1, bcryptHash)
            upgradeStmt.setString(2, username.toLowerCase)
            upgradeStmt.executeUpdate()
          }
        }
        println(s"AuthDatabase: Upgraded password hash for '$username' from SHA-256 to bcrypt")
      }
      matches
    } else {
      // bcrypt hash: use BCrypt.checkpw (internally constant-time)
      BCrypt.checkpw(password, storedHash)
    }
  }

  def getOrCreateUUID(username: String): UUID = {
    // Deterministic UUID from username so same player always gets same UUID
    val bytes = MessageDigest.getInstance("SHA-256")
      .digest(s"gridgame:$username".toLowerCase.getBytes(StandardCharsets.UTF_8))
    val msb = bytesToLong(bytes, 0)
    val lsb = bytesToLong(bytes, 8)
    new UUID(msb, lsb)
  }

  /** Saves a match and its players' rows, returning its id (-1 if it couldn't be saved). `results`
    * holds whoever the match is recorded for (MatchEnd leaves out a practice session's bots);
    * `playerCount` is everyone who played, since that is the field the ranks were placed among. */
  def saveMatch(mapIndex: Int, durationMinutes: Int, results: Seq[MatchRow], matchType: Byte = 0,
                playerCount: Int = -1): Long = timed("save_match") { dbLock.synchronized {
    var saved = -1L
    try {
      connection.setAutoCommit(false)

      statement("INSERT INTO matches(map_index, duration_minutes, played_at, player_count, match_type) VALUES(?, ?, ?, ?, ?)") { matchStmt =>
        matchStmt.setInt(1, mapIndex)
        matchStmt.setInt(2, durationMinutes)
        matchStmt.setLong(3, System.currentTimeMillis())
        matchStmt.setInt(4, if (playerCount > 0) playerCount else results.size)
        matchStmt.setInt(5, matchType & 0xFF)
        matchStmt.executeUpdate()
      }

      val matchId = statement("SELECT last_insert_rowid()")(rows(_)(_.getLong(1))).headOption.getOrElse(-1L)

      if (matchId > 0) {
        statement("INSERT INTO match_results(match_id, player_uuid, kills, deaths, rank, character_id, is_bot) VALUES(?, ?, ?, ?, ?, ?, ?)") { resultStmt =>
          results.foreach { r =>
            resultStmt.setLong(1, matchId)
            resultStmt.setString(2, r.playerId.toString)
            resultStmt.setInt(3, r.kills)
            resultStmt.setInt(4, r.deaths)
            resultStmt.setInt(5, r.rank & 0xFF)
            r.characterId match {
              case Some(c) => resultStmt.setInt(6, c & 0xFF)
              case None => resultStmt.setNull(6, java.sql.Types.INTEGER)
            }
            resultStmt.setInt(7, if (r.bot) 1 else 0)
            resultStmt.addBatch()
          }
          resultStmt.executeBatch()
        }
      }

      connection.commit()
      saved = matchId
      println(s"AuthDatabase: Saved match $matchId with ${results.size} players")
    } catch {
      case e: Exception =>
        try { connection.rollback() } catch { case _: Exception => }
        System.err.println(s"AuthDatabase: Failed to save match: ${e.getMessage}")
    } finally {
      connection.setAutoCommit(true)
    }
    saved
  } }

  /** Every row of a saved match, the bots' included, best first. */
  def getMatchRows(matchId: Long): Seq[MatchRow] = timed("match_rows") { dbLock.synchronized {
    statement("SELECT player_uuid, kills, deaths, rank, character_id, is_bot FROM match_results WHERE match_id = ? ORDER BY rank, id") { stmt =>
      stmt.setLong(1, matchId)
      rows(stmt) { rs =>
        val c = rs.getInt("character_id")
        val character = if (rs.wasNull()) None else Some(c.toByte)
        MatchRow(UUID.fromString(rs.getString("player_uuid")), rs.getInt("kills"), rs.getInt("deaths"),
          rs.getInt("rank"), character, rs.getInt("is_bot") != 0)
      }
    }
  } }

  /** Returns (matchId, mapIndex, durationMinutes, playedAt, kills, deaths, rank, playerCount, matchType) */
  def getMatchHistory(playerUUID: UUID, limit: Int = 20): Seq[(Long, Int, Int, Long, Int, Int, Int, Int, Int)] = timed("history") { dbLock.synchronized {
    statement(
      """SELECT m.match_id, m.map_index, m.duration_minutes, m.played_at,
        |       mr.kills, mr.deaths, mr.rank, m.player_count, m.match_type
        |FROM match_results mr
        |JOIN matches m ON mr.match_id = m.match_id
        |WHERE mr.player_uuid = ?
        |ORDER BY m.played_at DESC
        |LIMIT ?""".stripMargin
    ) { stmt =>
      stmt.setString(1, playerUUID.toString)
      stmt.setInt(2, limit)
      rows(stmt) { rs =>
        (
          rs.getLong("match_id"),
          rs.getInt("map_index"),
          rs.getInt("duration_minutes"),
          rs.getLong("played_at"),
          rs.getInt("kills"),
          rs.getInt("deaths"),
          rs.getInt("rank"),
          rs.getInt("player_count"),
          rs.getInt("match_type")
        )
      }
    }
  } }

  /** Returns (totalKills, totalDeaths, matchesPlayed, wins, elo). Practice sessions are
    * left out: kills on passive target bots, and a "win" for topping them, aren't a record. */
  def getPlayerStats(playerUUID: UUID): (Int, Int, Int, Int, Int) = timed("stats") { dbLock.synchronized {
    val result = statement(
      s"""SELECT COALESCE(SUM(mr.kills), 0) AS total_kills,
        |       COALESCE(SUM(mr.deaths), 0) AS total_deaths,
        |       COUNT(*) AS matches_played,
        |       COALESCE(SUM(CASE WHEN mr.rank = 1 THEN 1 ELSE 0 END), 0) AS wins
        |FROM match_results mr
        |JOIN matches m ON m.match_id = mr.match_id
        |WHERE mr.player_uuid = ? AND COALESCE(m.match_type, 0) <> ${AuthDatabase.PracticeMatchType}""".stripMargin
    ) { stmt =>
      stmt.setString(1, playerUUID.toString)
      rows(stmt)(rs => (rs.getInt("total_kills"), rs.getInt("total_deaths"), rs.getInt("matches_played"), rs.getInt("wins")))
        .headOption.getOrElse((0, 0, 0, 0))
    }

    val elo = getEloByUUID(playerUUID)
    (result._1, result._2, result._3, result._4, elo)
  } }

  /** Returns (username, elo, wins, matchesPlayed) sorted by ELO descending. Like the profile
    * stats, practice sessions don't count as matches or wins (`AuthDatabase.LeaderboardQuery`). */
  def getLeaderboard(limit: Int = 50): Seq[(String, Int, Int, Int)] = timed("leaderboard") { dbLock.synchronized {
    statement(AuthDatabase.LeaderboardQuery) { stmt =>
      stmt.setInt(1, limit)
      rows(stmt) { rs =>
        (
          rs.getString("username"),
          rs.getInt("elo"),
          rs.getInt("wins"),
          rs.getInt("matches_played")
        )
      }
    }
  } }

  def getElo(username: String): Int = timed("elo_get") { dbLock.synchronized {
    statement("SELECT elo FROM accounts WHERE username = ?") { stmt =>
      stmt.setString(1, username.toLowerCase)
      rows(stmt)(_.getInt("elo")).headOption.getOrElse(1000)
    }
  } }

  def getEloByUUID(playerUUID: UUID): Int = {
    val username = getUsernameByUUID(playerUUID)
    if (username != null) getElo(username) else 1000
  }

  def updateElo(username: String, newElo: Int): Unit = timed("elo_update") { dbLock.synchronized {
    statement("UPDATE accounts SET elo = ? WHERE username = ?") { stmt =>
      stmt.setInt(1, newElo)
      stmt.setString(2, username.toLowerCase)
      stmt.executeUpdate()
    }
  } }

  def getUsernameByUUID(uuid: UUID): String = timed("username_by_uuid") { dbLock.synchronized {
    statement("SELECT username FROM accounts WHERE uuid = ?") { stmt =>
      stmt.setString(1, uuid.toString)
      rows(stmt)(_.getString("username")).headOption.orNull
    }
  } }

  /** Legacy SHA-256 hash for migrating old accounts to bcrypt. */
  private def hashPasswordLegacy(password: String, salt: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val salted = (salt + password).getBytes(StandardCharsets.UTF_8)
    bytesToHex(digest.digest(salted))
  }

  /** Derive a deterministic UUID from a username. */
  private def deriveUUID(username: String): UUID = {
    val bytes = MessageDigest.getInstance("SHA-256")
      .digest(s"gridgame:$username".toLowerCase.getBytes(StandardCharsets.UTF_8))
    val msb = bytesToLong(bytes, 0)
    val lsb = bytesToLong(bytes, 8)
    new UUID(msb, lsb)
  }

  private def bytesToHex(bytes: Array[Byte]): String = {
    bytes.map(b => f"${b & 0xff}%02x").mkString
  }

  private def bytesToLong(bytes: Array[Byte], offset: Int): Long = {
    var result = 0L
    var i = 0
    while (i < 8) {
      result = (result << 8) | (bytes(offset + i) & 0xffL)
      i += 1
    }
    result
  }

  def close(): Unit = {
    if (connection != null && !connection.isClosed) {
      connection.close()
    }
  }
}

/** One player's line in a saved match. `characterId` is None in rows saved before characters were. */
final case class MatchRow(playerId: UUID, kills: Int, deaths: Int, rank: Int, characterId: Option[Byte] = None,
                          bot: Boolean = false)

object AuthDatabase {
  /** `matches.match_type` of a practice session (see `Lobby.matchType`). */
  val PracticeMatchType = 5

  /**
   * The leaderboard: the best-rated accounts, in order (read off their index), and each one's
   * matches and wins counted through the index of its own rows. Counted by joining every account
   * to every result and grouping, as it was, a request read the whole table, the bots' rows and
   * all, and took longer the longer the server was played on (1.1s at two million rows; 140ms
   * this way, most of that the counting of fifty players' thousand matches each).
   */
  private[server] val LeaderboardQuery: String =
    s"""SELECT a.username, a.elo,
       |       (SELECT COUNT(*) FROM match_results r JOIN matches m ON m.match_id = r.match_id
       |        WHERE r.player_uuid = a.uuid AND r.rank = 1
       |          AND COALESCE(m.match_type, 0) <> $PracticeMatchType) AS wins,
       |       (SELECT COUNT(*) FROM match_results r JOIN matches m ON m.match_id = r.match_id
       |        WHERE r.player_uuid = a.uuid
       |          AND COALESCE(m.match_type, 0) <> $PracticeMatchType) AS matches_played
       |FROM accounts a
       |ORDER BY a.elo DESC, a.username ASC
       |LIMIT ?""".stripMargin

  def resolveDbPath(): String = {
    val fileName = "game_accounts.db"
    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      new File(buildWorkDir, fileName).getAbsolutePath
    } else {
      fileName
    }
  }
}

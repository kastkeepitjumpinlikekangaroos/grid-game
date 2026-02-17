package com.gridgame.server

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

import org.mindrot.jbcrypt.BCrypt

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

  private def init(): Unit = {
    val stmt = connection.createStatement()
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
    val backfillRs = connection.prepareStatement("SELECT username FROM accounts WHERE uuid IS NULL").executeQuery()
    val usernames = scala.collection.mutable.ArrayBuffer[String]()
    while (backfillRs.next()) {
      usernames += backfillRs.getString("username")
    }
    backfillRs.close()
    if (usernames.nonEmpty) {
      val updateStmt = connection.prepareStatement("UPDATE accounts SET uuid = ? WHERE username = ?")
      usernames.foreach { uname =>
        updateStmt.setString(1, deriveUUID(uname).toString)
        updateStmt.setString(2, uname)
        updateStmt.addBatch()
      }
      updateStmt.executeBatch()
      updateStmt.close()
      println(s"AuthDatabase: Backfilled UUID for ${usernames.size} accounts")
    }

    // Create index on uuid column (safe - IF NOT EXISTS)
    try {
      stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_accounts_uuid ON accounts(uuid)")
    } catch {
      case _: Exception =>
    }

    // Migrate legacy SHA-256 hashes to bcrypt (detect by hash length: SHA-256 hex = 64 chars)
    val legacyRs = connection.prepareStatement(
      "SELECT username, password_hash, salt FROM accounts WHERE length(password_hash) = 64"
    ).executeQuery()
    var legacyCount = 0
    while (legacyRs.next()) {
      // Cannot migrate without knowing plaintext - mark for re-hash on next login
      legacyCount += 1
    }
    legacyRs.close()
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
    stmt.close()
    println(s"AuthDatabase: Initialized ($dbPath)")
  }

  private val VALID_USERNAME_PATTERN = "^[a-zA-Z0-9_-]{1,20}$".r

  def register(username: String, password: String): Boolean = {
    if (username == null || username.isEmpty || password == null || password.isEmpty) {
      return false
    }
    if (password.length < 6) return false
    // Reject usernames with control chars, emoji, RTL marks, or other unsafe characters
    if (VALID_USERNAME_PATTERN.findFirstIn(username).isEmpty) return false

    // Compute bcrypt hash outside the lock (slow ~200ms)
    val hash = BCrypt.hashpw(password, BCrypt.gensalt(12))
    val uuid = deriveUUID(username.toLowerCase)

    dbLock.synchronized {
      // Check if username already exists
      val checkStmt = connection.prepareStatement("SELECT 1 FROM accounts WHERE username = ?")
      checkStmt.setString(1, username.toLowerCase)
      val rs = checkStmt.executeQuery()
      val exists = rs.next()
      rs.close()
      checkStmt.close()

      if (exists) return false

      val insertStmt = connection.prepareStatement(
        "INSERT INTO accounts(username, password_hash, salt, created_at, uuid) VALUES(?, ?, ?, ?, ?)"
      )
      insertStmt.setString(1, username.toLowerCase)
      insertStmt.setString(2, hash)
      insertStmt.setString(3, "") // salt is embedded in bcrypt hash
      insertStmt.setLong(4, System.currentTimeMillis())
      insertStmt.setString(5, uuid.toString)
      val inserted = insertStmt.executeUpdate() > 0
      insertStmt.close()
      inserted
    }
  }

  def authenticate(username: String, password: String): Boolean = {
    if (username == null || username.isEmpty || password == null || password.isEmpty) {
      return false
    }

    // Fetch hash from DB inside lock, then verify outside lock (bcrypt is slow)
    val (storedHash, salt) = dbLock.synchronized {
      val stmt = connection.prepareStatement("SELECT password_hash, salt FROM accounts WHERE username = ?")
      stmt.setString(1, username.toLowerCase)
      val rs = stmt.executeQuery()
      val result = if (rs.next()) {
        (rs.getString("password_hash"), rs.getString("salt"))
      } else {
        (null, null)
      }
      rs.close()
      stmt.close()
      result
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
          val upgradeStmt = connection.prepareStatement(
            "UPDATE accounts SET password_hash = ?, salt = '' WHERE username = ?"
          )
          upgradeStmt.setString(1, bcryptHash)
          upgradeStmt.setString(2, username.toLowerCase)
          upgradeStmt.executeUpdate()
          upgradeStmt.close()
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

  def saveMatch(mapIndex: Int, durationMinutes: Int, results: Seq[(UUID, Int, Int, Byte)], matchType: Byte = 0): Unit = dbLock.synchronized {
    try {
      connection.setAutoCommit(false)

      val matchStmt = connection.prepareStatement(
        "INSERT INTO matches(map_index, duration_minutes, played_at, player_count, match_type) VALUES(?, ?, ?, ?, ?)"
      )
      matchStmt.setInt(1, mapIndex)
      matchStmt.setInt(2, durationMinutes)
      matchStmt.setLong(3, System.currentTimeMillis())
      matchStmt.setInt(4, results.size)
      matchStmt.setInt(5, matchType & 0xFF)
      matchStmt.executeUpdate()
      matchStmt.close()

      val idStmt = connection.createStatement()
      val idRs = idStmt.executeQuery("SELECT last_insert_rowid()")
      val matchId = if (idRs.next()) idRs.getLong(1) else -1L
      idRs.close()
      idStmt.close()

      if (matchId > 0) {
        val resultStmt = connection.prepareStatement(
          "INSERT INTO match_results(match_id, player_uuid, kills, deaths, rank) VALUES(?, ?, ?, ?, ?)"
        )
        results.foreach { case (uuid, kills, deaths, rank) =>
          resultStmt.setLong(1, matchId)
          resultStmt.setString(2, uuid.toString)
          resultStmt.setInt(3, kills)
          resultStmt.setInt(4, deaths)
          resultStmt.setInt(5, rank & 0xFF)
          resultStmt.addBatch()
        }
        resultStmt.executeBatch()
        resultStmt.close()
      }

      connection.commit()
      println(s"AuthDatabase: Saved match $matchId with ${results.size} players")
    } catch {
      case e: Exception =>
        try { connection.rollback() } catch { case _: Exception => }
        System.err.println(s"AuthDatabase: Failed to save match: ${e.getMessage}")
    } finally {
      connection.setAutoCommit(true)
    }
  }

  /** Returns (matchId, mapIndex, durationMinutes, playedAt, kills, deaths, rank, playerCount, matchType) */
  def getMatchHistory(playerUUID: UUID, limit: Int = 20): Seq[(Long, Int, Int, Long, Int, Int, Int, Int, Int)] = dbLock.synchronized {
    val stmt = connection.prepareStatement(
      """SELECT m.match_id, m.map_index, m.duration_minutes, m.played_at,
        |       mr.kills, mr.deaths, mr.rank, m.player_count, m.match_type
        |FROM match_results mr
        |JOIN matches m ON mr.match_id = m.match_id
        |WHERE mr.player_uuid = ?
        |ORDER BY m.played_at DESC
        |LIMIT ?""".stripMargin
    )
    stmt.setString(1, playerUUID.toString)
    stmt.setInt(2, limit)
    val rs = stmt.executeQuery()

    val results = scala.collection.mutable.ArrayBuffer[(Long, Int, Int, Long, Int, Int, Int, Int, Int)]()
    while (rs.next()) {
      results += ((
        rs.getLong("match_id"),
        rs.getInt("map_index"),
        rs.getInt("duration_minutes"),
        rs.getLong("played_at"),
        rs.getInt("kills"),
        rs.getInt("deaths"),
        rs.getInt("rank"),
        rs.getInt("player_count"),
        rs.getInt("match_type")
      ))
    }
    rs.close()
    stmt.close()
    results.toSeq
  }

  /** Returns (totalKills, totalDeaths, matchesPlayed, wins, elo) */
  def getPlayerStats(playerUUID: UUID): (Int, Int, Int, Int, Int) = dbLock.synchronized {
    val stmt = connection.prepareStatement(
      """SELECT COALESCE(SUM(kills), 0) AS total_kills,
        |       COALESCE(SUM(deaths), 0) AS total_deaths,
        |       COUNT(*) AS matches_played,
        |       COALESCE(SUM(CASE WHEN rank = 1 THEN 1 ELSE 0 END), 0) AS wins
        |FROM match_results
        |WHERE player_uuid = ?""".stripMargin
    )
    stmt.setString(1, playerUUID.toString)
    val rs = stmt.executeQuery()

    val result = if (rs.next()) {
      (rs.getInt("total_kills"), rs.getInt("total_deaths"),
       rs.getInt("matches_played"), rs.getInt("wins"))
    } else {
      (0, 0, 0, 0)
    }
    rs.close()
    stmt.close()

    val elo = getEloByUUID(playerUUID)
    (result._1, result._2, result._3, result._4, elo)
  }

  /** Returns (username, elo, wins, matchesPlayed) sorted by ELO descending */
  def getLeaderboard(limit: Int = 50): Seq[(String, Int, Int, Int)] = dbLock.synchronized {
    val stmt = connection.prepareStatement(
      """SELECT a.username, a.elo,
        |       COALESCE(SUM(CASE WHEN mr.rank = 1 THEN 1 ELSE 0 END), 0) AS wins,
        |       COUNT(mr.id) AS matches_played
        |FROM accounts a
        |LEFT JOIN match_results mr ON mr.player_uuid = a.uuid
        |GROUP BY a.username, a.elo
        |ORDER BY a.elo DESC
        |LIMIT ?""".stripMargin
    )
    stmt.setInt(1, limit)
    val rs = stmt.executeQuery()

    val results = scala.collection.mutable.ArrayBuffer[(String, Int, Int, Int)]()
    while (rs.next()) {
      results += ((
        rs.getString("username"),
        rs.getInt("elo"),
        rs.getInt("wins"),
        rs.getInt("matches_played")
      ))
    }
    rs.close()
    stmt.close()
    results.toSeq
  }

  def getElo(username: String): Int = dbLock.synchronized {
    val stmt = connection.prepareStatement("SELECT elo FROM accounts WHERE username = ?")
    stmt.setString(1, username.toLowerCase)
    val rs = stmt.executeQuery()
    val elo = if (rs.next()) rs.getInt("elo") else 1000
    rs.close()
    stmt.close()
    elo
  }

  def getEloByUUID(playerUUID: UUID): Int = {
    val username = getUsernameByUUID(playerUUID)
    if (username != null) getElo(username) else 1000
  }

  def updateElo(username: String, newElo: Int): Unit = dbLock.synchronized {
    val stmt = connection.prepareStatement("UPDATE accounts SET elo = ? WHERE username = ?")
    stmt.setInt(1, newElo)
    stmt.setString(2, username.toLowerCase)
    stmt.executeUpdate()
    stmt.close()
  }

  def getUsernameByUUID(uuid: UUID): String = dbLock.synchronized {
    val stmt = connection.prepareStatement("SELECT username FROM accounts WHERE uuid = ?")
    stmt.setString(1, uuid.toString)
    val rs = stmt.executeQuery()
    val found = if (rs.next()) rs.getString("username") else null
    rs.close()
    stmt.close()
    found
  }

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

object AuthDatabase {
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

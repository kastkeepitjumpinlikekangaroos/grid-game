package com.gridgame.server

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class AuthDatabase(dbPath: String = AuthDatabase.resolveDbPath()) {
  private val connection: Connection = {
    Class.forName("org.sqlite.JDBC")
    val conn = DriverManager.getConnection(s"jdbc:sqlite:$dbPath")
    conn.setAutoCommit(true)
    conn
  }

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

    stmt.executeUpdate(
      """CREATE TABLE IF NOT EXISTS matches(
        |  match_id INTEGER PRIMARY KEY AUTOINCREMENT,
        |  map_index INTEGER NOT NULL,
        |  duration_minutes INTEGER NOT NULL,
        |  played_at INTEGER NOT NULL,
        |  player_count INTEGER NOT NULL
        |)""".stripMargin
    )
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

  def register(username: String, password: String): Boolean = {
    if (username == null || username.isEmpty || password == null || password.isEmpty) {
      return false
    }

    // Check if username already exists
    val checkStmt = connection.prepareStatement("SELECT 1 FROM accounts WHERE username = ?")
    checkStmt.setString(1, username.toLowerCase)
    val rs = checkStmt.executeQuery()
    val exists = rs.next()
    rs.close()
    checkStmt.close()

    if (exists) return false

    // Generate salt and hash password
    val salt = generateSalt()
    val hash = hashPassword(password, salt)

    val insertStmt = connection.prepareStatement(
      "INSERT INTO accounts(username, password_hash, salt, created_at) VALUES(?, ?, ?, ?)"
    )
    insertStmt.setString(1, username.toLowerCase)
    insertStmt.setString(2, hash)
    insertStmt.setString(3, salt)
    insertStmt.setLong(4, System.currentTimeMillis())
    val inserted = insertStmt.executeUpdate() > 0
    insertStmt.close()
    inserted
  }

  def authenticate(username: String, password: String): Boolean = {
    if (username == null || username.isEmpty || password == null || password.isEmpty) {
      return false
    }

    val stmt = connection.prepareStatement("SELECT password_hash, salt FROM accounts WHERE username = ?")
    stmt.setString(1, username.toLowerCase)
    val rs = stmt.executeQuery()

    val result = if (rs.next()) {
      val storedHash = rs.getString("password_hash")
      val salt = rs.getString("salt")
      val computedHash = hashPassword(password, salt)
      storedHash == computedHash
    } else {
      false
    }

    rs.close()
    stmt.close()
    result
  }

  def getOrCreateUUID(username: String): UUID = {
    // Deterministic UUID from username so same player always gets same UUID
    val bytes = MessageDigest.getInstance("SHA-256")
      .digest(s"gridgame:$username".toLowerCase.getBytes(StandardCharsets.UTF_8))
    val msb = bytesToLong(bytes, 0)
    val lsb = bytesToLong(bytes, 8)
    new UUID(msb, lsb)
  }

  def saveMatch(mapIndex: Int, durationMinutes: Int, results: Seq[(UUID, Int, Int, Byte)]): Unit = {
    try {
      connection.setAutoCommit(false)

      val matchStmt = connection.prepareStatement(
        "INSERT INTO matches(map_index, duration_minutes, played_at, player_count) VALUES(?, ?, ?, ?)"
      )
      matchStmt.setInt(1, mapIndex)
      matchStmt.setInt(2, durationMinutes)
      matchStmt.setLong(3, System.currentTimeMillis())
      matchStmt.setInt(4, results.size)
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

  /** Returns (matchId, mapIndex, durationMinutes, playedAt, kills, deaths, rank, playerCount) */
  def getMatchHistory(playerUUID: UUID, limit: Int = 20): Seq[(Long, Int, Int, Long, Int, Int, Int, Int)] = {
    val stmt = connection.prepareStatement(
      """SELECT m.match_id, m.map_index, m.duration_minutes, m.played_at,
        |       mr.kills, mr.deaths, mr.rank, m.player_count
        |FROM match_results mr
        |JOIN matches m ON mr.match_id = m.match_id
        |WHERE mr.player_uuid = ?
        |ORDER BY m.played_at DESC
        |LIMIT ?""".stripMargin
    )
    stmt.setString(1, playerUUID.toString)
    stmt.setInt(2, limit)
    val rs = stmt.executeQuery()

    val results = scala.collection.mutable.ArrayBuffer[(Long, Int, Int, Long, Int, Int, Int, Int)]()
    while (rs.next()) {
      results += ((
        rs.getLong("match_id"),
        rs.getInt("map_index"),
        rs.getInt("duration_minutes"),
        rs.getLong("played_at"),
        rs.getInt("kills"),
        rs.getInt("deaths"),
        rs.getInt("rank"),
        rs.getInt("player_count")
      ))
    }
    rs.close()
    stmt.close()
    results.toSeq
  }

  /** Returns (totalKills, totalDeaths, matchesPlayed, wins, elo) */
  def getPlayerStats(playerUUID: UUID): (Int, Int, Int, Int, Int) = {
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
  def getLeaderboard(limit: Int = 50): Seq[(String, Int, Int, Int)] = {
    val stmt = connection.prepareStatement("SELECT username, elo FROM accounts ORDER BY elo DESC LIMIT ?")
    stmt.setInt(1, limit)
    val rs = stmt.executeQuery()

    val results = scala.collection.mutable.ArrayBuffer[(String, Int, Int, Int)]()
    while (rs.next()) {
      val uname = rs.getString("username")
      val elo = rs.getInt("elo")
      val uuid = getOrCreateUUID(uname)
      val (_, _, matchesPlayed, wins, _) = getPlayerStats(uuid)
      results += ((uname, elo, wins, matchesPlayed))
    }
    rs.close()
    stmt.close()
    results.toSeq
  }

  def getElo(username: String): Int = {
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

  def updateElo(username: String, newElo: Int): Unit = {
    val stmt = connection.prepareStatement("UPDATE accounts SET elo = ? WHERE username = ?")
    stmt.setInt(1, newElo)
    stmt.setString(2, username.toLowerCase)
    stmt.executeUpdate()
    stmt.close()
  }

  def getUsernameByUUID(uuid: UUID): String = {
    val stmt = connection.prepareStatement("SELECT username FROM accounts")
    val rs = stmt.executeQuery()
    var found: String = null
    while (rs.next() && found == null) {
      val username = rs.getString("username")
      val derivedUUID = getOrCreateUUID(username)
      if (derivedUUID.equals(uuid)) {
        found = username
      }
    }
    rs.close()
    stmt.close()
    found
  }

  private def generateSalt(): String = {
    val random = new SecureRandom()
    val salt = new Array[Byte](16)
    random.nextBytes(salt)
    bytesToHex(salt)
  }

  private def hashPassword(password: String, salt: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val salted = (salt + password).getBytes(StandardCharsets.UTF_8)
    bytesToHex(digest.digest(salted))
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

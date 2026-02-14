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

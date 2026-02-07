package com.gridgame.common.model

import java.net.InetAddress
import java.util.Objects
import java.util.UUID

class Player(
    private val id: UUID,
    private var name: String,
    private var position: Position,
    private var colorRGB: Int
) {
  Objects.requireNonNull(id, "Player ID cannot be null")
  Objects.requireNonNull(position, "Position cannot be null")

  private var lastUpdateTime: Long = System.currentTimeMillis()
  private var address: InetAddress = _
  private var port: Int = 0

  def getId: UUID = id

  def getName: String = name

  def setName(name: String): Unit = {
    this.name = name
  }

  def getPosition: Position = position

  def setPosition(position: Position): Unit = {
    this.position = Objects.requireNonNull(position, "Position cannot be null")
    this.lastUpdateTime = System.currentTimeMillis()
  }

  def getColorRGB: Int = colorRGB

  def setColorRGB(colorRGB: Int): Unit = {
    this.colorRGB = colorRGB
  }

  def getLastUpdateTime: Long = lastUpdateTime

  def updateHeartbeat(): Unit = {
    this.lastUpdateTime = System.currentTimeMillis()
  }

  def getAddress: InetAddress = address

  def setAddress(address: InetAddress): Unit = {
    this.address = address
  }

  def getPort: Int = port

  def setPort(port: Int): Unit = {
    this.port = port
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: Player => this.id.equals(that.id)
      case _ => false
    }
  }

  override def hashCode(): Int = id.hashCode()

  override def toString: String = {
    s"Player{id=${id.toString.substring(0, 8)}, name='$name', position=$position}"
  }
}

object Player {
  def generateColorFromUUID(id: UUID): Int = {
    val hash = id.getLeastSignificantBits
    val hue = (hash % 360) / 360.0f
    val saturation = 0.7f
    val brightness = 0.9f

    val rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness)
    0xFF000000 | (rgb & 0x00FFFFFF)
  }
}

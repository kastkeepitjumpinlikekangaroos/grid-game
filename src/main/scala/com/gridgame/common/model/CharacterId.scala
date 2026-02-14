package com.gridgame.common.model

sealed abstract class CharacterId(val id: Byte, val name: String)

object CharacterId {
  case object Spaceman extends CharacterId(0, "Spaceman")

  val all: Seq[CharacterId] = Seq(Spaceman)

  val DEFAULT: CharacterId = Spaceman

  def fromId(id: Byte): CharacterId = {
    all.find(_.id == id).getOrElse(DEFAULT)
  }
}

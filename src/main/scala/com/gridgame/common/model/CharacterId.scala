package com.gridgame.common.model

sealed abstract class CharacterId(val id: Byte, val name: String)

object CharacterId {
  case object Spaceman extends CharacterId(0, "Spaceman")
  case object Gladiator extends CharacterId(1, "Gladiator")

  val all: Seq[CharacterId] = Seq(Spaceman, Gladiator)

  val DEFAULT: CharacterId = Spaceman

  def fromId(id: Byte): CharacterId = {
    all.find(_.id == id).getOrElse(DEFAULT)
  }
}

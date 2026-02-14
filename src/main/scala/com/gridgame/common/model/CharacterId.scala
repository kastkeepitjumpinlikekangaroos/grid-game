package com.gridgame.common.model

sealed abstract class CharacterId(val id: Byte, val name: String)

object CharacterId {
  case object Spaceman extends CharacterId(0, "Spaceman")
  case object Gladiator extends CharacterId(1, "Gladiator")
  case object Wraith extends CharacterId(2, "Wraith")
  case object Wizard extends CharacterId(3, "Wizard")

  val all: Seq[CharacterId] = Seq(Spaceman, Gladiator, Wraith, Wizard)

  val DEFAULT: CharacterId = Spaceman

  def fromId(id: Byte): CharacterId = {
    all.find(_.id == id).getOrElse(DEFAULT)
  }
}

package com.gridgame.common.model

sealed abstract class CharacterId(val id: Byte, val name: String)

object CharacterId {
  case object Spaceman extends CharacterId(0, "Spaceman")
  case object Gladiator extends CharacterId(1, "Gladiator")
  case object Wraith extends CharacterId(2, "Wraith")
  case object Wizard extends CharacterId(3, "Wizard")
  case object Tidecaller extends CharacterId(4, "Tidecaller")
  case object Raptor extends CharacterId(5, "Raptor")

  val all: Seq[CharacterId] = Seq(Spaceman, Gladiator, Wraith, Wizard, Tidecaller, Raptor)

  val DEFAULT: CharacterId = Spaceman

  def fromId(id: Byte): CharacterId = {
    all.find(_.id == id).getOrElse(DEFAULT)
  }
}

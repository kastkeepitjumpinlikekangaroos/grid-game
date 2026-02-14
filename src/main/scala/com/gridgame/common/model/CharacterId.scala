package com.gridgame.common.model

sealed abstract class CharacterId(val id: Byte, val name: String)

object CharacterId {
  case object Spaceman extends CharacterId(0, "Spaceman")
  case object Gladiator extends CharacterId(1, "Gladiator")
  case object Wraith extends CharacterId(2, "Wraith")
  case object Tidecaller extends CharacterId(3, "Tidecaller")
  case object Soldier extends CharacterId(4, "Soldier")
  case object Raptor extends CharacterId(5, "Raptor")

  val all: Seq[CharacterId] = Seq(Spaceman, Gladiator, Wraith, Tidecaller, Soldier, Raptor)

  val DEFAULT: CharacterId = Spaceman

  def fromId(id: Byte): CharacterId = {
    all.find(_.id == id).getOrElse(DEFAULT)
  }
}

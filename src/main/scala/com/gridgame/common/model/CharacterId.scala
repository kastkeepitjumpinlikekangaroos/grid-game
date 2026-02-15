package com.gridgame.common.model

case class CharacterId(id: Byte, name: String)

object CharacterId {
  val Spaceman: CharacterId = CharacterId(0, "Spaceman")
  val Gladiator: CharacterId = CharacterId(1, "Gladiator")
  val Wraith: CharacterId = CharacterId(2, "Wraith")
  val Wizard: CharacterId = CharacterId(3, "Wizard")
  val Tidecaller: CharacterId = CharacterId(4, "Tidecaller")
  val Soldier: CharacterId = CharacterId(5, "Soldier")
  val Raptor: CharacterId = CharacterId(6, "Raptor")
  val Assassin: CharacterId = CharacterId(7, "Assassin")
  val Warden: CharacterId = CharacterId(8, "Warden")

  val DEFAULT: CharacterId = Spaceman

  def fromId(id: Byte): CharacterId =
    CharacterDef.all.map(_.id).find(_.id == id).getOrElse(DEFAULT)
}

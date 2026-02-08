package com.gridgame.common.model

class Item(
    val id: Int,
    val x: Int,
    val y: Int,
    val itemType: ItemType
) {

  def getCellX: Int = x

  def getCellY: Int = y

  def colorRGB: Int = itemType.colorRGB

  override def toString: String = {
    s"Item{id=$id, type=${itemType.name}, pos=($x, $y)}"
  }
}

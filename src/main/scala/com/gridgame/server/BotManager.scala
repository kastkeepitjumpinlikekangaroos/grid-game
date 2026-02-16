package com.gridgame.server

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._
import com.gridgame.common.model.CharacterDef
import scala.util.Random

case class BotSlot(id: UUID, name: String, characterId: Byte)

class BotManager {
  private val botSlots = new ConcurrentHashMap[UUID, BotSlot]()
  private val nextBotIndex = new AtomicInteger(1)

  def addBot(): BotSlot = {
    val index = nextBotIndex.getAndIncrement()
    val botId = new UUID(0L, index.toLong)
    val name = s"Bot $index"
    val idx = Random.nextInt(CharacterDef.all.size)
    val charId = CharacterDef.all(idx).id.id
    val slot = BotSlot(botId, name, charId)
    botSlots.put(botId, slot)
    slot
  }

  def removeBot(id: UUID): Boolean = {
    botSlots.remove(id) != null
  }

  def removeLastBot(): Option[BotSlot] = {
    val all = getBots
    if (all.isEmpty) return None
    val last = all.maxBy(_.id.getLeastSignificantBits)
    botSlots.remove(last.id)
    Some(last)
  }

  def getBots: Seq[BotSlot] = {
    botSlots.values().asScala.toSeq.sortBy(_.id.getLeastSignificantBits)
  }

  def botCount: Int = botSlots.size()

  def isBot(id: UUID): Boolean = id.getMostSignificantBits == 0L && id.getLeastSignificantBits > 0L

  def clear(): Unit = botSlots.clear()
}

object BotManager {
  def isBotUUID(id: UUID): Boolean = id.getMostSignificantBits == 0L && id.getLeastSignificantBits > 0L
}

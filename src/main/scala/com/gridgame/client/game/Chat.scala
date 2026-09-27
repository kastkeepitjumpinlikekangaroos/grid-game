package com.gridgame.client.game

import com.gridgame.common.Constants
import com.gridgame.common.protocol._

import java.util.concurrent.CopyOnWriteArrayList

/** Lobby and match chat: the last fifty lines, the line being typed, and sending one. The lobby's
  * own notices (someone joined, someone left) are lines of it too. */
trait Chat { this: GameClient =>
  // Chat state: each entry is Array(timestamp: Long, senderName: String, message: String, scope: Byte)
  val chatMessages: CopyOnWriteArrayList[Array[AnyRef]] = new CopyOnWriteArrayList[Array[AnyRef]]()
  @volatile var chatMessageListener: () => Unit = _
  @volatile var isChatOpen: Boolean = false
  @volatile var chatInputText: String = ""

  private[game] def forgetChat(): Unit = chatMessages.clear()

  private[game] def addLobbySystemMessage(text: String): Unit = {
    chatMessages.add(Array(
      System.currentTimeMillis().asInstanceOf[AnyRef],
      "".asInstanceOf[AnyRef],
      text.asInstanceOf[AnyRef],
      ChatScope.LOBBY.asInstanceOf[AnyRef]
    ))
    while (chatMessages.size() > 50) chatMessages.remove(0)
    val listener = chatMessageListener
    if (listener != null) listener()
  }

  private[game] def handleChatMessage(packet: ChatMessagePacket): Unit = {
    val senderId = packet.getPlayerId
    val senderName = if (senderId.equals(localPlayerId)) playerName else {
      // Try lobby members first, then players map, then truncated UUID
      import scala.jdk.CollectionConverters._
      val memberName = lobbyMembers.asScala.find(_.id == senderId).map(_.name)
      memberName.getOrElse {
        val p = players.get(senderId)
        if (p != null) p.getName else senderId.toString.substring(0, 8)
      }
    }
    chatMessages.add(Array(
      System.currentTimeMillis().asInstanceOf[AnyRef],
      senderName.asInstanceOf[AnyRef],
      packet.getMessage.asInstanceOf[AnyRef],
      packet.getScope.asInstanceOf[AnyRef]
    ))
    while (chatMessages.size() > 50) chatMessages.remove(0)
    val listener = chatMessageListener
    if (listener != null) listener()
  }

  def sendChatMessage(message: String, scope: Byte): Unit = {
    val msgBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val truncated = if (msgBytes.length > Constants.MAX_CHAT_MESSAGE_LEN) {
      new String(msgBytes, 0, Constants.MAX_CHAT_MESSAGE_LEN, java.nio.charset.StandardCharsets.UTF_8)
    } else message
    val packet = new ChatMessagePacket(
      sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp,
      scope, truncated
    )
    send(packet)
  }
}

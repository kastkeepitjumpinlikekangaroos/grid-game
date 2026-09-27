package com.gridgame.client.render
package hud

import com.gridgame.client.i18n.Messages

import com.gridgame.client.game.ClientState

/** The chat lines fading out along the left, and the line being typed. */
private[render] final class ChatPainter(ctx: RenderContext) {
  import ctx._

  // Deferred chat entries (same pattern as kill feed)
  private val MAX_CHAT_DISPLAY = 8
  private val _chatX = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatY = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatW = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatA = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatText = new Array[String](MAX_CHAT_DISPLAY)
  private val _chatR = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatG = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatB = new Array[Float](MAX_CHAT_DISPLAY)
  private var _chatCount = 0

  def draw(screenW: Int, screenH: Int): Unit = {
    if (client.clientState != ClientState.PLAYING) return

    val now = frameTimeMs
    val chatOpen = client.isChatOpen
    val baseY = screenH - 120f // above inventory bar

    // Collect visible messages into deferred arrays
    _chatCount = 0
    val iter = client.chatMessages.iterator()
    // Collect all eligible messages first, then take last MAX_CHAT_DISPLAY
    val tempEntries = new java.util.ArrayList[Array[AnyRef]]()
    while (iter.hasNext) {
      val entry = iter.next()
      val timestamp = entry(0).asInstanceOf[java.lang.Long].longValue()
      val elapsed = now - timestamp
      if (chatOpen || elapsed < 10000) {
        tempEntries.add(entry)
      }
    }

    // Take last MAX_CHAT_DISPLAY entries
    val startIdx = Math.max(0, tempEntries.size() - MAX_CHAT_DISPLAY)
    var i = startIdx
    while (i < tempEntries.size()) {
      val entry = tempEntries.get(i)
      val timestamp = entry(0).asInstanceOf[java.lang.Long].longValue()
      val sender = entry(1).asInstanceOf[String]
      val msg = entry(2).asInstanceOf[String]
      val scope = entry(3).asInstanceOf[java.lang.Byte].byteValue()
      val elapsed = now - timestamp

      val alpha = if (chatOpen) 0.9f
                  else Math.max(0.1f, (1.0 - elapsed / 10000.0).toFloat)

      val displayText = if (sender.isEmpty) msg else sender + ": " + msg
      val textW = fontSmall.measureWidth(displayText)
      val ci = _chatCount
      val yPos = baseY - (tempEntries.size() - startIdx - _chatCount) * 18f
      _chatX(ci) = 12f
      _chatY(ci) = yPos
      _chatW(ci) = textW
      _chatA(ci) = alpha
      _chatText(ci) = displayText

      if (sender.isEmpty) {
        // System message
        _chatR(ci) = 0.5f; _chatG(ci) = 0.55f; _chatB(ci) = 0.6f
      } else if (scope == 2) {
        // Team chat - green
        _chatR(ci) = 0.3f; _chatG(ci) = 1f; _chatB(ci) = 0.4f
      } else {
        // Game/lobby chat - white
        _chatR(ci) = 1f; _chatG(ci) = 1f; _chatB(ci) = 1f
      }
      _chatCount += 1
      i += 1
    }

    // Render backgrounds (shapes pass)
    if (_chatCount > 0 || chatOpen) {
      beginShapes()
      var ci = 0
      while (ci < _chatCount) {
        shapeBatch.fillRoundedRect(_chatX(ci) - 4f, _chatY(ci) - 2f, _chatW(ci) + 8f, 18f, 4f, 0.04f, 0.04f, 0.08f, 0.4f * _chatA(ci))
        ci += 1
      }

      // Input box when chat is open
      if (chatOpen) {
        val inputY = baseY + 4f
        shapeBatch.fillRoundedRect(8f, inputY, 340f, 24f, 6f, 0.06f, 0.06f, 0.12f, 0.85f)
      }

      // Render text (sprites pass)
      beginSprites()
      ci = 0
      while (ci < _chatCount) {
        fontSmall.drawText(spriteBatch, _chatText(ci), _chatX(ci), _chatY(ci), _chatR(ci), _chatG(ci), _chatB(ci), _chatA(ci))
        ci += 1
      }

      if (chatOpen) {
        val inputY = baseY + 4f
        val inputText = client.chatInputText
        val cursor = if ((now / 500) % 2 == 0) "_" else ""
        fontSmall.drawText(spriteBatch, "> " + inputText + cursor, 14f, inputY + 4f, 0.9f, 0.9f, 0.95f, 1f)
        fontSmall.drawText(spriteBatch, Messages.t("[Enter] Send  [Shift+Enter] Team  [Esc] Cancel"), 14f, inputY + 28f, 0.45f, 0.45f, 0.5f, 0.7f)
      }
    }
  }
}

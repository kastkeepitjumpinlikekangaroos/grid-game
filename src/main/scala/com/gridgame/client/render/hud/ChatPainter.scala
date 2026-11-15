package com.gridgame.client.render
package hud

import com.gridgame.client.i18n.Messages

import com.gridgame.client.game.ClientState

/**
 * The chat lines fading out along the left over the tray, and the line being typed, in the HUD's
 * glass (hud/HudStyle): each line on a pane of its own, its sender in their colour — a teammate's in
 * green, everyone else's in sky blue, the game's own grey — and what they said in white; the line
 * being typed in a field rimmed as the menus' focused fields are, with the keys that send it.
 *
 * What a line says is made once, the first frame it is shown, and kept for as long as it is: it built
 * every line again, and a list of them, every frame. Nothing is allocated as it draws but the
 * chat's iterators.
 */
private[render] final class ChatPainter(ctx: RenderContext) {
  import ctx._
  import HudStyle._

  private val style = new HudStyle(ctx)

  private val MAX_CHAT_DISPLAY = 8
  // The lines shown, and what each says, kept by the message they were made from
  private val _from = new Array[Array[AnyRef]](MAX_CHAT_DISPLAY)
  private val _sender = new Array[String](MAX_CHAT_DISPLAY)
  private val _text = new Array[String](MAX_CHAT_DISPLAY)
  // A spare set to swap with, so a line that moves up keeps what it says
  private val _from2 = new Array[Array[AnyRef]](MAX_CHAT_DISPLAY)
  private val _sender2 = new Array[String](MAX_CHAT_DISPLAY)
  private val _text2 = new Array[String](MAX_CHAT_DISPLAY)
  private val _alpha = new Array[Float](MAX_CHAT_DISPLAY)
  private val _scope = new Array[Int](MAX_CHAT_DISPLAY)
  private var _count = 0

  private var sSend, sTeam, sCancel: String = _
  private val EnterKey = "Enter"; private val ShiftKey = "Shift"; private val EscKey = "Esc"
  // The line being typed as it was last drawn, so it is only measured again when it changes
  private var _typedFor: String = null; private var _typedW = 0f

  def draw(screenW: Int, screenH: Int): Unit = {
    if (client.clientState != ClientState.PLAYING) return
    if (sSend == null) { sSend = Messages.t("Send"); sTeam = Messages.t("Team"); sCancel = Messages.t("Cancel") }
    val now = frameTimeMs
    val chatOpen = client.isChatOpen
    val baseY = screenH - TrayBottom - (SlotSize + TrayPadY * 2) - 18f - (if (chatOpen) 64f else 0f)

    // The last few lines still showing (all of them while the chat is open), newest last
    var eligible = 0
    var it = client.chatMessages.iterator()
    while (it.hasNext) {
      val e = it.next()
      if (chatOpen || now - e(0).asInstanceOf[java.lang.Long].longValue() < 10000L) eligible += 1
    }
    var skip = Math.max(0, eligible - MAX_CHAT_DISPLAY)
    var n = 0
    it = client.chatMessages.iterator()
    while (it.hasNext && n < MAX_CHAT_DISPLAY) {
      val e = it.next()
      val age = now - e(0).asInstanceOf[java.lang.Long].longValue()
      if (chatOpen || age < 10000L) {
        if (skip > 0) skip -= 1
        else {
          keep(n, e)
          _alpha(n) = if (chatOpen) 1f else Math.min(1f, (10000L - age) / 1500f)
          n += 1
        }
      }
    }
    // What is kept is now the lines shown
    var i = 0
    while (i < MAX_CHAT_DISPLAY) {
      _from(i) = _from2(i); _sender(i) = _sender2(i); _text(i) = _text2(i)
      _from2(i) = null; _sender2(i) = null; _text2(i) = null
      i += 1
    }
    _count = n

    val lineH = 30f
    val x = 16f
    i = 0
    while (i < _count) {
      val y = baseY - (_count - i) * (lineH + 4f)
      val a = _alpha(i)
      val sw = if (_sender(i) != null) fontSmall.measureWidth(_sender(i)) + 6f else 0f
      val w = Math.round(sw + fontSmall.measureWidth(_text(i)) + 22f).toFloat
      style.glass(x, y, w, lineH, 9f, 0.85f * a)
      beginSprites()
      val ty = fontSmall.digitsCentredOn(y + lineH / 2f)
      if (_sender(i) != null) {
        val sr = if (_scope(i) == 2) LeafR else SkyR; val sg = if (_scope(i) == 2) LeafG else SkyG
        val sb = if (_scope(i) == 2) LeafB else SkyB
        fontSmall.drawText(spriteBatch, _sender(i), x + 11f, ty, sr, sg, sb, a)
        fontSmall.drawText(spriteBatch, _text(i), x + 11f + sw, ty, TextR, TextG, TextB, a)
      } else fontSmall.drawText(spriteBatch, _text(i), x + 11f, ty, MutedR, MutedG, MutedB, a)
      i += 1
    }

    if (chatOpen) drawInput(x, baseY + 8f, now)
  }

  /** Line `n` of this frame is `e`: what it says, kept from last frame's if it was shown then. */
  private def keep(n: Int, e: Array[AnyRef]): Unit = {
    var j = 0
    while (j < MAX_CHAT_DISPLAY && (_from(j) ne e)) j += 1
    if (j < MAX_CHAT_DISPLAY) {
      _from2(n) = e; _sender2(n) = _sender(j); _text2(n) = _text(j)
    } else {
      val sender = e(1).asInstanceOf[String]
      _from2(n) = e
      _sender2(n) = if (sender.isEmpty) null else sender + ":"
      _text2(n) = e(2).asInstanceOf[String]
    }
    _scope(n) = e(3).asInstanceOf[java.lang.Byte].byteValue()
  }

  /** The line being typed, in a field, and under it the keys that send it to everyone or the team. */
  private def drawInput(x: Float, y: Float, now: Long): Unit = {
    val w = 380f; val h = 34f
    style.glass(x, y, w, h, 10f)
    shapeBatch.strokeRoundedRectSmooth(x, y, w, h, 10f, 1.5f, SkyR, SkyG, SkyB, 0.9f, 0.7f)
    val typed = client.chatInputText
    if (typed ne _typedFor) { _typedFor = typed; _typedW = fontSmall.measureWidth(typed) }
    beginSprites()
    val ty = fontSmall.digitsCentredOn(y + h / 2f)
    fontSmall.drawText(spriteBatch, typed, x + 12f, ty, TextR, TextG, TextB, 1f)
    if ((now / 500) % 2 == 0) {
      beginShapes()
      shapeBatch.fillRect(x + 13f + _typedW, y + 9f, 2f, h - 18f, SkyR, SkyG, SkyB, 1f)
    }
    // [Enter] Send   [Shift] [Enter] Team   [Esc] Cancel, on a pane of their own
    val enterW = style.keycapWidth(EnterKey, fontTiny)
    val rowW = enterW * 2 + style.keycapWidth(ShiftKey, fontTiny) + style.keycapWidth(EscKey, fontTiny) +
      fontTiny.measureWidth(sSend) + fontTiny.measureWidth(sTeam) + fontTiny.measureWidth(sCancel) + 6f * 3 + 4f + 14f * 2
    style.glass(x, y + h + 6f, Math.round(rowW + 16f).toFloat, 30f, 10f, 0.9f)
    var kx = x + 8f
    val ky = y + h + 6f + 5f
    kx += style.keycap(kx, ky, EnterKey, fontTiny) + 6f
    kx += hint(sSend, kx, ky) + 14f
    kx += style.keycap(kx, ky, ShiftKey, fontTiny) + 4f
    kx += style.keycap(kx, ky, EnterKey, fontTiny) + 6f
    kx += hint(sTeam, kx, ky) + 14f
    kx += style.keycap(kx, ky, EscKey, fontTiny) + 6f
    hint(sCancel, kx, ky)
  }

  private def hint(text: String, x: Float, keyY: Float): Float = {
    beginSprites()
    fontTiny.drawText(spriteBatch, text, x, fontTiny.digitsCentredOn(keyY + (KeycapH - 2f) / 2f), BodyR, BodyG, BodyB, 1f)
  }
}

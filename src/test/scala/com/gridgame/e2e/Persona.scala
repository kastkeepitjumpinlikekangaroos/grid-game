package com.gridgame.e2e

import com.gridgame.client.game.ClientState
import com.gridgame.common.model._
import org.lwjgl.glfw.GLFW._

import java.util.UUID
import scala.jdk.CollectionConverters._

/**
 * An honest player at the keyboard and the mouse, doing what a player can: walking (keys held, at the
 * client's own pace), aiming at whoever it can see or anywhere at all, firing and charging, taking Q
 * and E up and casting them with a click or putting them away, bursting, using what it carries,
 * pressing R, chatting, and with `leaves`, now and then leaving the match (Esc, twice). It only
 * presses keys and moves and clicks the mouse: every request it makes is the client's own.
 */
final class Persona(c: LoopClient, rnd: scala.util.Random, leaves: Boolean = false, chats: Boolean = true) {
  private val client = c.client
  private val moveKeys = Seq(GLFW_KEY_W, GLFW_KEY_A, GLFW_KEY_S, GLFW_KEY_D)
  private var held = Set.empty[Int]
  private var walkUntil = 0L
  private var heading: Option[Boolean] = None // toward the foe, or away; None: a direction of its own
  private var wander: Set[Int] = Set.empty
  private var nextAttack = 0L
  private var releaseAt = -1L // the primary held down until then
  private var clickAt = -1L // Q or E taken up, cast by a click then
  private var focus: UUID = _
  private var spot: (Double, Double) = (0.0, 0.0)

  /** One frame's worth of the player's hands. */
  def act(): Unit = {
    if (client.clientState != ClientState.PLAYING || client.getIsDead) {
      letGo()
      return
    }
    val now = System.currentTimeMillis()
    aim()
    walk(now)
    attack(now)
  }

  private def foes: Seq[Player] = client.getPlayers.values().asScala.toSeq
    .filter(p => !p.isDead && (client.localTeamId == 0 || p.getTeamId != client.localTeamId))

  private def foe: Option[Player] = {
    val known = foes
    Option(focus).flatMap(id => known.find(_.getId == id)).orElse {
      val me = client.getLocalPosition
      val nearest = known.sortBy(p => Math.abs(p.getPosition.getX - me.getX) + Math.abs(p.getPosition.getY - me.getY)).headOption
      focus = nearest.map(_.getId).orNull
      nearest
    }
  }

  /** The mouse on the foe, more or less, or now and then anywhere near. */
  private def aim(): Unit = {
    if (rnd.nextInt(40) == 0) {
      focus = null
      val me = client.getLocalPosition
      spot = (me.getX + rnd.nextGaussian() * 6, me.getY + rnd.nextGaussian() * 6)
    }
    foe match {
      case Some(p) if rnd.nextInt(8) != 0 =>
        c.aimAt(p.getPosition.getX + rnd.nextGaussian() * 0.8, p.getPosition.getY + rnd.nextGaussian() * 0.8)
      case _ => c.aimAt(spot._1, spot._2)
    }
  }

  /** WASD as the keyboard handler reads them (isometric): the keys for a step of (dx, dy). */
  private def keysFor(dx: Int, dy: Int): Set[Int] = (Integer.signum(dx), Integer.signum(dy)) match {
    case (-1, -1) => Set(GLFW_KEY_W)
    case (1, 1) => Set(GLFW_KEY_S)
    case (-1, 1) => Set(GLFW_KEY_A)
    case (1, -1) => Set(GLFW_KEY_D)
    case (-1, 0) => Set(GLFW_KEY_W, GLFW_KEY_A)
    case (1, 0) => Set(GLFW_KEY_S, GLFW_KEY_D)
    case (0, -1) => Set(GLFW_KEY_W, GLFW_KEY_D)
    case (0, 1) => Set(GLFW_KEY_S, GLFW_KEY_A)
    case _ => Set.empty
  }

  private def walk(now: Long): Unit = {
    if (now >= walkUntil) {
      walkUntil = now + 120 + rnd.nextInt(900)
      // Mostly after the nearest foe: a fight is where most of what can go wrong does
      rnd.nextInt(20) match {
        case r if r < 14 => heading = Some(true)
        case r if r < 15 => heading = Some(false)
        case r if r < 18 =>
          heading = None
          wander = keysFor(rnd.nextInt(3) - 1, rnd.nextInt(3) - 1)
        case _ =>
          heading = None
          wander = Set.empty
      }
    }
    val want = heading match {
      case Some(toward) =>
        foe.map { p =>
          val me = client.getLocalPosition
          val (dx, dy) = (p.getPosition.getX - me.getX, p.getPosition.getY - me.getY)
          // Close enough to fight: circle rather than stand on them
          if (toward && Math.abs(dx) + Math.abs(dy) <= 2) keysFor(-dy, dx)
          else if (toward) keysFor(dx, dy) else keysFor(-dx, -dy)
        }.getOrElse(wander)
      case None => wander
    }
    if (want != held) {
      (held -- want).foreach(c.release)
      (want -- held).foreach(k => c.press(k))
      held = want
    }
  }

  private def charges: Boolean =
    ProjectileDef.get(client.getSelectedCharacterDef.primaryProjectileType).chargeSpeedScaling.isDefined

  private def attack(now: Long): Unit = {
    if (releaseAt >= 0 && now >= releaseAt) { c.mouseUp(); releaseAt = -1 }
    if (clickAt >= 0 && now >= clickAt) {
      if (rnd.nextInt(10) == 0) c.rightClick() else { c.mouseDown(); c.mouseUp() }
      clickAt = -1
    }
    if (now < nextAttack || releaseAt >= 0 || clickAt >= 0) return
    nextAttack = now + 120 + rnd.nextInt(700)
    rnd.nextInt(200) match {
      case r if r < 90 =>
        // The primary: held to aim (or to charge) and let go
        c.mouseDown()
        releaseAt = now + (if (charges) 200 + rnd.nextInt(2300) else rnd.nextInt(150))
      case r if r < 160 =>
        // Q or E: taken up, and cast by the next click (one with nothing to aim goes off at once)
        val key = if (rnd.nextBoolean()) GLFW_KEY_Q else GLFW_KEY_E
        c.press(key)
        c.release(key)
        clickAt = now + 30 + rnd.nextInt(320)
      case r if r < 170 =>
        c.press(GLFW_KEY_SPACE, GLFW_MOD_SHIFT)
        c.release(GLFW_KEY_SPACE)
      case r if r < 188 =>
        // An item: one it carries, mostly
        val carried = Persona.ItemKeys.keys.filter(t => client.getItemCount(t.id) > 0).toSeq
        val kinds = if (carried.nonEmpty && rnd.nextInt(4) != 0) carried else Persona.ItemKeys.keys.toSeq
        val key = Persona.ItemKeys(kinds(rnd.nextInt(kinds.size)))
        c.press(key)
        c.release(key)
      case r if r < 193 =>
        c.press(GLFW_KEY_R)
        c.release(GLFW_KEY_R)
      case r if r < 196 && chats =>
        // T, a few words (as GLFW's character callback types them), Enter
        letGo()
        c.press(GLFW_KEY_T)
        c.keys.chatInputBuffer.append(Seq("gg", "nice", "over here", "!")(rnd.nextInt(4)))
        client.chatInputText = c.keys.chatInputBuffer.toString
        c.press(GLFW_KEY_ENTER)
      case 199 if leaves =>
        letGo()
        c.press(GLFW_KEY_ESCAPE)
        c.press(GLFW_KEY_ESCAPE)
      case _ =>
    }
  }

  /** Every key and button let go of. */
  def letGo(): Unit = {
    held.foreach(c.release)
    held = Set.empty
    if (releaseAt >= 0) { c.mouseUp(); releaseAt = -1 }
    clickAt = -1
  }
}

object Persona {
  /** The number keys the keyboard handler uses each item with. */
  val ItemKeys: Map[ItemType, Int] = Map(ItemType.Heart -> GLFW_KEY_1, ItemType.Star -> GLFW_KEY_2,
    ItemType.Gem -> GLFW_KEY_3, ItemType.Shield -> GLFW_KEY_4, ItemType.Fence -> GLFW_KEY_5)
}

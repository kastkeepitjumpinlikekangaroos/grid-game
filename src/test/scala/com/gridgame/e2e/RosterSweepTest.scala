package com.gridgame.e2e

import com.gridgame.client.game.Attacks
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model._
import com.gridgame.common.protocol.AttackSlot
import org.junit.Test
import org.lwjgl.glfw.GLFW._

import scala.collection.mutable

/**
 * Every attack of every character in the roster — the primary, Q, E and the burst — cast by the
 * real client as a player casts it (the button held and let go, a key, a click) and judged by the
 * real server, on a strict loopback (Loopback): each one must be taken, and every client must
 * agree with the server about everyone in its match all the while. The whole roster plays at once,
 * a match on every map of the rotation.
 *
 * A character whose client and server disagree about one of its attacks — a cast the client makes
 * that the server won't take, a step of a dash or a blink it refuses, a barrier or a phase it
 * doesn't put up — fails here by name.
 */
class RosterSweepTest {
  @Test def everyAttackOfEveryCharacterIsCastAndTaken(): Unit = Loopback(seed = 7) { loop =>
    loop.openingMs = 0
    // Anyone caught by a neighbour's attack is soon back to finish
    loop.respawnDelayMs = Some(300)
    val roster = CharacterDef.all
    val groups = roster.grouped((roster.size + WorldRegistry.size - 1) / WorldRegistry.size).toSeq
    val clients = groups.map(_.map(ch => loop.connect(s"sweep${ch.id.id & 0xFF}") -> ch))
    loop.logIn(clients.flatten.map(_._1))

    // A free-for-all on each map of the rotation, its players playing the characters of its share of the roster
    for ((group, map) <- clients.zipWithIndex) {
      group.foreach { case (c, ch) => c.pick(ch.id) }
      val host = group.head._1
      val lobbyId = host.createLobby(map)
      loop.joinLobby(group.tail.map(_._1), lobbyId)
      host.client.startGame()
    }
    loop.await("every match", 10000)(clients.flatten.forall { case (c, ch) =>
      c.playing && c.client.selectedCharacterId == ch.id.id
    })

    val sweeps = clients.flatten.map { case (c, ch) => c -> new Sweep(c, ch, loop.rnd) }.toMap
    loop.await("every attack of every character", 20000, c => sweeps.get(c).foreach(_.act()))(sweeps.values.forall(_.done))
    loop.settle()
    val missed = sweeps.values.flatMap(_.missed).toSeq
    loop.check(missed.isEmpty, s"never cast: ${missed.mkString(", ")}")
    loop.say(s"RosterSweepTest: ${roster.size} characters, " +
      loop.tally.toSeq.filterNot(_._1.startsWith("sent ")).sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", "))
  }

  /**
   * One character's attacks, one after another as a player would make them: the primary (held to
   * charge if it charges), Q and E (taken up by their key and cast by a click, or cast as the key goes
   * down), and the burst. An ability that can't go off where it is aimed (a trap with nowhere to land,
   * a dash or a blink with nowhere to go) is aimed somewhere else and tried again.
   */
  private final class Sweep(c: LoopClient, ch: CharacterDef, rnd: scala.util.Random) {
    private val client = c.client
    private val todo = mutable.Queue[String]("primary", "burst", "Q", "E")
    private var tries = 0
    private var next = 0L
    private var pending: Option[() => Boolean] = None
    val missed = mutable.Buffer[String]()

    def done: Boolean = todo.isEmpty

    // Somewhere a few cells off, every try a different way
    private def aimAway(): Unit = {
      val pos = client.getLocalPosition
      val a = rnd.nextDouble() * 2 * Math.PI
      val d = 3 + rnd.nextInt(4)
      c.aimAt(pos.getX + Math.cos(a) * d, pos.getY + Math.sin(a) * d)
    }

    private def cooledDown(slot: Int): Boolean =
      (if (slot == 0) client.getQCooldownRemaining else client.getECooldownRemaining) <= 0f

    /** One frame of the player's hands. */
    def act(): Unit = {
      val now = System.currentTimeMillis()
      // Nothing is cast while dead, dashing, phased (a phase cast as Q holds E back for its length) or
      // held by a neighbour's attack: waited out, not counted as a try
      if (done || now < next || !c.playing || client.getIsDead || client.isSwooping) return
      if (pending.isEmpty && (client.isPhased || client.isFrozen || client.isRooted)) return
      pending match {
        case Some(finish) =>
          // The second half of an attack: the button let go of, or the click that casts what is aimed
          if (finish()) { todo.dequeue(); tries = 0 } else retry()
          pending = None
          next = now + 120
        case None =>
          aimAway()
          val what = todo.head
          val before = c.shotsSent(slotOf(what))
          what match {
            case "primary" =>
              val charges = ProjectileDef.get(ch.primaryProjectileType).chargeSpeedScaling.isDefined
              c.mouseDown()
              pending = Some(() => { c.mouseUp(); c.shotsSent(AttackSlot.PRIMARY) > before })
              next = now + (if (charges) 400 else 30)
            case "burst" =>
              c.press(GLFW_KEY_SPACE, GLFW_MOD_SHIFT)
              c.release(GLFW_KEY_SPACE)
              pending = Some(() => c.shotsSent(AttackSlot.BURST) >= before + AttackSlot.BurstDirections.size)
              next = now + 30
            case ability =>
              val slot = if (ability == "Q") 0 else 1
              val key = if (slot == 0) GLFW_KEY_Q else GLFW_KEY_E
              val defn = if (slot == 0) ch.qAbility else ch.eAbility
              c.press(key)
              c.release(key)
              pending = Some(() => {
                // Taken up to be aimed: the click casts it. One that went off as its key went down
                // needs none, and a click would only fire the primary
                if (!Attacks.goesOffOnPress(defn)) { c.mouseDown(); c.mouseUp() }
                !cooledDown(slot)
              })
              next = now + 60
          }
      }
    }

    private def slotOf(what: String): Int = what match {
      case "primary" => AttackSlot.PRIMARY
      case "Q" => AttackSlot.Q
      case "E" => AttackSlot.E
      case _ => AttackSlot.BURST
    }

    private def retry(): Unit = {
      tries += 1
      if (tries >= 8) {
        missed += s"${ch.displayName}'s ${todo.head}"
        todo.dequeue()
        tries = 0
      }
    }
  }
}

package com.gridgame.client

import com.gridgame.client.audio.AudioManager
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.{After, Before, Test}

import java.util.UUID
import scala.collection.mutable

/**
 * What is heard of an ability that throws nothing — a dash, a blink, a phase, a barrier, a trap:
 * ours as our character's own, and another player's as theirs, once a cast. Each kind used to make
 * one sound for the whole roster, and only our own made any (client/audio/CLAUDE.md).
 */
class GameClientSoundsTest {
  private val heard = mutable.Buffer[String]()

  @Before def listen(): Unit = AudioManager.heardBy = s => heard.synchronized { heard += s }
  @After def stopListening(): Unit = AudioManager.heardBy = null

  private def sounds: Seq[String] = heard.synchronized(heard.toList)
  private def count(sound: String): Int = sounds.count(_ == sound)

  /** A match under way with us at (30, 30) as `me`, and `them` standing five cells off. */
  private def matchWith(them: CharacterId, me: CharacterId = CharacterId.Soldier): (TestClient, UUID) = {
    val t = new TestClient()
    t.client.selectedCharacterId = me.id
    val other = UUID.randomUUID()
    t.startMatch(spawn = (30, 30))
    t.join(other, 35, 30, charId = them.id)
    t.update(other, 35, 30, charId = them.id)
    heard.synchronized(heard.clear())
    (t, other)
  }

  @Test def ourBlinkIsOurCharactersAndNotThePlainOne(): Unit = {
    val (t, _) = matchWith(CharacterId.Soldier, me = CharacterId.Wizard)
    t.client.setMouseWorldPosition(36.0, 30.0)
    assertTrue("the Wizard's Blink was not cast", t.client.shootAbility(1))
    assertEquals(Seq("cast_blink"), sounds.filter(s => s.startsWith("cast_") || s == "teleport"))
  }

  @Test def ourLeapIsNotTheWizardsBlink(): Unit = {
    val (t, _) = matchWith(CharacterId.Soldier, me = CharacterId.Barbarian)
    t.client.setMouseWorldPosition(36.0, 30.0)
    assertTrue("the Barbarian's Leap was not cast", t.client.shootAbility(0))
    assertEquals(1, count("cast_leap"))
    assertEquals(0, count("cast_blink"))
  }

  @Test def anotherPlayersBlinkIsHeardAsTheirs(): Unit = {
    val (t, wizard) = matchWith(CharacterId.Wizard)
    t.update(wizard, 41, 30, charId = CharacterId.Wizard.id)      // six cells at once: a blink
    assertEquals(1, count("cast_blink"))
    t.update(wizard, 42, 30, charId = CharacterId.Wizard.id)      // a step: nothing
    assertEquals(1, count("cast_blink"))
  }

  @Test def someoneMovedByAnythingElseIsThePlainTeleport(): Unit = {
    // A Wraith's Haunt puts it behind its target: a jump, and no blink of its own to call it
    val (t, wraith) = matchWith(CharacterId.Wraith)
    t.update(wraith, 41, 30, charId = CharacterId.Wraith.id)
    assertEquals(1, count("teleport"))
  }

  @Test def aKnockbackIsNotABlink(): Unit = {
    val (t, wizard) = matchWith(CharacterId.Wizard)
    t.update(wizard, 41, 30, charId = CharacterId.Wizard.id, knockbacks = 1)
    assertEquals(0, count("cast_blink"))
  }

  @Test def aPhaseIsHeardOnceHoweverLongItLasts(): Unit = {
    val (t, wraith) = matchWith(CharacterId.Wraith)
    for (_ <- 1 to 6) t.update(wraith, 35, 30, charId = CharacterId.Wraith.id, flags = 0x08)
    assertEquals(1, count("cast_phase_shift"))
    // ...past the second an update's word on it is good for, when the next one renews it: still
    // the one cast (the client's own clock, so a real second)
    Thread.sleep(1100)
    t.update(wraith, 35, 30, charId = CharacterId.Wraith.id, flags = 0x08)
    assertEquals(1, count("cast_phase_shift"))
    t.update(wraith, 35, 30, charId = CharacterId.Wraith.id)                   // over
    t.update(wraith, 35, 30, charId = CharacterId.Wraith.id, flags = 0x08)     // and cast again
    assertEquals(2, count("cast_phase_shift"))
  }

  @Test def aDashThatPhasesItsCasterIsHeardAsTheDash(): Unit = {
    val (t, raptor) = matchWith(CharacterId.Raptor)
    t.update(raptor, 36, 30, charId = CharacterId.Raptor.id, flags = 0x08)
    t.update(raptor, 37, 30, charId = CharacterId.Raptor.id, flags = 0x08)
    assertEquals(1, count("cast_swoop"))
    assertEquals(0, count("phase_shift"))
  }

  @Test def aBarrierGoingUpIsHeardOnce(): Unit = {
    val (t, gladiator) = matchWith(CharacterId.Gladiator)
    for (_ <- 1 to 5) t.update(gladiator, 35, 30, charId = CharacterId.Gladiator.id, flags2 = 0x04, barrierMs = 3000)
    assertEquals(1, count("cast_scutum"))
  }

  @Test def aTrapSetDownIsItsSettersOwnAndOursIsNotHeardTwice(): Unit = {
    val (t, warden) = matchWith(CharacterId.Warden)
    t.trap(TrapAction.SPAWN, trapId = 1, trapType = TrapType.BEAR_TRAP, who = warden, x = 36, y = 30)
    assertEquals(Seq("cast_snare_mine"), sounds)
    // Ours was heard as we threw it: the server telling everyone of it is not a second one
    t.trap(TrapAction.SPAWN, trapId = 2, trapType = TrapType.BEAR_TRAP, who = t.id, x = 31, y = 30)
    assertEquals(Seq("cast_snare_mine"), sounds)
  }

  @Test def aCastTooFarAwayToHearIsNotPlayed(): Unit = {
    val (t, wizard) = matchWith(CharacterId.Wizard)
    t.update(wizard, 35, 30, charId = CharacterId.Wizard.id)
    // Out past where an attack is heard at all, then a blink out there
    t.update(wizard, 35, 33, charId = CharacterId.Wizard.id)
    for (y <- 36 to 57 by 3) t.update(wizard, 35, y, charId = CharacterId.Wizard.id)
    heard.synchronized(heard.clear())
    t.update(wizard, 2, 57, charId = CharacterId.Wizard.id)
    assertEquals(0, count("cast_blink"))
  }
}

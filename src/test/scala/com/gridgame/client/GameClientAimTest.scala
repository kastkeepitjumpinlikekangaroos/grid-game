package com.gridgame.client

import com.gridgame.client.game.Attacks
import com.gridgame.client.input.{GLKeyboardHandler, GLMouseHandler}
import com.gridgame.client.render.GameCamera
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test
import org.lwjgl.glfw.GLFW._

/**
 * Casting mode (design/characters.md, *Casting mode*): Q or E takes its ability up to be aimed, we
 * walk as ever while the renderer shows where it will go, and the next click casts it toward the aim
 * of that moment and is nothing more. An ability with nothing to aim (a slam, a phase, a barrier)
 * goes off as its key goes down; the right button puts away what is aimed without casting it. The
 * primary is held to aim, and goes off as its button comes up. Each attack keeps its own cooldown,
 * so a quick click, Q, click, E, click casts all three. And what the renderer is told of an aimed
 * dash, blink or throw is where the cast then puts us or the trap.
 */
class GameClientAimTest {
  private val t = new TestClient()
  private val c = t.client

  private def charges(d: CharacterDef): Boolean = ProjectileDef.get(d.primaryProjectileType).chargeSpeedScaling.isDefined
  private def aimed(a: AbilityDef): Boolean = !Attacks.goesOffOnPress(a)

  /** Play the first character with an ability of this kind and a primary that doesn't charge (so a
    * click that fired the primary as well would show); returns the ability's slot (0 Q, 1 E). */
  private def play(is: AbilityDef => Boolean): Int = {
    val (who, slot) = CharacterDef.all.iterator.filterNot(charges).flatMap { d =>
      if (is(d.qAbility)) Some((d.id.id, 0)) else if (is(d.eAbility)) Some((d.id.id, 1)) else None
    }.next()
    c.selectedCharacterId = who
    t.startMatch(spawn = (30, 30))
    c.setMouseWorldPosition(38.0, 30.0)
    t.clearSent()
    slot
  }

  /** Play the first character for whom this holds. */
  private def playAs(is: CharacterDef => Boolean): CharacterDef = {
    val d = CharacterDef.all.find(is).get
    c.selectedCharacterId = d.id.id
    t.startMatch(spawn = (30, 30))
    c.setMouseWorldPosition(38.0, 30.0)
    t.clearSent()
    d
  }

  private val shot: AbilityDef => Boolean = _.castBehavior == StandardProjectile
  private def attackSlot(slot: Int): Int = if (slot == 0) AttackSlot.Q else AttackSlot.E
  private def cooldownOf(slot: Int): Float = if (slot == 0) c.getQCooldownFraction else c.getECooldownFraction
  private def click(): Unit = { c.pressPrimary(); c.releasePrimary() }
  private def ms(n: Long): Unit = t.nanos += n * 1000000L

  // --- Q and E: aimed, then cast by a click ---

  @Test def anAbilityIsAimedThenCastByAClick(): Unit = {
    val slot = play(shot)
    c.pressAbility(slot)
    assertTrue("nothing goes as the key goes down", t.sent.isEmpty)
    assertEquals(0f, cooldownOf(slot), 0f)
    assertEquals(slot, c.aimedAbility)
    assertEquals("it is what the renderer aims", attackSlot(slot), c.aimingSlot)
    c.pressPrimary()
    assertEquals("it goes as the button goes down", 1, t.sentSpawns.size)
    assertEquals(attackSlot(slot), t.sentSpawns.head.getAttackSlot)
    assertTrue(cooldownOf(slot) > 0f)
    assertEquals("and nothing is aimed now", -1, c.aimedAbility)
    assertEquals(-1, c.aimingSlot)
    c.releasePrimary()
    assertEquals("the click was the cast: the primary doesn't go as it comes up", 1, t.sentSpawns.size)
  }

  @Test def itGoesWhereTheAimIsAtTheClick(): Unit = {
    val slot = play(shot)
    c.setMouseWorldPosition(38.0, 30.0) // east
    c.pressAbility(slot)
    c.setMouseWorldPosition(30.0, 20.0) // turned north while it was aimed
    c.pressPrimary()
    val s = t.sentSpawns.head
    assertEquals(0f, s.getDx, 1e-4f)
    assertEquals(-1f, s.getDy, 1e-4f)
  }

  @Test def weWalkWhileItIsAimed(): Unit = {
    val slot = play(shot)
    c.pressAbility(slot)
    c.movePlayer(1, 0)
    c.movePlayer(0, 1)
    assertEquals((31, 31), t.at)
    assertEquals(slot, c.aimedAbility)
    c.pressPrimary()
    assertEquals("cast from where we walked to", (31f, 31f), (t.sentSpawns.head.getX, t.sentSpawns.head.getY))
  }

  @Test def onItsCooldownTheKeyTakesNothingUp(): Unit = {
    val slot = play(shot)
    c.pressAbility(slot); click()
    t.clearSent()
    c.pressAbility(slot)
    assertEquals(-1, c.aimedAbility)
    assertEquals(-1, c.aimingSlot)
    click()
    assertEquals("so a click is the primary's", Seq(AttackSlot.PRIMARY), t.sentSpawns.map(_.getAttackSlot))
  }

  @Test def theOtherKeyTakesOver(): Unit = {
    playAs(d => aimed(d.qAbility) && aimed(d.eAbility) && !charges(d))
    c.pressAbility(0)
    ms(5)
    c.pressAbility(1)
    assertEquals(1, c.aimedAbility)
    assertEquals(AttackSlot.E, c.aimingSlot)
    c.pressPrimary()
    assertTrue("E is cast", c.getECooldownFraction > 0f)
    assertEquals("and Q was put away, not cast", 0f, c.getQCooldownFraction, 0f)
    assertEquals(-1, c.aimedAbility)
  }

  @Test def theSameKeyAgainLeavesItAimed(): Unit = {
    val slot = play(shot)
    c.pressAbility(slot)
    ms(50)
    c.pressAbility(slot)
    assertEquals(slot, c.aimedAbility)
    assertEquals("aimed since the first press", 50L, c.aimedForMs(attackSlot(slot)))
    assertTrue(t.sent.isEmpty)
  }

  @Test def aClickWhileItCouldntGoOffCastsNothingAndKeepsItAimed(): Unit = {
    // A free-for-all's opening holds every attack: aimed all the same, to see where it would go
    val slot = play(shot)
    t.opening(30000, MatchOpening.NO_ATTACKS)
    t.clearSent()
    c.pressAbility(slot)
    assertEquals(slot, c.aimedAbility)
    assertFalse(c.abilityReady(slot))
    click()
    assertTrue("nothing cast", t.sentSpawns.isEmpty)
    assertEquals("nothing spent", 0f, cooldownOf(slot), 0f)
    assertEquals("and still aimed", slot, c.aimedAbility)
    t.opening(0)
    c.pressPrimary()
    assertEquals(Seq(attackSlot(slot)), t.sentSpawns.map(_.getAttackSlot))
  }

  @Test def rootedADashWaitsForTheRootToEnd(): Unit = {
    val slot = play(_.castBehavior.isInstanceOf[DashBuff])
    t.update(t.id, 30, 30, flags = 0x40) // rooted
    c.pressAbility(slot)
    click()
    assertFalse("a root lets us go nowhere", c.isSwooping)
    assertEquals(slot, c.aimedAbility)
    t.update(t.id, 30, 30)
    c.pressPrimary()
    assertTrue(c.isSwooping)
    assertEquals(-1, c.aimedAbility)
  }

  @Test def aTrapWithNowhereToLandStaysAimed(): Unit = {
    // Nowhere at all: standing inside a wall, aiming at our own feet
    val slot = play(_.castBehavior.isInstanceOf[TrapCast])
    c.pressAbility(slot)
    c.getWorld.setTile(30, 30, Tile.Wall)
    c.setMouseWorldPosition(30.0, 30.0)
    c.pressPrimary()
    assertTrue(t.sentTraps.isEmpty)
    assertEquals("nothing spent", 0f, cooldownOf(slot), 0f)
    assertEquals(slot, c.aimedAbility)
    c.getWorld.setTile(30, 30, Tile.Grass)
    c.setMouseWorldPosition(32.0, 30.0)
    c.pressPrimary()
    assertEquals((32, 30), (t.sentTraps.last.getX, t.sentTraps.last.getY))
  }

  // --- What goes off as its key goes down ---

  @Test def everySlamGoesOffAsItsKeyGoesDown(): Unit = {
    // Earthshaker's Seismic Root, whose ring roots whoever is in it, as much as the Cyborg's
    // Overclock, which only speeds us up: nothing to aim, wherever we aim
    var seen = Set.empty[String]
    for (d <- CharacterDef.all; (a, slot) <- Seq((d.qAbility, 0), (d.eAbility, 1)) if a.castBehavior.isInstanceOf[GroundSlam]) {
      c.selectedCharacterId = d.id.id
      t.startMatch(spawn = (30, 30))
      t.clearSent()
      c.pressAbility(slot)
      assertEquals(s"${d.displayName}'s ${a.name}", Seq(attackSlot(slot)), t.sentSpawns.map(_.getAttackSlot))
      assertEquals(-1, c.aimedAbility)
      seen += a.name
    }
    assertTrue(seen.contains(CharacterDef.get(CharacterId.Earthshaker).qAbility.name))
  }

  @Test def thePhaseGoesOffAsItsKeyGoesDown(): Unit = {
    val slot = play(_.castBehavior.isInstanceOf[PhaseShiftBuff])
    c.pressAbility(slot)
    assertTrue(c.isPhased)
    assertEquals("nothing is aimed", -1, c.aimedAbility)
  }

  @Test def theBarrierGoesUpAsItsKeyGoesDown(): Unit = {
    val slot = play(_.castBehavior.isInstanceOf[BarrierCast])
    c.pressAbility(slot)
    assertTrue(c.hasBarrier)
    assertEquals(-1, c.aimedAbility)
  }

  @Test def aFanAllTheWayRoundGoesOffAsItsKeyGoesDown(): Unit = {
    // None in the roster now, but a ring of shots round us has nothing to aim, as a slam hasn't
    val ring = AbilityDef("Ring", "", 8000, 8, 10, ProjectileType.ARROW, "Q", FanProjectile(8, 2 * Math.PI))
    assertTrue(Attacks.goesOffOnPress(ring))
    assertFalse(Attacks.goesOffOnPress(ring.copy(castBehavior = FanProjectile(3, Math.toRadians(30)))))
  }

  @Test def aSlamLeavesTheOtherAimed(): Unit = {
    def slam(a: AbilityDef) = a.castBehavior.isInstanceOf[GroundSlam]
    val d = playAs(d => (slam(d.qAbility) && aimed(d.eAbility)) || (slam(d.eAbility) && aimed(d.qAbility)))
    val (aimedSlot, atOnce) = if (slam(d.eAbility)) (0, 1) else (1, 0)
    c.pressAbility(aimedSlot)
    c.pressAbility(atOnce)
    assertTrue("the slam went off", cooldownOf(atOnce) > 0f)
    assertEquals("and the other is still aimed", aimedSlot, c.aimedAbility)
    c.pressPrimary()
    assertTrue(cooldownOf(aimedSlot) > 0f)
  }

  // --- Putting it away ---

  @Test def theRightButtonPutsItAwayWithoutCasting(): Unit = {
    val slot = play(shot)
    c.pressAbility(slot)
    c.cancelAims()
    assertEquals(-1, c.aimedAbility)
    assertEquals(-1, c.aimingSlot)
    click()
    assertEquals("the click is the primary's again", Seq(AttackSlot.PRIMARY), t.sentSpawns.map(_.getAttackSlot))
    assertEquals("and the ability's cooldown isn't spent", 0f, cooldownOf(slot), 0f)
  }

  @Test def aNewMatchPutsAwayWhatWasAimed(): Unit = {
    val slot = play(shot)
    c.pressAbility(slot)
    t.startMatch(spawn = (30, 30))
    t.clearSent()
    assertEquals(-1, c.aimedAbility)
    click()
    assertEquals(Seq(AttackSlot.PRIMARY), t.sentSpawns.map(_.getAttackSlot))
  }

  @Test def itIsTimedOnTheClientsClock(): Unit = {
    val slot = play(shot)
    c.pressAbility(slot)
    ms(250)
    assertEquals(250L, c.aimedForMs(attackSlot(slot)))
    assertEquals(-1L, c.aimedForMs(AttackSlot.PRIMARY))
  }

  // --- Click, Q, click, E, click ---

  /** Someone whose primary doesn't charge and whose Q and E both fire projectiles toward the aim. */
  private def bothShoot(d: CharacterDef): Boolean = !charges(d) && Seq(d.qAbility, d.eAbility).forall { a =>
    a.castBehavior == StandardProjectile || (a.castBehavior.isInstanceOf[FanProjectile] && aimed(a))
  }

  @Test def clickQClickEClickCastsAllThree(): Unit = {
    playAs(bothShoot)
    click()
    ms(40); c.pressAbility(0)
    ms(40); c.pressPrimary(); ms(30); c.releasePrimary()
    ms(40); c.pressAbility(1)
    ms(40); c.pressPrimary(); ms(30); c.releasePrimary()
    assertEquals("in 220ms", Seq(AttackSlot.PRIMARY, AttackSlot.Q, AttackSlot.E),
      t.sentSpawns.map(_.getAttackSlot).distinct)
    // Each on its own cooldown: a fourth click is the primary's, inside its half second
    val n = t.sentSpawns.size
    ms(40); click()
    assertEquals(n, t.sentSpawns.size)
  }

  @Test def anAttackOnItsCooldownDropsOutOfTheCombo(): Unit = {
    playAs(bothShoot)
    c.pressAbility(0); click() // Q, now on its cooldown
    ms(1000)
    t.clearSent()
    click()
    ms(40); c.pressAbility(0)
    ms(40); click() // Q wasn't taken up: the primary's, inside its half second
    ms(40); c.pressAbility(1)
    ms(40); click()
    assertEquals(Seq(AttackSlot.PRIMARY, AttackSlot.E), t.sentSpawns.map(_.getAttackSlot).distinct)
  }

  // --- The primary ---

  /** Play the first character whose primary does (or doesn't) charge. */
  private def primary(charging: Boolean): Unit = {
    playAs(d => charges(d) == charging)
  }

  @Test def thePrimaryGoesOffAsTheButtonComesUp(): Unit = {
    primary(charging = false)
    c.pressPrimary()
    assertTrue(t.sent.isEmpty)
    assertEquals(AttackSlot.PRIMARY, c.aimingSlot)
    c.setMouseWorldPosition(30.0, 38.0) // turned south
    c.releasePrimary()
    assertEquals(1, t.sentSpawns.size)
    assertEquals(AttackSlot.PRIMARY, t.sentSpawns.head.getAttackSlot)
    assertEquals(1f, t.sentSpawns.head.getDy, 1e-4f)
  }

  @Test def thePrimaryWaitsOutItsCooldownFromTheLastShot(): Unit = {
    primary(charging = false)
    click()
    ms(300)
    c.pressPrimary()
    assertFalse(c.primaryReady)
    c.releasePrimary()
    assertEquals("300ms after the last, nothing", 1, t.sentSpawns.size)
    // Pressed inside the cooldown and let go after it, it goes: the wait is counted to the shot
    c.pressPrimary()
    ms(250)
    assertTrue(c.primaryReady)
    c.releasePrimary()
    assertEquals(2, t.sentSpawns.size)
  }

  @Test def aChargedPrimaryChargesWhileHeld(): Unit = {
    primary(charging = true)
    c.pressPrimary()
    assertTrue(c.isCharging)
    ms(2000)
    c.releasePrimary()
    assertFalse(c.isCharging)
    assertEquals(1, t.sentSpawns.size)
    assertEquals(50, t.sentSpawns.head.getChargeLevel.toInt)
  }

  @Test def theRightButtonDropsACharge(): Unit = {
    primary(charging = true)
    c.pressPrimary()
    c.cancelAims()
    assertFalse(c.isCharging)
    c.releasePrimary()
    assertTrue(t.sentSpawns.isEmpty)
  }

  @Test def aClickCastsWhatIsAimedRatherThanCharging(): Unit = {
    val d = playAs(d => charges(d) && (shot(d.qAbility) || shot(d.eAbility)))
    val slot = if (shot(d.qAbility)) 0 else 1
    c.pressAbility(slot)
    c.pressPrimary()
    assertFalse(c.isCharging)
    ms(2000)
    c.releasePrimary()
    assertEquals(Seq(attackSlot(slot)), t.sentSpawns.map(_.getAttackSlot))
  }

  @Test def aShotHeldAsTheKeyGoesDownGoesAsTheButtonComesUp(): Unit = {
    val slot = play(shot)
    c.pressPrimary()
    ms(5)
    c.pressAbility(slot)
    assertEquals("the last pressed is the one shown", attackSlot(slot), c.aimingSlot)
    c.releasePrimary()
    assertEquals(Seq(AttackSlot.PRIMARY), t.sentSpawns.map(_.getAttackSlot))
    assertEquals("and the ability is still aimed", slot, c.aimedAbility)
    c.pressPrimary()
    assertEquals(Seq(AttackSlot.PRIMARY, attackSlot(slot)), t.sentSpawns.map(_.getAttackSlot))
  }

  @Test def aChargeGoesOnUnderAnAbilityTakenUp(): Unit = {
    val d = playAs(d => charges(d) && (aimed(d.qAbility) || aimed(d.eAbility)))
    val slot = if (aimed(d.qAbility)) 0 else 1
    c.pressPrimary()
    ms(5)
    c.pressAbility(slot)
    assertTrue(c.isCharging)
    assertEquals(attackSlot(slot), c.aimingSlot)
    ms(2000)
    c.releasePrimary()
    assertEquals(Seq(AttackSlot.PRIMARY), t.sentSpawns.map(_.getAttackSlot))
    assertEquals(50, t.sentSpawns.head.getChargeLevel.toInt)
    assertEquals(slot, c.aimedAbility)
  }

  // --- The keys and the mouse themselves ---

  private def keyOf(slot: Int): Int = if (slot == 0) GLFW_KEY_Q else GLFW_KEY_E

  @Test def theKeyTakesItUpAndTheButtonCastsIt(): Unit = {
    val keys = new GLKeyboardHandler(c)
    val mouse = new GLMouseHandler(c, new GameCamera())
    val slot = play(shot)
    keys.invoke(0L, keyOf(slot), 0, GLFW_PRESS, 0)
    keys.invoke(0L, keyOf(slot), 0, GLFW_REPEAT, 0)
    keys.invoke(0L, keyOf(slot), 0, GLFW_RELEASE, 0)
    assertTrue("the key coming up casts nothing", t.sent.isEmpty)
    assertEquals(slot, c.aimedAbility)
    mouse.onCursorPos(0, 0)
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0)
    assertEquals(Seq(attackSlot(slot)), t.sentSpawns.map(_.getAttackSlot))
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0)
    assertEquals(1, t.sentSpawns.size)
  }

  @Test def theRightButtonPutsAway(): Unit = {
    val keys = new GLKeyboardHandler(c)
    val mouse = new GLMouseHandler(c, new GameCamera())
    val slot = play(shot)
    keys.invoke(0L, keyOf(slot), 0, GLFW_PRESS, 0)
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS, 0)
    assertEquals(-1, c.aimedAbility)
  }

  @Test def openingTheChatPutsAwayWhatIsAimed(): Unit = {
    val keys = new GLKeyboardHandler(c)
    val slot = play(shot)
    keys.invoke(0L, keyOf(slot), 0, GLFW_PRESS, 0)
    keys.invoke(0L, GLFW_KEY_T, 0, GLFW_PRESS, 0)
    assertEquals(-1, c.aimedAbility)
    keys.invoke(0L, GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0) // the chat closed again
    click()
    assertEquals(Seq(AttackSlot.PRIMARY), t.sentSpawns.map(_.getAttackSlot))
  }

  @Test def losingTheFocusPutsAwayWhatIsAimed(): Unit = {
    val keys = new GLKeyboardHandler(c)
    val slot = play(shot)
    c.pressAbility(slot)
    keys.clearAllKeys()
    assertEquals(-1, c.aimedAbility)
    c.pressPrimary()
    assertTrue("the click that brings the window back casts nothing", t.sentSpawns.forall(_.getAttackSlot == AttackSlot.PRIMARY))
  }

  // --- What is shown is what is cast ---

  /** A wall across the way east, three cells out: x = 33, y 20 to 40. */
  private def wallEast(): Unit = (20 to 40).foreach(y => t.world.setTile(33, y, Tile.Wall))

  @Test def aDashEndsWhereItsAimSaid(): Unit = {
    wallEast()
    val slot = play(_.castBehavior.isInstanceOf[DashBuff])
    val max = (if (slot == 0) c.getSelectedCharacterDef.qAbility else c.getSelectedCharacterDef.eAbility)
      .castBehavior.asInstanceOf[DashBuff].maxDistance
    for (aim <- Seq((36.4, 31.2), (30.0 + max + 5, 30.0), (31.0, 30.2), (25.5, 24.0))) {
      t.startMatch(spawn = (30, 30))
      c.setMouseWorldPosition(aim._1, aim._2)
      c.pressAbility(slot)
      val shown = c.dashLanding(max)
      c.pressPrimary()
      assertTrue(c.isSwooping)
      assertEquals(s"aimed at $aim", (shown.getX.toFloat, shown.getY.toFloat), (c.getSwoopTargetX, c.getSwoopTargetY))
    }
    // Over the wall, and no further than the cursor (nor than its range), on the farthest open cell
    t.startMatch(spawn = (30, 30))
    c.setMouseWorldPosition(36.0, 30.0)
    val far = 30 + Math.min(6, max)
    assertEquals(new Position(if (far == 33) 32 else far, 30), c.dashLanding(max))
  }

  @Test def aBlinkLandsWhereItsAimSaid(): Unit = {
    wallEast()
    val slot = play(_.castBehavior.isInstanceOf[TeleportCast])
    val max = (if (slot == 0) c.getSelectedCharacterDef.qAbility else c.getSelectedCharacterDef.eAbility)
      .castBehavior.asInstanceOf[TeleportCast].maxDistance
    for (aim <- Seq((40.0, 30.0), (30.0, 22.0), (31.0, 30.0), (24.0, 37.0))) {
      t.startMatch(spawn = (30, 30))
      c.setMouseWorldPosition(aim._1, aim._2)
      c.pressAbility(slot)
      val shown = c.blinkLanding(max)
      c.pressPrimary()
      assertEquals(s"aimed at $aim", (shown.getX, shown.getY), t.at)
    }
    // Its whole range however near the cursor is, stopped before the wall
    t.startMatch(spawn = (30, 30))
    c.setMouseWorldPosition(30.0, 29.4)
    assertEquals(new Position(30, 30 - max), c.blinkLanding(max))
    c.setMouseWorldPosition(40.0, 30.0)
    assertEquals(new Position(Math.min(32, 30 + max), 30), c.blinkLanding(max))
  }

  @Test def aTrapLandsWhereItsAimSaid(): Unit = {
    wallEast()
    val slot = play(_.castBehavior.isInstanceOf[TrapCast])
    val range = (if (slot == 0) c.getSelectedCharacterDef.qAbility else c.getSelectedCharacterDef.eAbility)
      .castBehavior.asInstanceOf[TrapCast].maxRange
    for (aim <- Seq((40.0, 30.0), (30.0, 34.5), (27.0, 27.0), (30.0, 30.0 - range - 6))) {
      t.startMatch(spawn = (30, 30))
      t.clearSent()
      c.setMouseWorldPosition(aim._1, aim._2)
      c.pressAbility(slot)
      val shown = c.trapLanding(range).get
      c.pressPrimary()
      val thrown = t.sentTraps.last
      assertEquals(s"aimed at $aim", (shown.getX, shown.getY), (thrown.getX, thrown.getY))
    }
  }
}

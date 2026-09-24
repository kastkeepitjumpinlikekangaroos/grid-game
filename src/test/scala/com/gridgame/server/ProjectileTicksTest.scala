package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * Every projectile packet says which of the server's projectile ticks it is from, and a client
 * flies its copy of the projectile by that (NetProjectile): a MOVE is where the projectile is at
 * the end of its tick, and a SPAWN's position holds until the tick after the one it carries, the
 * first to move it. Positions a whole number of ticks apart are that many steps apart.
 */
class ProjectileTicksTest {
  private val m = new TestMatch()
  private val shooter = m.join(CharacterId.Soldier, 10, 10)
  private val watcher = m.join(CharacterId.Wizard, 50, 50)

  private def about(id: Int, action: Byte): Seq[ProjectilePacket] =
    m.projectileEvents(m.udpSent(watcher), action).filter(_.getProjectileId == id)

  @Test def aMoveCarriesTheTickThatMovedIt(): Unit = {
    m.tick(5) // a match under way
    m.clearSent()
    val shot = m.launch(shooter, ProjectileType.BULLET, 1f, 0f)
    m.tick(3)
    val sent = m.udpSent(watcher)
    val spawn = m.projectileEvents(sent, ProjectileAction.SPAWN).filter(_.getProjectileId == shot.id)
    val moves = m.projectileEvents(sent, ProjectileAction.MOVE).filter(_.getProjectileId == shot.id)
    assertEquals("fired after tick 5", Seq(5), spawn.map(_.getTick))
    assertEquals("and moved by each tick after", Seq(6, 7, 8), moves.map(_.getTick))
    // A bullet goes a cell a tick: each position is a step on from the one a tick before
    for ((mv, i) <- moves.zipWithIndex) assertEquals(spawn.head.getX + (i + 1), mv.getX, 1e-4f)
  }

  @Test def ticksCountOnWhenNothingIsFlying(): Unit = {
    m.tick(4)
    m.clearSent()
    val shot = m.launch(shooter, ProjectileType.BULLET, 0f, 1f)
    m.tick()
    assertEquals(Seq(5), about(shot.id, ProjectileAction.MOVE).map(_.getTick))
  }

  @Test def itsEndCarriesTheTickItEndedIn(): Unit = {
    m.tick(2)
    m.clearSent()
    val shot = m.launch(shooter, ProjectileType.BULLET, 1f, 0f)
    m.tickUntilGone(shot)
    val sent = m.udpSent(watcher)
    val moves = m.projectileEvents(sent, ProjectileAction.MOVE).filter(_.getProjectileId == shot.id)
    val end = m.projectileEvents(sent, ProjectileAction.DESPAWN).filter(_.getProjectileId == shot.id)
    // Its 18 cells: seventeen moves, and the end of its range in the eighteenth tick
    assertEquals((3 to 19).toSeq, moves.map(_.getTick))
    assertEquals(Seq(20), end.map(_.getTick))
    assertEquals("where its range ran out", 11f + 18f, end.head.getX, 1e-4f)
  }

  @Test def eachMatchCountsFromTheStart(): Unit = {
    m.tick(40)
    val next = new TestMatch()
    val p = next.join(CharacterId.Soldier, 10, 10)
    val w = next.join(CharacterId.Wizard, 50, 50)
    val shot = next.launch(p, ProjectileType.BULLET, 1f, 0f)
    next.tick()
    val sent = next.udpSent(w)
    assertEquals(Seq(0), next.projectileEvents(sent, ProjectileAction.SPAWN).filter(_.getProjectileId == shot.id).map(_.getTick))
    assertEquals(Seq(1), next.projectileEvents(sent, ProjectileAction.MOVE).filter(_.getProjectileId == shot.id).map(_.getTick))
  }
}

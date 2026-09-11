package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

import java.util.UUID

class TeamAssignmentTest {

  private val host = UUID.fromString("11111111-1111-1111-1111-111111111111")
  private val guest = UUID.fromString("22222222-2222-2222-2222-222222222222")
  private val bot1 = new UUID(0L, 7L)
  private val bot2 = new UUID(0L, 9L)

  @Test
  def recognisesBotIds(): Unit = {
    assertTrue(TeamAssignment.isBot(bot1))
    assertFalse(TeamAssignment.isBot(host))
    assertFalse(TeamAssignment.isBot(new UUID(0L, 0L)))
  }

  @Test
  def dealsHumansBeforeBotsRoundRobin(): Unit = {
    // The host added a bot before the guest joined. Teams still go humans first, so the
    // guest is on team 2 whatever order the lobby filled in.
    val teams = TeamAssignment.assign(Seq(host, guest), Seq(bot1, bot2)).toMap
    assertEquals(1.toByte, teams(host))
    assertEquals(2.toByte, teams(guest))
    assertEquals(1.toByte, teams(bot1))
    assertEquals(2.toByte, teams(bot2))
  }
}

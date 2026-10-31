package com.gridgame.server

import com.gridgame.server.game.{GameInstance, MatchJournal, MatchJournals, PacketValidator}
import com.gridgame.server.lobby.LobbyStatus
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * What the server writes down of a match for a problem report about it (MatchJournal): the start
 * and who played what, every request it refused with why and the numbers that explain it, the
 * corrections, deaths and respawns, joins and leaves; kept within its bounds however a client
 * floods it; kept a while after the match (MatchJournals) for the report that comes from its
 * scoreboard. And the match's own id, which a client names a report by, sent as the match begins
 * and to a player rejoining it (GameEvent.MATCH_INFO).
 */
class MatchJournalTest {
  import ServerTestKit.server

  private val m = new TestMatch()
  private def journal: Vector[String] = m.instance.journal.lines
  private def linesAbout(p: Player): Vector[String] = journal.filter(_.contains(p.getId.toString.substring(0, 8)))

  private def assertWritten(what: String, lines: Vector[String], parts: String*): Unit =
    assertTrue(s"$what: ${lines.mkString("\n")}", lines.exists(l => parts.forall(l.contains)))

  // --- Refusals, with why ---

  @Test def aRefusedStepIsWrittenWithWhyAndTheNumbersThatSaySo(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    // A first update has the network's tolerance of credit, a few cells short of twenty for him
    assertFalse("forty cells at once is no step", m.move(p, 50, 10))
    assertWritten("the step", linesAbout(p), "step refused (movement_speed)", "(10,10) -> (50,10)", "40 cells", "at most")
    m.world.setTile(11, 10, Tile.Wall)
    assertFalse(m.move(p, 11, 10))
    assertWritten("the wall", linesAbout(p), "step refused (movement_walkable)", "(10,10) -> (11,10)")
    assertFalse(m.move(p, 60, 10))
    assertWritten("off the map", linesAbout(p), "step refused (movement_bounds)", "(60,10) is off the 60x60 map")
  }

  @Test def aRefusedShotIsWrittenWithWhy(): Unit = {
    val p = m.join(CharacterId.Soldier, 10, 10)
    val primary = CharacterDef.get(p.getCharacterId).primaryProjectileType
    // The grenade's 10s, less the 2s a cast may come early by. (The primary's 500ms has all of its
    // cooldown to come early by: PacketValidator.castToleranceMs)
    val grenade = CharacterDef.get(p.getCharacterId).qAbility.projectileType
    assertEquals(1, m.fire(p, AttackSlot.Q, grenade, Seq((1f, 0f))).size)
    // Past the moment a cast's shots all go out in, and well within its cooldown
    Thread.sleep(PacketValidator.castWindowMs(10000) + 100)
    assertEquals("the next press is too soon", 0, m.fire(p, AttackSlot.Q, grenade, Seq((1f, 0f))).size)
    assertWritten("too soon", linesAbout(p), "shot refused (projectile_fire_rate)", "Q cast again", "before 8000ms")

    // The primary's projectile, said to come from E
    m.fire(p, AttackSlot.E, primary, Seq((1f, 0f)))
    assertWritten("another attack's projectile", linesAbout(p), "shot refused (projectile_fire_rate)", "E fires",
      s"not ${MatchJournal.projectileName(primary)}")

    p.setFrozenUntil(System.currentTimeMillis() + 5000)
    m.fire(p, AttackSlot.Q, CharacterDef.get(p.getCharacterId).qAbility.projectileType, Seq((1f, 0f)))
    assertWritten("held", linesAbout(p), "shot refused", "Q fired while held")
  }

  @Test def aShotInTheCeasefireIsWrittenAsSo(): Unit = {
    val opening = new TestMatch(opening = true)
    val p = opening.join(CharacterId.Soldier, 10, 10)
    opening.fire(p, AttackSlot.PRIMARY, CharacterDef.get(p.getCharacterId).primaryProjectileType, Seq((1f, 0f)))
    assertWritten("the ceasefire", opening.instance.journal.lines, "shot refused (match_opening)", "in the opening's ceasefire")
  }

  @Test def aRefusedRIsWrittenWithHowLongItHasLeft(): Unit = {
    val p = m.join(CharacterId.Soldier, 10, 10)
    m.instance.handler.processPacket(PacketSerializer.deserialize(
      new CooldownResetPacket(1, p.getId, CooldownResetAction.REQUEST, 0).serialize()), null, null)
    assertWritten("R", linesAbout(p), "R refused (cooldown_reset): too soon", "ms of its minute left")
  }

  @Test def aCorrectionIsWritten(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    // Refusals that go on for a quarter of a second get the client told where it is
    // (into a wall: a jump too far is taken once the credit the time since has built up covers it)
    m.world.setTile(11, 10, Tile.Wall)
    for (_ <- 0 until 4) { m.move(p, 11, 10, gapMs = 100) }
    assertWritten("the correction", linesAbout(p), "corrected: told it stands at (10,10)", "its steps refused for")
  }

  // --- What happens in it ---

  @Test def deathsRespawnsJoinsAndLeavesAreWritten(): Unit = {
    val shooter = m.join(CharacterId.Soldier, 5, 30)
    val victim = m.join(CharacterId.Gladiator, 8, 30)
    victim.setHealth(1)
    m.tickUntilGone(m.launch(shooter, ProjectileType.BULLET, 1f, 0f))
    assertTrue("the shot killed", victim.isDead)
    assertWritten("the death", linesAbout(victim), s"killed by ${shooter.getName} (${shooter.getId.toString.substring(0, 8)}) with",
      MatchJournal.projectileName(ProjectileType.BULLET))
    m.instance.respawn(victim.getId)
    assertWritten("the respawn", linesAbout(victim), "respawned at")
    m.rejoin(victim)
    assertWritten("the rejoin", linesAbout(victim), "rejoined")
    m.instance.handler.removePlayer(victim.getId)
    assertWritten("the leave", linesAbout(victim), "left the match")
  }

  @Test def itsFirstLinesAreTheMatchAndEveryoneInIt(): Unit = {
    val gi = new GameInstance(ServerTestKit.freshGameId(), "", 5, server)
    gi.world = WorldData.createEmpty(40, 40)
    gi.gameMode = 1
    val a = new Player(UUID.randomUUID(), "anna", new Position(3, 3), 0)
    a.setCharacterId(CharacterId.Wizard.id)
    a.setTeamId(1)
    val bot = new Player(new UUID(0L, 7L), "Bot Seven", new Position(30, 30), 0)
    bot.setCharacterId(CharacterId.Gladiator.id)
    bot.setTeamId(2)
    gi.registry.add(a)
    gi.registry.add(bot)
    gi.begin()
    val lines = gi.journal.lines
    assertTrue(lines.head, lines.head.contains(s"match ${gi.matchId} began") && lines.head.contains("Teams") &&
      lines.head.contains("5 min") && lines.head.contains("opening behind the wall"))
    assertWritten("a player", lines, "anna", s"plays ${CharacterDef.get(CharacterId.Wizard).displayName}, team 1, from (3,3)")
    assertWritten("a bot", lines, "Bot Seven", "team 2, a bot")
    assertTrue("every line is timed from the start", lines.forall(_.matches("""\d\d:\d\d\.\d\d\d  .*""")))
  }

  // --- Its bounds ---

  @Test def itKeepsItsStartAndItsLastLinesHoweverLongTheMatch(): Unit = {
    val j = new MatchJournal(UUID.randomUUID())
    val n = MatchJournal.HeadLines + MatchJournal.TailLines + 5000
    for (i <- 0 until n) j.note(s"line $i")
    val lines = j.lines
    assertEquals(MatchJournal.HeadLines + MatchJournal.TailLines + 1, lines.size)
    assertTrue(lines.head, lines.head.endsWith("line 0"))
    assertTrue(lines(MatchJournal.HeadLines - 1).endsWith(s"line ${MatchJournal.HeadLines - 1}"))
    assertTrue(lines(MatchJournal.HeadLines), lines(MatchJournal.HeadLines).contains("5000 lines dropped"))
    assertTrue(lines.last, lines.last.endsWith(s"line ${n - 1}"))
    assertTrue(lines(MatchJournal.HeadLines + 1).endsWith(s"line ${MatchJournal.HeadLines + 5000}"))
  }

  @Test def aFloodOfRefusalsIsCountedNotWritten(): Unit = {
    var t = 0L
    val j = new MatchJournal(UUID.randomUUID(), nanoClock = () => t)
    val id = UUID.randomUUID()
    var built = 0
    for (_ <- 0 until 500) j.refused(id, "flood", { built += 1; "step refused" })
    assertEquals("only the ones written are made", MatchJournal.RefusalsPerSecond, built)
    t = 1500000000L
    j.refused(id, "flood", "a refusal the next second")
    val lines = j.lines
    assertEquals(MatchJournal.RefusalsPerSecond, lines.count(_.endsWith("step refused")))
    assertWritten("the rest counted", lines, s"and ${500 - MatchJournal.RefusalsPerSecond} more refusals that second")
    assertWritten("the next second's written", lines, "a refusal the next second")
    // A second flood with no refusal after it is counted when the journal is read
    for (_ <- 0 until 30) j.refused(id, "flood", "more")
    assertWritten("counted at the end", j.lines, s"and ${30 - MatchJournal.RefusalsPerSecond + 1} more refusals that second")
  }

  @Test def aJournalOfAValidatorOnItsOwnKeepsNothing(): Unit = {
    val off = MatchJournal.Off
    off.note("nothing")
    off.refused(UUID.randomUUID(), "x", throw new AssertionError("never made"))
    assertTrue(off.lines.isEmpty)
  }

  // --- Kept after the match ---

  @Test def aFinishedMatchsJournalIsKeptForTheReportsAboutIt(): Unit = {
    val gi = new GameInstance(ServerTestKit.freshGameId(), "", 5, server)
    server.registerGameInstance(gi.gameId, gi)
    assertEquals("a running match's is its own", Some(gi.journal), server.journalOf(gi.matchId))
    server.unregisterGameInstance(gi.gameId)
    assertEquals("and kept when it ends", Some(gi.journal), server.journalOf(gi.matchId))
    assertEquals(None, server.journalOf(UUID.randomUUID()))
  }

  @Test def finishedJournalsGoAfterHalfAnHourOrWhenThereAreTooMany(): Unit = {
    val kept = new MatchJournals(keepMs = 1000, maxKept = 3)
    val js = (0 until 4).map(_ => new MatchJournal(UUID.randomUUID()))
    js.zipWithIndex.foreach { case (j, i) => kept.keep(j, now = 100L + i) }
    assertEquals("the oldest of too many goes", None, kept.get(js(0).matchId, now = 104L))
    assertEquals(Some(js(3)), kept.get(js(3).matchId, now = 104L))
    assertEquals("all of them, their time up", None, kept.get(js(3).matchId, now = 104L + 1001L))
    assertEquals(0, kept.size)
  }

  // --- Which match it is ---

  @Test def aRejoiningPlayerIsToldWhichMatchItIsFirst(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    m.clearSent()
    m.rejoin(p)
    val sent = m.tcpSent(p)
    val info = sent.collect { case e: GameEventPacket if e.getEventType == GameEvent.MATCH_INFO => e }
    assertEquals(1, info.size)
    assertEquals(m.instance.matchId, info.head.getTargetId)
    assertEquals(m.instance.gameId, info.head.getGameId)
    assertEquals("before anything else of the match", info.head, sent.head)
  }

  @Test def everyoneIsToldWhichMatchItIsAsItBegins(): Unit = {
    // As LobbyFlowTest drives a lobby: through GameServer, as a client's packets come
    var seq = 0
    def client(name: String): (UUID, EmbeddedChannel) = {
      val id = UUID.randomUUID()
      val ch = new EmbeddedChannel()
      server.sessions.channelToPlayer.put(ch, id)
      seq += 1
      server.handleIncomingPacket(new PlayerJoinPacket(seq, id, new Position(1, 1), 0xFF3366CC, name), ch, null)
      (id, ch)
    }
    def lobbyAction(id: UUID, ch: EmbeddedChannel, action: Byte, lobbyId: Short = 0): Unit = {
      seq += 1
      server.handleIncomingPacket(new LobbyActionPacket(seq, id, Packet.getCurrentTimestamp, action, lobbyId, 0.toByte, 5.toByte,
        0.toByte, 0.toByte, 0.toByte, "lobby", 0.toByte, 0.toByte, 2.toByte), ch, null)
    }
    def received(ch: EmbeddedChannel): Seq[Packet] =
      Iterator.continually(ch.readOutbound[ByteBuf]()).takeWhile(_ != null).map(ServerTestKit.decode).toSeq

    val (host, hostCh) = client("host")
    lobbyAction(host, hostCh, LobbyAction.CREATE)
    val lobby = server.lobbyManager.getPlayerLobby(host)
    val (guest, guestCh) = client("guest")
    lobbyAction(guest, guestCh, LobbyAction.JOIN, lobby.id)
    received(hostCh); received(guestCh)
    lobbyAction(host, hostCh, LobbyAction.START)
    val gi = lobby.gameInstance
    try {
      assertNotNull(gi)
      for (ch <- Seq(hostCh, guestCh)) {
        val sent = received(ch)
        val starting = sent.indexWhere { case l: LobbyActionPacket => l.getAction == LobbyAction.GAME_STARTING; case _ => false }
        val info = sent.indexWhere { case e: GameEventPacket => e.getEventType == GameEvent.MATCH_INFO; case _ => false }
        val world = sent.indexWhere(_.isInstanceOf[WorldInfoPacket])
        assertTrue(s"the start, then which match, then the world: $starting, $info, $world", starting >= 0 && starting < info && info < world)
        assertEquals(gi.matchId, sent(info).asInstanceOf[GameEventPacket].getTargetId)
      }
      assertNotEquals("every match its own", gi.matchId, new GameInstance(1, "", 5, server).matchId)
    } finally if (gi != null && lobby.status == LobbyStatus.IN_GAME) server.endGame(lobby.id)
  }
}

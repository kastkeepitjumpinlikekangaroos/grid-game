package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * The projectile definitions are registered by CharacterDef's initializer. Until something had
 * touched CharacterDef, every lookup came back as the default bolt: the projectile gallery drew
 * wall-passers with no lift, and a Projectile made then flew at the default's speed.
 *
 * This runs in a JVM of its own (its own Bazel target) and must be the first thing in it to look
 * anything up, which is why it is a single test: nothing may reach CharacterDef before it does.
 */
class ProjectileDefRegistryTest {
  @Test def definitionsAreThereForTheFirstLookup(): Unit = {
    val soulBolt = ProjectileDef.get(ProjectileType.SOUL_BOLT)
    assertEquals("Soul Bolt", soulBolt.name)
    assertTrue("a soul bolt flies through walls", soulBolt.passesThroughWalls)

    val arrow = new Projectile(1, UUID.randomUUID(), 0f, 0f, 1f, 0f, 0, 0, ProjectileType.ARROW)
    assertEquals("an arrow flies at the arrow's speed", ProjectileDef.get(ProjectileType.ARROW).speedMultiplier,
      arrow.speedMultiplier, 0f)
    assertEquals(0.95f, arrow.speedMultiplier, 0f)

    // Types with no definition of their own are still the default bolt
    assertEquals("Normal", ProjectileDef.get(ProjectileType.NORMAL).name)
  }
}

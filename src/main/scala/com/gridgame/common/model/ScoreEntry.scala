package com.gridgame.common.model

import java.util.UUID

class ScoreEntry(
    val playerId: UUID,
    val kills: Int,
    val deaths: Int,
    val rank: Int,
    val teamId: Int = 0
)

package com.gridgame.common.model

class LobbyInfo(
    val lobbyId: Short,
    var name: String,
    var mapIndex: Int,
    var durationMinutes: Int,
    var playerCount: Int,
    var maxPlayers: Int,
    var status: Int,
    var gameMode: Int = 0,
    var teamSize: Int = 2
)

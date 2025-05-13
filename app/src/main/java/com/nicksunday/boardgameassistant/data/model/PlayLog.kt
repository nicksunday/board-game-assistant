package com.nicksunday.boardgameassistant.data.model

data class PlayLog(
    val bggId: String = "",
    val date: String = "",
    val scores: List<PlayerScore>? = null, // Not needed in win-based games
    val winners: List<String> = listOf(),
    val playerIds: List<String> = listOf(),
    val timestamp: Long = System.currentTimeMillis(),
)

data class PlayerScore(
    val playerId: String = "",
    val score: Int? = null,
)
package com.nicksunday.boardgameassistant.api

import com.nicksunday.boardgameassistant.data.model.Game

data class BoardGame(
    val id: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val playingTime: Int,
    val primaryName: String,
    val description: String,
    val imageUrl: String,
    val thumbnailUrl: String,
    val yearPublished: Int,
    val averageRating: Double? = null,
    val boardGameRank: Int? = null,
    val minAge: Int = 0,
    val mechanics: List<String> = listOf(),
    val categories: List<String> = listOf(),
    val designers: List<String> = listOf(),
    val publishers: List<String> = listOf()
) {
    fun toGame(): Game {
        return Game(
            bggId = id,
            minPlayers = minPlayers,
            maxPlayers = maxPlayers,
            playingTime = playingTime,
            name = primaryName,
            description = description,
            imageUrl = imageUrl,
            thumbnailUrl = thumbnailUrl,
            yearPublished = yearPublished,
            averageRating = averageRating,
            boardGameRank = boardGameRank,
            minAge = minAge,
            mechanics = mechanics,
            categories = categories,
            designers = designers,
            publishers = publishers
        )
    }
}
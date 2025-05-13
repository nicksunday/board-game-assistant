package com.nicksunday.boardgameassistant.data.model
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import java.io.Serializable

data class Game(
    val name: String = "",
    val bggId: String = "",
    val imageUrl: String = "",
    val thumbnailUrl: String = "",
    val description: String = "",
    val minPlayers: Int = 0,
    val maxPlayers: Int = 0,
    val playingTime: Int = 0,
    val yearPublished: Int = 0,
    val averageRating: Double? = null,
    val boardGameRank: Int? = null,
    val minAge: Int = 0,
    val mechanics: List<String> = listOf(),
    val categories: List<String> = listOf(),
    val designers: List<String> = listOf(),
    val publishers: List<String> = listOf(),

) : Serializable

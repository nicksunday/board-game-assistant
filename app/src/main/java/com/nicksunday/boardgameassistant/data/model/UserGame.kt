package com.nicksunday.boardgameassistant.data.model

import com.google.firebase.Timestamp
import java.io.Serializable

data class UserGame(
    val bggId: String = "",
    val dateAdded: Timestamp = Timestamp.now()
) : Serializable

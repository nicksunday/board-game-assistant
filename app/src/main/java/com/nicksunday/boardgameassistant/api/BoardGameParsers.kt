package com.nicksunday.boardgameassistant.api

import android.util.Log
import com.nicksunday.boardgameassistant.data.model.Game
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** Updated XML parsers using XmlPullParser for robust and correct parsing of board game data */

typealias XmlString = String

fun parseCollection(xml: XmlString): List<BoardGame> {
    val doc = Jsoup.parse(xml, "", Parser.xmlParser())
    val games = mutableListOf<BoardGame>()

    for (item in doc.select("item")) {
        val id = item.attr("objectid")

        val stats = item.selectFirst("stats")
        val rating = stats?.selectFirst("rating")

        val minPlayers = stats?.attr("minplayers")?.toIntOrNull() ?: 0
        val maxPlayers = stats?.attr("maxplayers")?.toIntOrNull() ?: 0
        val playingTime = stats?.attr("playingtime")?.toIntOrNull() ?: 0
        val averageRating = rating?.selectFirst("average")?.attr("value")?.toDoubleOrNull()

        val primaryName = item.selectFirst("name")?.text() ?: "Unknown"
        val yearPublished = item.selectFirst("yearpublished")?.text()?.toIntOrNull() ?: 0
        val imageUrl = item.selectFirst("image")?.text() ?: ""
        val thumbnailUrl = item.selectFirst("thumbnail")?.text() ?: ""

        games.add(
            BoardGame(
                id = id,
                primaryName = primaryName,
                yearPublished = yearPublished,
                minPlayers = minPlayers,
                maxPlayers = maxPlayers,
                playingTime = playingTime,
                imageUrl = imageUrl,
                thumbnailUrl = thumbnailUrl,
                description = "",
                averageRating = averageRating,
                boardGameRank = null
            )
        )
    }

    return games
}

fun parseSearchResults(xml: XmlString): List<BoardGame> {
    val doc = Jsoup.parse(xml, "", Parser.xmlParser())
    return doc.select("item").map { item ->
        val id = item.attr("id")
        val primaryName = item.selectFirst("name[type=primary]")?.attr("value") ?: "Unknown"
        val yearPublished = item.selectFirst("yearpublished")?.attr("value")?.toIntOrNull() ?: 0

        BoardGame(
            id = id,
            primaryName = primaryName,
            yearPublished = yearPublished,
            minPlayers = 0,
            maxPlayers = 0,
            playingTime = 0,
            description = "",
            imageUrl = "",
            thumbnailUrl = ""
        )
    }
}

fun parseBoardGameDetails(xml: String): BoardGame {
    return parseBoardGameDetailsList(xml).firstOrNull()
        ?: throw IllegalArgumentException("No board game found in details response")
}

fun parseBoardGameDetailsList(xml: String): List<BoardGame> {
    val factory = XmlPullParserFactory.newInstance()
    val parser = factory.newPullParser()
    parser.setInput(xml.reader())

    val games = mutableListOf<BoardGame>()
    var currentGame: MutableBoardGame? = null
    var eventType = parser.eventType

    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "item" -> {
                    val id = parser.getAttributeValue(null, "id")
                    if (id != null) {
                        val type = parser.getAttributeValue(null, "type")
                        if (type == "boardgame" || type == "boardgameexpansion") {
                            currentGame = MutableBoardGame(id)
                        }
                    }
                }
                "name" -> if (parser.getAttributeValue(null, "type") == "primary") {
                    currentGame?.primaryName = parser.getAttributeValue(null, "value") ?: "Unknown"
                }
                "description" -> currentGame?.description = parser.nextText()
                "image" -> currentGame?.imageUrl = parser.nextText()
                "thumbnail" -> currentGame?.thumbnailUrl = parser.nextText()
                "yearpublished" -> currentGame?.yearPublished = parser.getAttributeValue(null, "value")?.toIntOrNull() ?: 0
                "minplayers" -> currentGame?.minPlayers = parser.getAttributeValue(null, "value")?.toIntOrNull() ?: 0
                "maxplayers" -> currentGame?.maxPlayers = parser.getAttributeValue(null, "value")?.toIntOrNull() ?: 0
                "playingtime" -> currentGame?.playingTime = parser.getAttributeValue(null, "value")?.toIntOrNull() ?: 0
                "minage" -> currentGame?.minAge = parser.getAttributeValue(null, "value")?.toIntOrNull() ?: 0
                "average" -> currentGame?.averageRating = parser.getAttributeValue(null, "value")?.toDoubleOrNull()
                "rank" -> if (parser.getAttributeValue(null, "name") == "boardgame") {
                    currentGame?.boardGameRank = parser.getAttributeValue(null, "value")?.toIntOrNull()
                }
                "link" -> {
                    val type = parser.getAttributeValue(null, "type")
                    val value = parser.getAttributeValue(null, "value")
                    if (value != null) {
                        when (type) {
                            "boardgamemechanic" -> currentGame?.mechanics?.add(value)
                            "boardgamecategory" -> currentGame?.categories?.add(value)
                            "boardgamedesigner" -> currentGame?.designers?.add(value)
                            "boardgamepublisher" -> currentGame?.publishers?.add(value)
                        }
                    }
                }
            }
            XmlPullParser.END_TAG -> if (parser.name == "item") {
                currentGame?.let { games.add(it.toImmutable()) }
                currentGame = null
            }
        }
        eventType = parser.next()
    }

    Log.d("ImportDebug", "Parsed ${games.size} enriched games from batch.")
    return games
}

private class MutableBoardGame(val id: String) {
    var minPlayers: Int = 0
    var maxPlayers: Int = 0
    var playingTime: Int = 0
    var primaryName: String = "Unknown"
    var description: String = ""
    var imageUrl: String = ""
    var thumbnailUrl: String = ""
    var yearPublished: Int = 0
    var averageRating: Double? = null
    var boardGameRank: Int? = null
    var minAge: Int = 0
    val mechanics = mutableListOf<String>()
    val categories = mutableListOf<String>()
    val designers = mutableListOf<String>()
    val publishers = mutableListOf<String>()

    fun toImmutable(): BoardGame {
        return BoardGame(
            id = id,
            minPlayers = minPlayers,
            maxPlayers = maxPlayers,
            playingTime = playingTime,
            primaryName = primaryName,
            description = description,
            imageUrl = imageUrl,
            thumbnailUrl = thumbnailUrl,
            yearPublished = yearPublished,
            averageRating = averageRating,
            boardGameRank = boardGameRank,
            minAge = minAge,
            mechanics = mechanics.toList(),
            categories = categories.toList(),
            designers = designers.toList(),
            publishers = publishers.toList()
        )
    }
}

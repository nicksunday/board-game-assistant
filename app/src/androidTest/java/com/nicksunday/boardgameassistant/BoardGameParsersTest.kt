package com.nicksunday.boardgameassistant

import com.nicksunday.boardgameassistant.api.parseBoardGameDetailsList
import com.nicksunday.boardgameassistant.api.parseCollection
import com.nicksunday.boardgameassistant.api.parseSearchResults
import org.junit.Assert.*
import org.junit.Test

class BoardGameParsersTest {

    @Test
    fun parseCollection_parsesBasicFieldsCorrectly() {
        val xml = """
            <items>
              <item objectid="1234">
                <name>Test Game</name>
                <yearpublished>2020</yearpublished>
                <image>https://example.com/image.jpg</image>
                <thumbnail>https://example.com/thumb.jpg</thumbnail>
                <stats minplayers="2" maxplayers="5" playingtime="60">
                  <rating>
                    <average value="7.5" />
                  </rating>
                </stats>
              </item>
            </items>
        """

        val games = parseCollection(xml)
        assertEquals(1, games.size)
        val game = games[0]
        assertEquals("1234", game.id)
        assertEquals("Test Game", game.primaryName)
        assertEquals(2020, game.yearPublished)
        assertEquals(2, game.minPlayers)
        assertEquals(5, game.maxPlayers)
        assertEquals(60, game.playingTime)
        assertEquals(7.5, game.averageRating ?: error("averageRating was null"), 0.01)
    }

    @Test
    fun parseSearchResults_handlesMissingFields() {
        val xml = """
            <items>
              <item id="999">
                <name type="primary" value="Fallback Game"/>
              </item>
            </items>
        """

        val results = parseSearchResults(xml)
        assertEquals(1, results.size)
        assertEquals("Fallback Game", results[0].primaryName)
    }

    @Test
    fun parseBoardGameDetailsList_parsesMultipleItems() {
        val xml = """
            <items>
              <item type="boardgame" id="1">
                <name type="primary" value="One"/>
              </item>
              <item type="boardgame" id="2">
                <name type="primary" value="Two"/>
              </item>
            </items>
        """

        val results = parseBoardGameDetailsList(xml)
        assertEquals(2, results.size)
        assertEquals("1", results[0].id)
        assertEquals("2", results[1].id)
    }
}
package com.nicksunday.boardgameassistant

import com.nicksunday.boardgameassistant.api.BGGApi
import com.nicksunday.boardgameassistant.api.BoardGameRepository
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BoardGameRepositoryTest {

    private lateinit var api: BGGApi
    private lateinit var repo: BoardGameRepository

    @Before
    fun setup() {
        api = mock(BGGApi::class.java)
        repo = BoardGameRepository(api)
    }

    @Test
    fun getUserCollection_success() = runTest {
        val fakeXml = """
            <items>
                <item objectid="123">
                    <name>Game</name>
                    <yearpublished>2020</yearpublished>
                    <image></image>
                    <thumbnail></thumbnail>
                    <stats minplayers="2" maxplayers="4" playingtime="60">
                        <rating><average value="7.5"/></rating>
                    </stats>
                </item>
            </items>
        """.trimIndent()

        val body = fakeXml.toResponseBody("application/xml".toMediaTypeOrNull())
        `when`(api.getBoardGameLibrary("tester")).thenReturn(Response.success(body))

        val results = repo.getUserCollection("tester")
        assertEquals(1, results.size)
        assertEquals("123", results[0].id)
    }

    @Test
    fun getBoardGameDetails_success() = runTest {
        val fakeXml = """
            <items>
                <item id="999" type="boardgame">
                    <name type="primary" value="Cool Game"/>
                    <description>Fun stuff</description>
                    <minplayers value="2"/>
                    <maxplayers value="4"/>
                    <playingtime value="45"/>
                    <yearpublished value="2019"/>
                </item>
            </items>
        """.trimIndent()

        val body = fakeXml.toResponseBody("application/xml".toMediaTypeOrNull())
        `when`(api.getBoardGameDetails("999")).thenReturn(Response.success(body))

        val game = repo.getBoardGameDetails("999")
        assertEquals("Cool Game", game?.primaryName)
        assertEquals(45, game?.playingTime)
    }

    @Test
    fun getBoardGameDetailsBatch_success() = runTest {
        val fakeXml = """
            <items>
                <item id="1" type="boardgame">
                    <name type="primary" value="One"/>
                    <yearpublished value="2000"/>
                </item>
                <item id="2" type="boardgame">
                    <name type="primary" value="Two"/>
                    <yearpublished value="2001"/>
                </item>
            </items>
        """.trimIndent()

        val body = fakeXml.toResponseBody("application/xml".toMediaTypeOrNull())
        `when`(api.getBoardGameDetails("1,2", 1)).thenReturn(Response.success(body))

        val results = repo.getBoardGameDetailsBatch(listOf("1", "2"), batchSize = 20, throttleMillis = 0)
        assertEquals(2, results.size)
        assertEquals("One", results[0].primaryName)
        assertEquals("Two", results[1].primaryName)
    }
}

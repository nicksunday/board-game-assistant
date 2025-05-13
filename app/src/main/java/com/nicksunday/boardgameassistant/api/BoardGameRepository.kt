package com.nicksunday.boardgameassistant.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class BoardGameRepository(private val bggApi: BGGApi) {

    suspend fun getUserCollection(username: String): List<BoardGame> = withContext(Dispatchers.IO) {
        val maxRetries = 5
        val retryDelayMillis = 2000L

        repeat(maxRetries) { attempt ->
            val response = bggApi.getBoardGameLibrary(username)
            if (response.code() == 202) {
                delay(retryDelayMillis)
            } else if (response.isSuccessful) {
                val body = response.body()?.string()
                if (!body.isNullOrEmpty()) {
                    return@withContext parseCollection(body)
                }
            }
        }

        return@withContext emptyList()
    }

    suspend fun getBoardGameDetails(id: String): BoardGame? = withContext(Dispatchers.IO) {
        val response = bggApi.getBoardGameDetails(id)
        if (response.isSuccessful) {
            val body = response.body()?.string()
            if (!body.isNullOrEmpty()) {
                return@withContext parseBoardGameDetails(body)
            }
        }
        return@withContext null
    }

    suspend fun getBoardGameDetailsBatch(
        ids: List<String>,
        batchSize: Int = 20, // BGG API allows up to 20 IDs per request
        throttleMillis: Long = 5_000L
    ): List<BoardGame> = withContext(Dispatchers.IO) {
        val results = mutableListOf<BoardGame>()

        ids.chunked(batchSize).forEach { chunk ->
            // throttle so we don’t hit the BGG servers too hard
            delay(throttleMillis)

            // join into a single comma‑separated string
            val csv = chunk.joinToString(separator = ",")
            val resp = bggApi.getBoardGameDetails(csv, stats = 1)
            Log.d("ImportDebug", "Fetching enriched data for: $csv")

            if (resp.isSuccessful) {
                val xml = resp.body()?.string()
                Log.d("ImportDebug", "Response: ${resp.code()}, First few XML chars: ${xml?.take(200)}")

                if (!xml.isNullOrEmpty()) {
                    results += parseBoardGameDetailsList(xml)
                }
            } else {
                Log.w("BGGRepo", "Batch call failed [${resp.code()}]")
            }

        }
        Log.d("ImportDebug", "Parsed ${results.size} enriched games from batch.")
        return@withContext results
    }

    suspend fun searchBoardGames(query: String): List<BoardGame> = withContext(Dispatchers.IO) {
        val response = bggApi.search(query)
        if (response.isSuccessful) {
            val body = response.body()?.string()
            if (!body.isNullOrEmpty()) {
                return@withContext parseSearchResults(body)
            }
        }
        return@withContext emptyList()
    }
}

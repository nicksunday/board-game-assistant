package com.nicksunday.boardgameassistant.data.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.nicksunday.boardgameassistant.api.BoardGame
import com.nicksunday.boardgameassistant.api.BoardGameRepository
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import kotlin.math.ceil

data class ImportResult(val added: Int, val skipped: Int, val logLines: List<String>)

suspend fun importBoardGameLibrary(
    context: Context,
    boardGames: List<BoardGame>,
    boardGameRepo: BoardGameRepository,
    firestoreRepo: FirestoreRepository,
    onLineImported: suspend (String) -> Unit = {}
): ImportResult {
    var addedCount = 0
    var skippedCount = 0
    val logLines = mutableListOf<String>()

    val batchSize = 400
    boardGames.chunked(batchSize).forEachIndexed { batchIdx, chunk ->

        val detailedList = boardGameRepo
            .getBoardGameDetailsBatch(chunk.map { it.id })
        Log.d("ImportDebug", "Batch XML starts with:\n${detailedList.joinToString("\n")}")
        val detailedMap = detailedList.associateBy { it.id }

        for (game in chunk) {
            val enriched = detailedMap[game.id]
            val toWrite = (enriched ?: game).toGame()

            // Always overwrite — we cleared the DB beforehand
            firestoreRepo.addGame(toWrite)
            addedCount++

            val status = if (enriched != null) "✅" else "⚠️"
            val label = if (enriched != null) "imported" else "fallback"
            val line = "$status ${game.primaryName} $label"
            logLines += line
            onLineImported("$line\n")
        }

        val batchLine = "―― Batch ${batchIdx + 1} of ${ceil(boardGames.size / batchSize.toDouble()).toInt()} done ――\n"
        onLineImported(batchLine)
    }

    val summary = "✅ $addedCount imported"
    Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
    return ImportResult(addedCount, skippedCount, logLines)
}

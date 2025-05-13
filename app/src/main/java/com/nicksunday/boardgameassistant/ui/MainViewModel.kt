package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicksunday.boardgameassistant.api.BGGApi
import com.nicksunday.boardgameassistant.api.BoardGame
import com.nicksunday.boardgameassistant.api.BoardGameRepository
import com.nicksunday.boardgameassistant.data.model.Game
import com.nicksunday.boardgameassistant.data.model.Player
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import kotlinx.coroutines.launch

class MainViewModel(
    private val firestoreRepo: FirestoreRepository
) : ViewModel() {

    // 1) Raw list from Firestore
    private val _allLibraryGames = MutableLiveData<List<Game>>(emptyList())

    // 2) Filter text
    private val _searchQuery = MutableLiveData("")

    // 3) Public, filtered LiveData
    val libraryGames: LiveData<List<Game>> = MediatorLiveData<List<Game>>().apply {
        fun update() {
            val games = _allLibraryGames.value.orEmpty()
            val query = _searchQuery.value.orEmpty().trim()
            value = if (query.isBlank()) games
            else games.filter { it.name.contains(query, ignoreCase = true) }
        }
        addSource(_allLibraryGames) { update() }
        addSource(_searchQuery)    { update() }
    }

    init {
        // auto‐load on creation
        refreshLibrary()
    }

    /** Public: force a reload from Firestore */
    fun refreshLibrary() {
        viewModelScope.launch {
            try {
                val games = firestoreRepo
                    .getGames()                    // pulls full Game objects
                    .sortedBy { it.name.lowercase() }
                _allLibraryGames.value = games
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to load library", e)
            }
        }
    }

    /** Called by your Fragment when the search input changes */
    fun setLibrarySearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Games
    private val searchGames = MutableLiveData<List<BoardGame>>()

    fun observeSearchGames(): MutableLiveData<List<BoardGame>> {
        return searchGames
    }

    fun setSearchGames(games: List<BoardGame>) {
        searchGames.postValue(games)
    }

    fun searchBoardGames(query: String, repository: BoardGameRepository) {
        viewModelScope.launch {
            val results = repository.searchBoardGames(query)
            setSearchGames(results)
        }
    }

    suspend fun getBoardGameDetails(bggId: String): BoardGame? {
        return BoardGameRepository(BGGApi.create()).getBoardGameDetails(bggId)
    }

    // Players
    private val players = MutableLiveData<List<Player>>()

    fun observePlayers(): MutableLiveData<List<Player>> {
        return players
    }

    fun startListeningToPlayers(repo: FirestoreRepository) {
        repo.listenToPlayers {
            players.postValue(it)
        }
    }

    fun loadPlayers(repository: FirestoreRepository) {
        viewModelScope.launch {
            try {
                val playerList = repository.getPlayers()
                players.postValue(playerList)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading players", e)
                Toast.makeText(
                    null,
                    "Error loading players: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val playerWinCounts = MutableLiveData<Map<String, Int>>()

    fun observePlayerWinCounts(): LiveData<Map<String, Int>> {
        return playerWinCounts
    }

    fun loadPlayerWinCounts(repo: FirestoreRepository) {
        viewModelScope.launch {
            val plays = repo.getPlayLogs()
            val allPlayers = repo.getPlayers().associateBy { it.id }
            val myUid = repo.getUser().uid

            val winMap = mutableMapOf<String, Int>()

            for (play in plays) {
                // Only consider plays that include the current user
                if (!play.playerIds.contains(myUid)) continue

                for (winnerId in play.winners) {
                    // Try to match directly
                    val directMatch = allPlayers[winnerId]

                    // Or resolve by friend code
                    val resolvedMatch = allPlayers.values.find { player ->
                        repo.getUidByFriendCode(player.friendCode) == winnerId
                    }

                    val resolvedId = directMatch?.id ?: resolvedMatch?.id

                    if (resolvedId != null) {
                        winMap[resolvedId] = (winMap[resolvedId] ?: 0) + 1
                    }
                }
            }

            playerWinCounts.postValue(winMap)
        }
    }

    private val playerIdNameCache = mutableMapOf<String, String>()
    private val friendCodeToUidCache = mutableMapOf<String, String>()
    suspend fun resolvePlayerName(
        playerId: String,
        allPlayers: Map<String, Player>,
        repo: FirestoreRepository
    ): String {
        Log.d("ResolveName", "Trying to resolve: $playerId")

        // 1. Direct match
        allPlayers[playerId]?.let {
            playerIdNameCache[playerId] = it.name
            return it.name
        }
        Log.d("ResolveName", "Friend codes available: ${allPlayers.values.map { it.friendCode }}")

        // 2. Reverse lookup: friendCode → uid
        val friendMatch = allPlayers.values.find { player ->
            val resolvedUid = repo.getUidByFriendCode(player.friendCode)
            Log.d("ResolveName", "FriendCode=${player.friendCode}, resolves to UID=$resolvedUid")
            val friendCode = player.friendCode
            if (friendCode.isBlank()) return@find false

            val cachedUid = friendCodeToUidCache[friendCode]
                ?: repo.getUidByFriendCode(friendCode)?.also {
                    friendCodeToUidCache[friendCode] = it
                }

            cachedUid == playerId
        }

        if (friendMatch != null) {
            playerIdNameCache[playerId] = friendMatch.name
            return friendMatch.name
        }


        // return "Unknown Player"
        Log.w("ResolveName", "❗ Unknown playerId: $playerId")
        Log.d("ResolveName", "Available Player IDs: ${allPlayers.keys}")
        Log.d("ResolveName", "Friend code map: ${
            allPlayers.values.associate { it.friendCode to it.name }
        }")
        return "Unknown ($playerId)"
    }

    fun getCachedPlayersMap(): Map<String, Player> {
        return players.value?.associateBy { it.id } ?: emptyMap()
    }


    // Random game functions
    private var lastRandomGameFilterResult: List<Game> = emptyList()

    fun setLastFilteredRandomGames(games: List<Game>) {
        lastRandomGameFilterResult = games
    }

    fun getLastFilteredRandomGames(): List<Game> {
        return lastRandomGameFilterResult
    }


}
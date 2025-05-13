package com.nicksunday.boardgameassistant.data.repository
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nicksunday.boardgameassistant.data.model.Game
import com.nicksunday.boardgameassistant.data.model.PlayLog
import com.nicksunday.boardgameassistant.data.model.Player
import com.nicksunday.boardgameassistant.data.model.User
import com.nicksunday.boardgameassistant.data.model.UserGame
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val uid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User must be logged in")

    private fun userDoc() = db.collection("users").document(uid)
    private fun userLibraryCollection() = userDoc().collection("library")
    private fun globalGamesCollection() = db.collection("games")
    private fun playerCollection() = userDoc().collection("players")
    private fun friendCodesCollection() = db.collection("friendCodes")

    // --- User Functions ---
    suspend fun getUser(): User {
        return userDoc().get().await().toObject(User::class.java)!!
    }

    /** Update the “name” field on the users/{uid} document */
    suspend fun updateUserName(newName: String) {
        userDoc()
            .update("name", newName)
            .await()
    }

    fun generateFriendCode(): String {
        // Exclude I, L, O and digits 0,1
        val letters = ('A'..'Z').filterNot { it in listOf('I','L','O') }
        val digits  = ('2'..'9')
        val pool    = (letters + digits)

        val raw = (1..6)
            .map { pool.random() }
            .joinToString("")

        // Format as AAA‑PPP
        return "${raw.substring(0,3)}-${raw.substring(3,6)}"
    }

    suspend fun generateUniqueFriendCode(): String {
        var code: String
        var exists: Boolean

        do {
            code = generateFriendCode()
            exists = checkFriendCodeExists(code)
        } while (exists)

        return code
    }

    // 1) Check if a code exists
    suspend fun checkFriendCodeExists(code: String): Boolean {
        // former: a query on /users — now just a get by document
        return friendCodesCollection()
            .document(code)
            .get()
            .await()
            .exists()
    }

    // 2) Lookup uid by code
    suspend fun getUidByFriendCode(code: String): String? {
        val snap = friendCodesCollection()
            .document(code)
            .get()
            .await()
        return if (snap.exists()) snap.getString("uid") else null
    }

    // 3) On signup, write BOTH /users/{uid} AND /friendCodes/{code}
    suspend fun addUserIfNotExists(name: String) {
        val userRef = db.collection("users").document(uid)
        val snapshot = userRef.get().await()
        if (!snapshot.exists()) {
            val friendCode = generateUniqueFriendCode()

            // 3a) write the user document
            val newUser = User(uid = uid, name = name, friendCode = friendCode)
            userRef.set(newUser).await()

            // 3b) write the code→uid map
            friendCodesCollection()
                .document(friendCode)
                .set(mapOf("uid" to uid))
                .await()

            // 3c) create the “self” player entry
            val firstName = name.trim().split("\\s+".toRegex()).firstOrNull() ?: name
            val selfPlayer = Player(
                id = uid,
                name = firstName,
                friendCode = friendCode
            )
            addOrUpdatePlayer(selfPlayer)
        }
    }

    // --- Game Functions ---
    suspend fun addGame(game: Game) {
        // Write full game to global collection
        globalGamesCollection().document(game.bggId).set(game).await()

        // Add reference to user's personal library
        val userGame = UserGame(bggId = game.bggId, dateAdded = Timestamp.now())
        userLibraryCollection().document(game.bggId).set(userGame).await()
    }

    suspend fun removeGame(bggId: String) {
        userLibraryCollection().document(bggId).delete().await()
        // Don't delete global copy — other users may use it
    }

    suspend fun getGame(bggId: String): Game? =
        globalGamesCollection()
            .document(bggId)
            .get()
            .await()
            .toObject(Game::class.java)

    suspend fun getGames(): List<Game> {
        val userGames = userLibraryCollection().get().await().mapNotNull {
            it.getString("bggId")
        }

        if (userGames.isEmpty()) return emptyList()

        val results = mutableListOf<Game>()
        userGames.chunked(10).forEach { chunk ->
            val docs = globalGamesCollection()
                .whereIn("bggId", chunk)
                .get().await().documents
            results += docs.mapNotNull { it.toObject(Game::class.java) }
        }
        return results
    }

    suspend fun cacheGlobalGame(game: Game) {
        globalGamesCollection()
            .document(game.bggId)
            .set(game)
            .await()
    }

    suspend fun gameExists(bggId: String): Boolean {
        val doc = userLibraryCollection().document(bggId).get().await()
        return doc.exists()
    }

    // --- Player Functions ---
    private fun playerDoc(player: Player) = playerCollection().document(player.id)

    suspend fun addOrUpdatePlayer(player: Player) {
        playerDoc(player).set(player).await()
    }

    suspend fun getPlayers(): List<Player> {
        return playerCollection().get().await().map { doc ->
            val player = doc.toObject(Player::class.java)
            player.copy(id = doc.id)
        }
    }

    suspend fun removePlayer(player: Player) {
        playerDoc(player).delete().await()
    }

    fun listenToPlayers(onUpdate: (List<Player>) -> Unit) {
        playerCollection().addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener

            val players = snapshot.documents.mapNotNull { it.toObject(Player::class.java) }
            onUpdate(players)
        }
    }

    // --- PlayLog Functions ---
    suspend fun addPlayLog(playLog: PlayLog) {
        db.collection("plays").add(playLog).await()
    }

    suspend fun getPlayLogs(): List<PlayLog> {
        return db.collection("plays")
            .get().await()
            .mapNotNull { it.toObject(PlayLog::class.java) }
    }

    fun listenToPlaysForGame(gameId: String, onUpdate: (List<PlayLog>) -> Unit) {
        db.collection("plays")
            .whereEqualTo("bggId", gameId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val plays = snapshot.documents.mapNotNull { it.toObject(PlayLog::class.java) }
                onUpdate(plays)
            }
    }

    fun listenToMyPlays(onUpdate: (List<PlayLog>) -> Unit) {
        db.collection("plays")
            .whereArrayContains("playerIds", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val plays = snapshot.documents.mapNotNull { it.toObject(PlayLog::class.java) }
                onUpdate(plays)
            }
    }
}

package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.nicksunday.boardgameassistant.MainActivity
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.data.model.Game
import com.nicksunday.boardgameassistant.data.model.PlayLog
import com.nicksunday.boardgameassistant.data.model.Player
import com.nicksunday.boardgameassistant.data.model.PlayerScore
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import com.nicksunday.boardgameassistant.databinding.FragmentLogPlayBinding
import kotlinx.coroutines.launch
import java.time.LocalDate

class LogPlayFragment : Fragment(R.layout.fragment_log_play) {
    private lateinit var adapter: LogPlayAdapter
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            (requireActivity() as MainActivity).firestoreRepo
        )
    }
    private val firestoreRepository = FirestoreRepository()
    private val args: LogPlayFragmentArgs by navArgs()
    private var selectedGame: Game? = null
    private var pendingRowIndexToReselect: Int? = null
    private var hasInitializedAdapter = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentLogPlayBinding.bind(view)
        var useScores = true

        binding.scoreModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            useScores = isChecked
            if (::adapter.isInitialized) {
                adapter.setUseScores(isChecked)
            }
        }

        selectedGame = args.selectedGame

        selectedGame?.let { game ->
            binding.logPlayGameTitle.text = game.name
            Glide.with(this)
                .load(game.thumbnailUrl)
                .into(binding.logPlayGameThumbnail)
        }

        binding.savePlayButton.isEnabled = false

        viewModel.observePlayers().observe(viewLifecycleOwner) { players ->
            if (!hasInitializedAdapter) {
                adapter = LogPlayAdapter(
                    players = players,
                    onDeleteClicked = { position ->
                        adapter.removeRow(position)
                    },
                    onAddNewPlayerRequested = { rowIndex ->
                        pendingRowIndexToReselect = rowIndex
                        showAddPlayerBottomSheet()
                    }
                )
                binding.logPlayRecyclerView.adapter = adapter
                binding.logPlayRecyclerView.layoutManager =
                    androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                adapter.addRow()
                hasInitializedAdapter = true
                adapter.onScoresChanged = {
                    if (useScores) {
                        val entries = adapter.getEntries()
                        val topScore = entries.maxOfOrNull { it.score ?: Int.MIN_VALUE } ?: Int.MIN_VALUE
                        val topScorers = entries.filter { it.score == topScore }
                        val tied = if (topScorers.size > 1) topScorers.map { it.playerId }.toSet() else emptySet()
                        binding.logPlayRecyclerView.post {
                            adapter.setTiedPlayers(tied)
                        }
                    }
                }
                binding.savePlayButton.isEnabled = true
            } else {
                adapter.updatePlayers(players)
            }
        }

        viewModel.loadPlayers(firestoreRepository)

        binding.logPlayAddPlayerButton.setOnClickListener {
            adapter.addRow()
        }

        binding.savePlayButton.setOnClickListener {
            if (!::adapter.isInitialized) return@setOnClickListener

            lifecycleScope.launch {
                val originalEntries = adapter.getEntries()

                val resolvedEntries = originalEntries.map { entry ->
                    val friendCode = viewModel.observePlayers().value
                        ?.find { it.id == entry.playerId }
                        ?.friendCode

                    val resolvedId = if (!friendCode.isNullOrBlank()) {
                        firestoreRepository.getUidByFriendCode(friendCode) ?: entry.playerId
                    } else {
                        entry.playerId
                    }

                    entry.copy(playerId = resolvedId)
                }

                if (resolvedEntries.isEmpty()) return@launch
                if (useScores && resolvedEntries.any { it.score == null }) return@launch

                val bggId = selectedGame?.bggId ?: return@launch

                val playLog = if (useScores) {
                    val winners = calculateScoreWinners(resolvedEntries)
                    if (winners.isEmpty()) return@launch

                    val scores = resolvedEntries.map { PlayerScore(it.playerId, it.score ?: 0) }
                    PlayLog(
                        bggId = bggId,
                        date = LocalDate.now().toString(),
                        scores = scores,
                        winners = winners,
                        playerIds = resolvedEntries.map { it.playerId }
                    )
                } else {
                    val winners = resolvedEntries.filter { it.won }.map { it.playerId }
                    PlayLog(
                        bggId = bggId,
                        date = LocalDate.now().toString(),
                        scores = null,
                        winners = winners,
                        playerIds = resolvedEntries.map { it.playerId }
                    )
                }

                firestoreRepository.addPlayLog(playLog)
                findNavController().popBackStack()
            }
        }
    }

    fun onNewPlayerAdded(newPlayer: Player) {
        pendingRowIndexToReselect?.let { rowIndex ->
            adapter.setNewlyAddedPlayer(newPlayer.id, rowIndex)
            viewModel.loadPlayers(firestoreRepository)
            pendingRowIndexToReselect = null
        }
    }

    private fun showAddPlayerBottomSheet() {
        val sheet = AddPlayerBottomSheet()
        sheet.setOnPlayerAddedListener { player ->
            onNewPlayerAdded(player)
            viewModel.loadPlayers(firestoreRepository)
        }
        sheet.show(parentFragmentManager, "AddPlayerBottomSheet")
    }

    private fun calculateScoreWinners(entries: List<LogPlayAdapter.RowEntry>): List<String> {
        val maxScore = entries.maxOfOrNull { it.score ?: Int.MIN_VALUE } ?: return emptyList()
        val topScorers = entries.filter { it.score == maxScore }

        return if (topScorers.size > 1) {
            adapter.setTiedPlayers(topScorers.map { it.playerId }.toSet())
            val tieBreakerWinners = topScorers.filter { it.tieWinner }.map { it.playerId }
            if (tieBreakerWinners.isEmpty()) {
                emptyList()
            } else {
                tieBreakerWinners
            }
        } else {
            adapter.setTiedPlayers(emptySet())
            topScorers.map { it.playerId }
        }
    }
}

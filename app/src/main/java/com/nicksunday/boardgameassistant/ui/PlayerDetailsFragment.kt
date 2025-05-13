package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.nicksunday.boardgameassistant.MainActivity
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import com.nicksunday.boardgameassistant.databinding.FragmentPlayerDetailsBinding
import kotlinx.coroutines.launch
import java.util.Locale

class PlayerDetailsFragment : Fragment(R.layout.fragment_player_details) {
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            (requireActivity() as MainActivity).firestoreRepo
        )
    }
    private val args: PlayerDetailsFragmentArgs by navArgs()
    private val repo = FirestoreRepository()

    private lateinit var binding: FragmentPlayerDetailsBinding
    private lateinit var adapter: PlayerPlaysAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentPlayerDetailsBinding.bind(view)

        val playerId = args.playerId
        Log.d("PlayerDetails", "Viewing details for playerId: $playerId")

        viewModel.observePlayers().observe(viewLifecycleOwner) { players ->
            if (players.isNullOrEmpty()) return@observe

            lifecycleScope.launch {
                val allPlays = repo.getPlayLogs()
                val allGames = repo.getGames()

                val playerMap = players.associateBy { it.id }
                val player = playerMap[playerId]

                val resolvedUid = if (player?.friendCode != null) {
                    repo.getUidByFriendCode(player.friendCode!!) ?: playerId
                } else {
                    playerId
                }

                val myUid = repo.getUser().uid

                val logsForSharedGames = allPlays.filter {
                    it.playerIds.contains(resolvedUid) && it.playerIds.contains(myUid)
                }
                val winCount = logsForSharedGames.count { it.winners.contains(resolvedUid) }

                binding.playerGamesPlayed.text = String.format(Locale.getDefault(), "%d", logsForSharedGames.size)
                binding.playerGamesWon.text = String.format(Locale.getDefault(), "%d", winCount)

                if (player != null) {
                    binding.playerDetailsName.text = player.name
                    binding.playerFriendCode.text = player.friendCode
                }

                adapter = PlayerPlaysAdapter(
                    currentPlayerId = resolvedUid,
                    allPlayers = playerMap,
                    gameInfo = allGames.associateBy({ it.bggId }, { it.name to it.imageUrl }),
                    viewModel = viewModel,
                    repo = repo,
                    lifecycleScope = viewLifecycleOwner.lifecycleScope
                )
                binding.playLogRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                binding.playLogRecyclerView.adapter = adapter

                adapter.submitList(logsForSharedGames.sortedByDescending { it.timestamp })
            }
        }

        binding.copyFriendCodeButton.setOnClickListener {
            val friendCode = binding.playerFriendCode.text.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Friend Code", friendCode))
        }
    }
}

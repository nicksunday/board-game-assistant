package com.nicksunday.boardgameassistant.ui

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.nicksunday.boardgameassistant.MainActivity
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.api.BGGApi
import com.nicksunday.boardgameassistant.api.BoardGameRepository
import com.nicksunday.boardgameassistant.data.model.Game
import com.nicksunday.boardgameassistant.data.model.Player
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import com.nicksunday.boardgameassistant.databinding.ExpandableSectionBinding
import com.nicksunday.boardgameassistant.databinding.FragmentGameDetailsBinding
import kotlinx.coroutines.launch

class GameDetailsFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            (requireActivity() as MainActivity).firestoreRepo
        )
    }
    private var _binding: FragmentGameDetailsBinding? = null
    private val binding get() = _binding!!

    private val args by navArgs<GameDetailsFragmentArgs>()
    private val repo = FirestoreRepository()
    private lateinit var adapter: LoggedPlaysAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val bggId = args.bggId
        adapter = LoggedPlaysAdapter(emptyMap(), viewModel, repo, viewLifecycleOwner.lifecycleScope)
        binding.loggedPlaysRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.loggedPlaysRecyclerView.adapter = adapter

        viewModel.libraryGames.observe(viewLifecycleOwner) { games ->
            val game = games.find { it.bggId == bggId } ?: return@observe
            lifecycleScope.launch {
                val enrichedGame = if (game.description.isBlank()) {
                    val enriched = BoardGameRepository(BGGApi.create()).getBoardGameDetails(bggId)?.toGame()
                    enriched?.let { repo.cacheGlobalGame(it) }
                    enriched ?: game
                } else game

                displayGameDetails(enrichedGame)
            }
        }

        viewModel.observePlayers().observe(viewLifecycleOwner) { players ->
            val allPlayers = players.associateBy { it.id }
            adapter = LoggedPlaysAdapter(allPlayers, viewModel, repo, viewLifecycleOwner.lifecycleScope)
            binding.loggedPlaysRecyclerView.adapter = adapter

            lifecycleScope.launch {
                val plays = repo.getPlayLogs()
                val myUid = repo.getUser().uid
                val filteredPlays = plays.filter { it.bggId == bggId && it.playerIds.contains(myUid) }
                adapter.submitList(filteredPlays.sortedByDescending { it.timestamp })
            }
        }
    }

    private fun displayGameDetails(game: Game) {
        Glide.with(this)
            .load(game.imageUrl.ifBlank { game.thumbnailUrl })
            .into(binding.boxArtImageView)

        binding.gameTitle.text = game.name
        binding.gameYearPublished.text = "Published: ${game.yearPublished}"
        binding.playerCountChip.text = "${game.minPlayers}–${game.maxPlayers} players"
        binding.playTimeChip.text = "${game.playingTime} min"

        setupExpandable(binding.descriptionSection, "Description", Html.fromHtml(game.description, Html.FROM_HTML_MODE_COMPACT).toString())
        setupExpandable(binding.mechanicsSection, "Mechanics", game.mechanics.joinToString(", "))
        setupExpandable(binding.categoriesSection, "Categories", game.categories.joinToString(", "))
        setupExpandable(binding.designersSection, "Designers", game.designers.joinToString(", "))
        setupExpandable(binding.publishersSection, "Publishers", game.publishers.joinToString(", "))
    }

    private fun setupExpandable(section: ExpandableSectionBinding, title: String, text: String) {
        val header = section.toggleHeader
        val content = section.toggleContent

        val rightArrow = ContextCompat.getDrawable(requireContext(), R.drawable.ic_expand_less)
        val downArrow = ContextCompat.getDrawable(requireContext(), R.drawable.ic_expand_more)

        header.text = title
        content.text = text.ifBlank { "None listed." }
        content.isVisible = false
        header.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, rightArrow, null)

        header.setOnClickListener {
            val isVisible = !content.isVisible
            content.isVisible = isVisible
            header.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, if (isVisible) downArrow else rightArrow, null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

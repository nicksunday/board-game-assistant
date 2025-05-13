package com.nicksunday.boardgameassistant.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nicksunday.boardgameassistant.MainActivity
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.api.BGGApi
import com.nicksunday.boardgameassistant.api.BoardGame
import com.nicksunday.boardgameassistant.api.BoardGameRepository
import com.nicksunday.boardgameassistant.data.model.Game
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import com.nicksunday.boardgameassistant.databinding.FragmentAddGameBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddGameFragment : Fragment(R.layout.fragment_add_game) {
    private lateinit var adapter: AddGameAdapter
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            (requireActivity() as MainActivity).firestoreRepo
        )
    }
    private var debounceJob: Job? = null
    private val boardGameRepo = BoardGameRepository(BGGApi.create())

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAddGameBinding.bind(view)

        // Observe current library IDs
        viewModel.libraryGames.observe(viewLifecycleOwner) { games ->
            val libraryIds = games.map { it.bggId }.toSet()
            adapter.existingLibraryIds = libraryIds
            if (adapter.currentList.isNotEmpty()) {
                adapter.submitList(adapter.currentList.toList())
            }
        }

        // Clear-on-touch logic for search input
        binding.addGameSearchView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val editText = v as EditText
                val drawableEnd = 2
                val clearIcon = editText.compoundDrawables[drawableEnd]
                if (clearIcon != null &&
                    event.rawX >= (editText.right - clearIcon.bounds.width() - editText.paddingEnd)
                ) {
                    editText.text.clear()
                    editText.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }

        adapter = AddGameAdapter(
            onGameClicked = { game ->
                val bindingRef = FragmentAddGameBinding.bind(requireView())
                (requireActivity() as MainActivity).hideKeyboard(bindingRef.addGameSearchView)
                bindingRef.addGameSearchView.clearFocus()

                lifecycleScope.launch {
                    val firestoreRepo = FirestoreRepository()
                    // Attempt to load cached global metadata
                    val cached: Game? = firestoreRepo.getGame(game.id)

                    // Use cached if available, else fetch from BGG and cache globally
                    val fullDetails: BoardGame? = if (cached != null) {
                        BoardGame(
                            id            = cached.bggId,
                            primaryName   = cached.name,
                            description   = cached.description,
                            imageUrl      = cached.imageUrl,
                            thumbnailUrl  = cached.thumbnailUrl,
                            yearPublished = cached.yearPublished,
                            minPlayers    = cached.minPlayers,
                            maxPlayers    = cached.maxPlayers,
                            playingTime   = cached.playingTime,
                            averageRating = cached.averageRating,
                            boardGameRank = cached.boardGameRank,
                            minAge        = cached.minAge,
                            mechanics     = cached.mechanics,
                            categories    = cached.categories,
                            designers     = cached.designers,
                            publishers    = cached.publishers
                        )
                    } else {
                        boardGameRepo.getBoardGameDetails(game.id)?.also { remote ->
                            // Cache global metadata without affecting user library
                            firestoreRepo.cacheGlobalGame(remote.toGame())
                        }
                    }

                    // Update UI with details
                    if (fullDetails != null) {
                        val currentList = viewModel.observeSearchGames().value.orEmpty()
                        val updatedList = currentList.map {
                            if (it.id == game.id) fullDetails else it
                        }
                        adapter.submitList(updatedList)
                        val idx = updatedList.indexOfFirst { it.id == game.id }
                        if (idx >= 0) bindingRef.recyclerView.smoothScrollToPosition(idx)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Error fetching game details",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onAddGameClicked = { boardGame ->
                val firestoreRepo = FirestoreRepository()
                lifecycleScope.launch {
                    // Add both global and user reference
                    firestoreRepo.addGame(boardGame.toGame())
                    viewModel.refreshLibrary()
                    Toast.makeText(
                        requireContext(),
                        "Game added to library!",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Update adapter state
                    val newIds = adapter.existingLibraryIds + boardGame.id
                    adapter.existingLibraryIds = newIds
                    adapter.submitList(viewModel.observeSearchGames().value.orEmpty())
                }
            }
        )

        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Debounced search
        binding.addGameSearchView.addTextChangedListener { editable ->
            debounceJob?.cancel()
            val query = editable.toString().trim()
            debounceJob = lifecycleScope.launch {
                delay(300)
                if (query.length >= 2) {
                    viewModel.searchBoardGames(query, boardGameRepo)
                }
            }
        }

        viewModel.observeSearchGames().observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            val visible = list.isNotEmpty()
            binding.searchResultsLabel.visibility = if (visible) View.VISIBLE else View.GONE
            binding.searchHintText.visibility    = if (visible) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.setSearchGames(emptyList())
    }
}

package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.nicksunday.boardgameassistant.MainActivity
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.api.BGGApi
import com.nicksunday.boardgameassistant.api.BoardGameRepository
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import com.nicksunday.boardgameassistant.databinding.FragmentLibraryBinding
import com.nicksunday.boardgameassistant.util.safeNavigate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LibraryFragment : Fragment(R.layout.fragment_library) {
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            (requireActivity() as MainActivity).firestoreRepo
        )
    }
    private lateinit var adapter: LibraryAdapter
    private val firestoreRepository = FirestoreRepository()

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentLibraryBinding.bind(view)

        binding.addNewGameButton.shrink()
        binding.addNewGameButton.setOnClickListener {
            Log.d("LibraryFragment", "Add New Game button clicked")
            // Navigate to the AddGameFragment
            findNavController().safeNavigate(
                LibraryFragmentDirections.actionLibraryFragmentToAddGameFragment()
            )
        }
        binding.addNewGameButton.setOnHoverListener { v, _ ->
            binding.addNewGameButton.extend()
            false
        }

        binding.addNewGameButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!binding.addNewGameButton.isExtended) {
                        binding.addNewGameButton.extend()
                    }
                    true // consume event to delay navigation
                }
                MotionEvent.ACTION_UP -> {
                    v.performClick() // for accessibility
                    v.postDelayed({
                        findNavController().safeNavigate(
                            LibraryFragmentDirections.actionLibraryFragmentToAddGameFragment()
                        )
                    }, 100) // Delay slightly so expansion is visible
                    true
                }
                else -> false
            }
        }

        binding.libSwipeRefreshLayout.isEnabled = false

        binding.libSwipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch {
                viewModel.refreshLibrary()
                binding.libSwipeRefreshLayout.isRefreshing = false
                Toast.makeText(requireContext(), "Library refreshed!", Toast.LENGTH_SHORT).show()
            }
        }

        adapter = LibraryAdapter(
            onGameClicked = { game ->
                // TODO: Handle game item click
                Log.d("LibraryFragment-onGameClicked", "Game clicked: ${game.name}")
            },
            onLogPlayClicked = { game ->
                Log.d("LibraryFragment-onLogPlayClicked", "Game clicked: ${game.name}")
                val action = LibraryFragmentDirections.actionLibraryFragmentToLogPlayFragment(game)
                findNavController().safeNavigate(action)
            },
            onMoreDetailsClicked = { game ->
                Log.d("LibraryFragment-onMoreDetailsClicked", "Game clicked: ${game.name}")
                val action = LibraryFragmentDirections.actionLibraryFragmentToGameDetailsFragment(game.bggId)
                findNavController().safeNavigate(action)
            }
        )
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val game     = adapter.currentList[position]

                // launch in the Fragment's scope
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // 1) remove from Firestore
                        firestoreRepository.removeGame(game.bggId)

                        // 2) reload your MVVM list
                        viewModel.refreshLibrary()

                        // 3) show undo snackbar
                        Snackbar.make(
                            binding.recyclerView,
                            "\"${game.name}\" removed",
                            Snackbar.LENGTH_LONG
                        ).setAction("UNDO") {
                            viewLifecycleOwner.lifecycleScope.launch {
                                // re‑add and reload
                                firestoreRepository.addGame(game)
                                viewModel.refreshLibrary()
                            }
                        }.show()
                    } catch (e: Exception) {
                        Log.e("LibraryFragment", "Error removing game", e)
                        // put it back if something went wrong
                        adapter.notifyItemChanged(position)
                    }
                }
            }


            // Customize swipe visuals
            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

                val itemView = viewHolder.itemView
                val background = ColorDrawable(Color.RED)
                background.setBounds(
                    itemView.right + dX.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
                )
                background.draw(c)

                val deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete_black_foreground)
                val iconMargin = (itemView.height - deleteIcon!!.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = iconTop + deleteIcon.intrinsicHeight
                val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
                val iconRight = itemView.right - iconMargin

                deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                deleteIcon.draw(c)
            }
        })

        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        var searchJob: Job? = null

        binding.librarySearchView.addTextChangedListener { editable ->
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(300) // debounce delay
                val query = editable?.toString().orEmpty()
                viewModel.setLibrarySearchQuery(query)
            }
        }

        binding.librarySearchView.setOnTouchListener { v, event ->
            val DRAWABLE_END = 2
            if (event.action == MotionEvent.ACTION_UP) {
                val drawable = binding.librarySearchView.compoundDrawables[DRAWABLE_END]
                if (drawable != null) {
                    val drawableWidth = drawable.bounds.width()
                    val touchX = event.rawX
                    val rightEdge = binding.librarySearchView.right
                    if (touchX >= rightEdge - drawableWidth - binding.librarySearchView.paddingEnd) {
                        binding.librarySearchView.setText("")
                        viewModel.setLibrarySearchQuery("")
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        viewModel.libraryGames.observe(viewLifecycleOwner) {
            Log.d("LibraryFragment", "Submitting ${it.size} games to adapter")
            adapter.submitList(it)
        }
    }

}
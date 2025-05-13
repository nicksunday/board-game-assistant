package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.nicksunday.boardgameassistant.MainActivity
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.data.model.Player
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import com.nicksunday.boardgameassistant.databinding.FragmentPlayersBinding
import com.nicksunday.boardgameassistant.util.safeNavigate
import kotlinx.coroutines.launch
import java.util.UUID

class PlayersFragment : Fragment(R.layout.fragment_players) {
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            (requireActivity() as MainActivity).firestoreRepo
        )
    }
    private lateinit var adapter: PlayersAdapter
    private val firestoreRepository = FirestoreRepository()

    private fun showAddPlayerSheet() {
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_player, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(view)

        val nameInput = view.findViewById<EditText>(R.id.playerNameInput)
        val friendCodeInput = view.findViewById<EditText>(R.id.playerFriendCodeInput)
        val confirmBtn = view.findViewById<Button>(R.id.addPlayerConfirmBtn)

        confirmBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val code = friendCodeInput.text.toString().trim()

            if (name.isNotBlank()) {
                val player = Player(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    friendCode = code
                )
                lifecycleScope.launch {
                    firestoreRepository.addOrUpdatePlayer(player)
                    viewModel.loadPlayers(firestoreRepository)
                    dialog.dismiss()
                }
            } else {
                nameInput.error = "Player name is required"
            }
        }

        dialog.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPlayersBinding.bind(view)

        adapter = PlayersAdapter(
            onPlayerClicked = { /* Optional scroll or highlight */ },
            onViewDetailsClicked = { playerId ->
                val action = PlayersFragmentDirections.actionPlayersFragmentToPlayerDetailsFragment(playerId)
                findNavController().safeNavigate(action)
            },
            onSaveFriendCode = { player, newCode ->
                val updatedPlayer = player.copy(friendCode = newCode)
                lifecycleScope.launch {
                    firestoreRepository.addOrUpdatePlayer(updatedPlayer)
                }
            }
        )

        binding.playersRecyclerView.adapter = adapter
        binding.playersRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        binding.addPlayerButton.setOnClickListener {
            showAddPlayerSheet()
        }

        viewModel.observePlayers()
            .observe(viewLifecycleOwner) { players ->
                adapter.submitList(players)
            }

        viewModel.observePlayerWinCounts().observe(viewLifecycleOwner) { winMap ->
            adapter.setPlayerWinCounts(winMap)
        }
        viewModel.loadPlayerWinCounts(firestoreRepository)

        // Start listening to Firestore player updates
        viewModel.startListeningToPlayers(firestoreRepository)

        // Swipe-to-delete setup
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, tgt: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val player = adapter.currentList[vh.adapterPosition]
                if (player.id == FirebaseAuth.getInstance().uid) {
                    // Don't allow deleting self
                    adapter.notifyItemChanged(vh.adapterPosition)
                    Toast.makeText(requireContext(), "You can't remove yourself!", Toast.LENGTH_SHORT).show()
                    return
                }

                lifecycleScope.launch {
                    firestoreRepository.removePlayer(player)
                    Snackbar.make(binding.playersRecyclerView, "${player.name} removed", Snackbar.LENGTH_LONG)
                        .setAction("UNDO") {
                            lifecycleScope.launch {
                                firestoreRepository.addOrUpdatePlayer(player)
                                val newList = adapter.currentList.toMutableList()
                                adapter.submitList(null)
                                adapter.submitList(newList)
                            }
                        }.show()
                }
            }

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
        itemTouchHelper.attachToRecyclerView(binding.playersRecyclerView)

        // Observe player data
        viewModel.observePlayers().observe(viewLifecycleOwner) { players ->
            adapter.submitList(players)
        }

        // Initial load
        viewModel.loadPlayers(firestoreRepository)
    }
}

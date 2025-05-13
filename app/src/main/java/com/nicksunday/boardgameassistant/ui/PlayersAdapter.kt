package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.nicksunday.boardgameassistant.MainActivity
import com.nicksunday.boardgameassistant.data.model.Player
import com.nicksunday.boardgameassistant.databinding.RowPlayerBinding

class PlayersAdapter(
    private val onPlayerClicked: (Player) -> Unit,
    private val onViewDetailsClicked: (String) -> Unit,
    private val onSaveFriendCode: (Player, String) -> Unit
) : ListAdapter<Player, PlayersAdapter.PlayerViewHolder>(DIFF_CALLBACK) {

    private var expandedPlayerId: String? = null
    private var playerWinCounts: Map<String, Int> = emptyMap()

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Player>() {
            override fun areItemsTheSame(oldItem: Player, newItem: Player): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Player, newItem: Player): Boolean {
                return oldItem == newItem
            }
        }
    }

    fun setPlayerWinCounts(newMap: Map<String, Int>) {
        val oldMap = playerWinCounts
        playerWinCounts = newMap

        currentList.forEachIndexed { index, player ->
            val oldCount = oldMap[player.id] ?: 0
            val newCount = newMap[player.id] ?: 0
            if (oldCount != newCount) {
                notifyItemChanged(index)
            }
        }
    }

    inner class PlayerViewHolder(private val binding: RowPlayerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(player: Player) {
            val isExpanded = player.id == expandedPlayerId



            binding.playerName.text = player.name
            val wins = playerWinCounts[player.id] ?: 0
            binding.playerWins.text = "Wins: $wins" // You can fill this in later

            // Toggle expanded view
            binding.expandedSection.visibility = if (isExpanded) View.VISIBLE else View.GONE

            // Pre-fill friend code
            binding.friendCodeInput.setText(player.friendCode)
            val isCurrentUser = player.id == FirebaseAuth.getInstance().uid
            binding.friendCodeInput.isEnabled = !isCurrentUser
            binding.saveFriendCodeButton.isEnabled = !isCurrentUser
            // Grey out the text field if it's the current user
            if (isCurrentUser) {
                binding.friendCodeInput.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.darker_gray))
                binding.friendCodeInput.setBackgroundColor(Color.TRANSPARENT)
            }

            // Save Friend Code button
            binding.saveFriendCodeButton.setOnClickListener {
                val context = binding.root.context
                if (context is MainActivity) {
                    context.hideKeyboard(binding.friendCodeInput)
                }
                binding.friendCodeInput.clearFocus()
                val newCode = binding.friendCodeInput.text.toString().trim()
                onSaveFriendCode(player, newCode)
            }

            // View Details button
            binding.viewDetailsButton.setOnClickListener {
                onViewDetailsClicked(player.id)
            }

            // Toggle expand on root click
            binding.root.setOnClickListener {
                val wasExpanded = expandedPlayerId == player.id
                expandedPlayerId = if (wasExpanded) null else player.id

                notifyItemChanged(adapterPosition)
                currentList.indexOfFirst { it.id == expandedPlayerId }
                    .takeIf { it != -1 && it != adapterPosition }
                    ?.let { notifyItemChanged(it) }

                if (wasExpanded) {
                    val context = binding.root.context
                    if (context is MainActivity) {
                        context.hideKeyboard(binding.friendCodeInput)
                    }
                    binding.friendCodeInput.clearFocus()
                }

                onPlayerClicked(player)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val binding = RowPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlayerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}


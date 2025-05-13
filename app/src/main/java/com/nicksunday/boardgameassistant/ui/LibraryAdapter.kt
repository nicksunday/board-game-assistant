package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nicksunday.boardgameassistant.data.model.Game
import com.nicksunday.boardgameassistant.databinding.RowBoardGameBinding
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.nicksunday.boardgameassistant.MainActivity

class LibraryAdapter(
    private val onGameClicked: (Game) -> Unit,
    private val onLogPlayClicked: (Game) -> Unit,
    private val onMoreDetailsClicked: (Game) -> Unit
) : ListAdapter<Game, LibraryAdapter.GameViewHolder>(DIFF_CALLBACK) {

    private var expandedGameId: String? = null

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Game>() {
            override fun areItemsTheSame(oldItem: Game, newItem: Game): Boolean {
                return oldItem.bggId == newItem.bggId
            }

            override fun areContentsTheSame(oldItem: Game, newItem: Game): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class GameViewHolder(private val binding: RowBoardGameBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(game: Game) {
            val isExpanded = game.bggId == expandedGameId

            if (isExpanded) {
                Glide.with(binding.root.context)
                    .load(game.imageUrl)
                    .into(binding.boardGameCover)
            } else {
                Glide.with(binding.root.context)
                    .load(game.thumbnailUrl)
                    .into(binding.boardGameThumbnail)
            }

            // Set titles
            binding.compactTitle.text = game.name
            binding.expandedTitle.text = game.name

            // Toggle layout visibility
            binding.expandedImageContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.compactRow.visibility = if (isExpanded) View.GONE else View.VISIBLE

            // Animated expand/collapse
            TransitionManager.beginDelayedTransition(binding.itemRootLayout, AutoTransition())
            binding.expandedSection.visibility = if (isExpanded) View.VISIBLE else View.GONE

            // Expanded info
            binding.playTime.text = "Play time: ${game.playingTime} min"
            binding.playerRange.text = "Players: ${game.minPlayers}–${game.maxPlayers}"

            binding.logPlayButton.setOnClickListener {
                onLogPlayClicked(game)
            }

            binding.moreDetailsButton.setOnClickListener {
                onMoreDetailsClicked(game)
            }

            // Handle expand toggle
            binding.root.setOnClickListener {
                val previousExpanded = expandedGameId
                expandedGameId = if (isExpanded) null else game.bggId

                notifyItemChanged(adapterPosition)
                previousExpanded?.let { prevId ->
                    val prevIndex = currentList.indexOfFirst { it.bggId == prevId }
                    if (prevIndex != -1 && prevIndex != adapterPosition) {
                        notifyItemChanged(prevIndex)
                    }
                }

                onGameClicked(game)
                val context = binding.root.context
                if (context is MainActivity) {
                    context.hideKeyboard(binding.root)
                }
                binding.root.clearFocus()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val binding = RowBoardGameBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GameViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

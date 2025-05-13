package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nicksunday.boardgameassistant.api.BoardGame
import com.nicksunday.boardgameassistant.databinding.RowAddGameBinding

class AddGameAdapter(
    private val onGameClicked: (BoardGame) -> Unit,
    private val onAddGameClicked: (BoardGame) -> Unit
) : ListAdapter<BoardGame, AddGameAdapter.GameViewHolder>(DIFF_CALLBACK) {

    private var expandedGameId: String? = null

    var existingLibraryIds = setOf<String>()

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BoardGame>() {
            override fun areItemsTheSame(oldItem: BoardGame, newItem: BoardGame): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: BoardGame, newItem: BoardGame): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class GameViewHolder(private val binding: RowAddGameBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(game: BoardGame) {
            // Compact Section
            binding.bgTitle.text = game.primaryName
            binding.bgYearPublished.text = String.format(game.yearPublished.toString())

            val isExpanded = game.id == expandedGameId
            val isInLibrary = existingLibraryIds.contains(game.id)

            // Expanded content
            binding.expandedSection.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.bgThumbnail.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.bgDescription.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.bgAddGameButton.visibility = if (isExpanded) View.VISIBLE else View.GONE

            if (isExpanded) {
                Glide.with(binding.root.context)
                    .load(game.imageUrl.ifBlank { game.thumbnailUrl })
                    .into(binding.bgThumbnail)

                binding.bgDescription.text = Html.fromHtml(
                    game.description,
                    Html.FROM_HTML_MODE_COMPACT
                )
            }

            // Add Game Button logic
            binding.bgAddGameButton.apply {
                if (isInLibrary) {
                    // Animate disabled state
                    animate().alpha(0f).setDuration(150).withEndAction {
                        text = "Already in Library ✓"
                        isEnabled = false
                        animate().alpha(1f).setDuration(150).start()
                    }.start()
                } else {
                    alpha = 1f
                    isEnabled = true
                    text = "Add Game"
                    setOnClickListener {
                        onAddGameClicked(game)
                        expandedGameId = null
                        notifyItemChanged(adapterPosition)
                    }
                }
            }

            binding.root.setOnClickListener {
                val previouslyExpandedId = expandedGameId
                val clickedId = game.id

                expandedGameId = if (clickedId == previouslyExpandedId) {
                    null
                } else {
                    clickedId
                }

                notifyItemChanged(adapterPosition) // Notify the adapter to refresh the item
                previouslyExpandedId?.let { prevId ->
                    val prevIndex = currentList.indexOfFirst { it.id == prevId }
                    if (prevIndex != -1 && prevIndex != adapterPosition) {
                        notifyItemChanged(prevIndex)
                    }
                }
                onGameClicked(game)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val binding = RowAddGameBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GameViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

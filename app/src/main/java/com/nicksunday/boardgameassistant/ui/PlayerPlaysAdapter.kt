package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nicksunday.boardgameassistant.data.model.Player
import com.nicksunday.boardgameassistant.data.model.PlayLog
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import com.nicksunday.boardgameassistant.databinding.RowPlayerPlaysBinding
import kotlinx.coroutines.launch

class PlayerPlaysAdapter(
    private val currentPlayerId: String,
    private val allPlayers: Map<String, Player>,
    gameInfo: Map<String, Pair<String, String?>>,
    private val viewModel: MainViewModel,
    private val repo: FirestoreRepository,
    private val lifecycleScope: LifecycleCoroutineScope
) : ListAdapter<PlayLog, PlayerPlaysAdapter.PlayLogViewHolder>(DIFF_CALLBACK) {

    private val expandedMap = mutableMapOf<String, Boolean>()
    private val dynamicGameInfo = gameInfo.toMutableMap()

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PlayLog>() {
            override fun areItemsTheSame(oldItem: PlayLog, newItem: PlayLog): Boolean {
                return "${oldItem.bggId}_${oldItem.timestamp}" == "${newItem.bggId}_${newItem.timestamp}"
            }

            override fun areContentsTheSame(oldItem: PlayLog, newItem: PlayLog): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class PlayLogViewHolder(private val binding: RowPlayerPlaysBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int) {
            val playLog = getItem(position)
            val playLogId = "${playLog.bggId}_${playLog.timestamp}"
            val isExpanded = expandedMap[playLogId] ?: false

            // Resolve game info
            val (gameName, gameImageUrl) = dynamicGameInfo[playLog.bggId] ?: run {
                // Trigger background enrichment if game is unknown
                lifecycleScope.launch {
                    val enriched = viewModel.getBoardGameDetails(playLog.bggId)
                    if (enriched != null) {
                        val name = enriched.primaryName
                        val image = enriched.imageUrl
                        dynamicGameInfo[playLog.bggId] = name to image

                        // If still bound to this item, rebind
                        if (adapterPosition == position) {
                            notifyItemChanged(position)
                        }
                    }
                }
                "Unknown Game" to null
            }

            binding.playLogGameTitle.text = gameName
            Glide.with(binding.root.context)
                .load(gameImageUrl)
                .into(binding.playLogGameImage)

            // Show current player's score
            val currentScore = playLog.scores
                ?.find { it.playerId == currentPlayerId }
                ?.score

            if (currentScore != null) {
                binding.playLogScore.visibility = View.VISIBLE
                binding.playLogScore.text = "Score: $currentScore"
            } else {
                binding.playLogScore.visibility = View.GONE
            }


            // Show date
            binding.playLogDate.text = playLog.date

            // Show trophy if current player won
            binding.playLogWinIcon.visibility =
                if (playLog.winners.contains(currentPlayerId)) View.VISIBLE else View.GONE

            // Expandable section
            binding.expandedPlayLog.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.expandedPlayLog.removeAllViews()

            if (isExpanded) {
                binding.expandedPlayLog.removeAllViews()

                playLog.playerIds.forEach { playerId ->
                    val isCurrent = playerId == currentPlayerId
                    val row = TextView(binding.root.context).apply {
                        text = "Loading..."
                        textSize = 14f
                        if (isCurrent) setTypeface(null, android.graphics.Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    binding.expandedPlayLog.addView(row)

                    // async fetch name
                    lifecycleScope.launch {
                        val name = viewModel.resolvePlayerName(playerId, allPlayers, repo)
                        val score = playLog.scores?.find { it.playerId == playerId }?.score
                        val won = playLog.winners.contains(playerId)

                        val text = buildString {
                            append(name)
                            if (score != null) append(": $score")
                            if (won) append(" 🏆")
                        }
                        row.text = text
                    }
                }
            }



            binding.root.setOnClickListener {
                expandedMap[playLogId] = !isExpanded
                notifyItemChanged(position)
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayLogViewHolder {
        val binding = RowPlayerPlaysBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlayLogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlayLogViewHolder, position: Int) {
        holder.bind(position)
    }
}

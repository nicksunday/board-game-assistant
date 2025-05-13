package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nicksunday.boardgameassistant.data.model.PlayLog
import com.nicksunday.boardgameassistant.data.model.Player
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import com.nicksunday.boardgameassistant.databinding.RowLoggedPlayBinding
import kotlinx.coroutines.launch

class LoggedPlaysAdapter(
    private val allPlayers: Map<String, Player>,
    private val viewModel: MainViewModel,
    private val repo: FirestoreRepository,
    private val lifecycleScope: LifecycleCoroutineScope
) : ListAdapter<PlayLog, LoggedPlaysAdapter.PlayViewHolder>(DIFF_CALLBACK) {

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PlayLog>() {
            override fun areItemsTheSame(oldItem: PlayLog, newItem: PlayLog): Boolean {
                return oldItem.bggId == newItem.bggId && oldItem.date == newItem.date
            }

            override fun areContentsTheSame(oldItem: PlayLog, newItem: PlayLog): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class PlayViewHolder(private val binding: RowLoggedPlayBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(play: PlayLog) {
            binding.playDateText.text = play.date
            binding.playersListContainer.removeAllViews()

            val context = binding.root.context
            val scoreMap = play.scores?.associateBy { it.playerId } ?: emptyMap()

            play.playerIds.toSet().forEach { playerId ->
                val rowView = TextView(context).apply {
                    text = "Loading..."
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                binding.playersListContainer.addView(rowView)

                lifecycleScope.launch {
                    val name = viewModel.resolvePlayerName(playerId, allPlayers, repo)
                    val score = scoreMap[playerId]?.score
                    val won = play.winners.contains(playerId)

                    val text = buildString {
                        append(name)
                        if (score != null) append(": $score")
                        if (won) append(" 🏆")
                    }
                    rowView.text = text
                    if (!won) {
                        rowView.setTextColor(context.getColor(android.R.color.darker_gray))
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayViewHolder {
        val binding = RowLoggedPlayBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlayViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

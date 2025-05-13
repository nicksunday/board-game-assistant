package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.R.layout.simple_spinner_dropdown_item
import android.R.layout.simple_spinner_item
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nicksunday.boardgameassistant.data.model.Player
import com.nicksunday.boardgameassistant.databinding.RowLogPlayPlayerBinding

class LogPlayAdapter(
    players: List<Player>,
    private val onDeleteClicked: (Int) -> Unit,
    private val onAddNewPlayerRequested: (rowIndex: Int) -> Unit
) : RecyclerView.Adapter<LogPlayAdapter.ViewHolder>() {

    private var players = players.toMutableList()
    private var newlyAddedPlayerId: String? = null
    private var newlyAddedRowIndex: Int? = null
    private var useScores = true
    private var tiedPlayerIds: Set<String> = setOf()
    var onScoresChanged: (() -> Unit)? = null

    fun setUseScores(enabled: Boolean) {
        useScores = enabled
        notifyDataSetChanged()
    }

    fun setNewlyAddedPlayer(id: String, rowIndex: Int) {
        newlyAddedPlayerId = id
        newlyAddedRowIndex = rowIndex
    }

    fun setTiedPlayers(tied: Set<String>) {
        if (tied != tiedPlayerIds) {
            val previous = tiedPlayerIds
            tiedPlayerIds = tied
            rows.forEachIndexed { index, row ->
                val wasTied = previous.contains(row.playerId)
                val isTiedNow = tied.contains(row.playerId)
                if (wasTied != isTiedNow) {
                    notifyItemChanged(index)
                }
            }
        }
    }

    fun updatePlayers(newPlayers: List<Player>) {
        if (players != newPlayers) {
            players.clear()
            players.addAll(newPlayers)
            notifyItemRangeChanged(0, rows.size)
        }
    }

    data class RowEntry(
        var playerId: String = "",
        var score: Int? = null,
        var won: Boolean = false,
        var tieWinner: Boolean = false,
    )

    val rows = mutableListOf<RowEntry>()

    fun addRow() {
        rows.add(RowEntry())
        notifyItemInserted(rows.lastIndex)
    }

    fun removeRow(position: Int) {
        if (position in rows.indices) {
            rows.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getEntries(): List<RowEntry> = rows.filter { it.playerId.isNotBlank() }

    inner class ViewHolder(val binding: RowLogPlayPlayerBinding) : RecyclerView.ViewHolder(binding.root) {
        private var scoreWatcher: TextWatcher? = null

        fun bind(entry: RowEntry, position: Int) {
            val allPlayers = listOf(Player(id = "", name = "Select Player")) +
                    players +
                    Player(id = "new", name = "➕ Add New Player...")

            val playerNames = allPlayers.map { it.name }
            val playerIds = allPlayers.map { it.id }

            val adapter = ArrayAdapter(binding.root.context, simple_spinner_item, playerNames)
            adapter.setDropDownViewResource(simple_spinner_dropdown_item)
            binding.playerSpinner.adapter = adapter

            val selectedId = when {
                newlyAddedRowIndex == position -> ""
                else -> entry.playerId
            }

            val playerIndex = playerIds.indexOf(selectedId)
            if (playerIndex >= 0) {
                binding.playerSpinner.setSelection(playerIndex)
            }

            if (newlyAddedRowIndex == position) {
                newlyAddedPlayerId = null
                newlyAddedRowIndex = null
            }

            binding.playerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    val idSelected = playerIds[pos]

                    if (idSelected == "new") {
                        onAddNewPlayerRequested(position)
                        return
                    }

                    // Prevent duplicates
                    val isDuplicate = rows.withIndex().any { (i, row) ->
                        i != position && row.playerId == idSelected
                    }

                    if (isDuplicate) {
                        // Revert spinner selection to previous value
                        val previousIndex = playerIds.indexOf(entry.playerId)
                        if (previousIndex >= 0) {
                            binding.playerSpinner.setSelection(previousIndex)
                        } else {
                            binding.playerSpinner.setSelection(0) // Fallback
                        }

                        // Show toast
                        parent.context?.let {
                            android.widget.Toast.makeText(it, "Player already selected", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        entry.playerId = idSelected
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

            // Score Mode
            if (useScores) {
                binding.playerScoreInput.visibility = View.VISIBLE
                binding.playerWinChip.visibility = if (tiedPlayerIds.contains(entry.playerId)) View.VISIBLE else View.GONE

                // Remove old TextWatcher to avoid leaks and value overwriting
                scoreWatcher?.let {
                    binding.playerScoreInput.removeTextChangedListener(it)
                }

                // Update text if needed
                val scoreText = entry.score?.toString() ?: ""
                if (binding.playerScoreInput.text.toString() != scoreText) {
                    binding.playerScoreInput.setText(scoreText)
                }

                // Attach fresh watcher
                scoreWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        entry.score = s?.toString()?.toIntOrNull()
                        onScoresChanged?.invoke()
                    }
                    override fun afterTextChanged(s: Editable?) {}
                }
                binding.playerScoreInput.addTextChangedListener(scoreWatcher)

                // Tie-breaker checkbox handler
                binding.playerWinChip.setOnCheckedChangeListener(null)
                binding.playerWinChip.isChecked = entry.tieWinner
                binding.playerWinChip.setOnCheckedChangeListener { _, isChecked ->
                    entry.tieWinner = isChecked
                }

            } else {
                // Win/Loss Mode
                binding.playerScoreInput.visibility = View.GONE
                binding.playerWinChip.visibility = View.VISIBLE

                binding.playerWinChip.setOnCheckedChangeListener(null)
                binding.playerWinChip.isChecked = entry.won
                binding.playerWinChip.setOnCheckedChangeListener { _, isChecked ->
                    entry.won = isChecked
                }
            }

            binding.deleteRowBtn.setOnClickListener {
                onDeleteClicked(position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowLogPlayPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(rows[position], position)
    }
}

package com.nicksunday.boardgameassistant.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.nicksunday.boardgameassistant.MainActivity
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.data.model.Game
import com.nicksunday.boardgameassistant.databinding.FragmentRandomGameSearchBinding
import com.nicksunday.boardgameassistant.util.safeNavigate

class RandomGameSearchFragment : Fragment(R.layout.fragment_random_game_search) {
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            (requireActivity() as MainActivity).firestoreRepo
        )
    }
    private lateinit var binding: FragmentRandomGameSearchBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentRandomGameSearchBinding.bind(view)

        viewModel.libraryGames.observe(viewLifecycleOwner) { allGames ->
            if (allGames.isEmpty()) {
                binding.randomGameSearchButton.isEnabled = false
                Toast.makeText(
                    requireContext(),
                    "Your library is empty. Add some games first!",
                    Toast.LENGTH_LONG
                ).show()
                return@observe
            }

            setupGameTypeSpinner(allGames)

            binding.randomGameSearchButton.setOnClickListener {
                val filtered = filterGames(allGames)
                if (filtered.isNotEmpty()) {
                    viewModel.setLastFilteredRandomGames(filtered)
                    val chosen = filtered.random()
                    val action = RandomGameSearchFragmentDirections
                        .actionRandomGameSearchFragmentToRandomGameResultFragment(chosen)
                    findNavController().safeNavigate(action)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "No games found matching your criteria.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupGameTypeSpinner(allGames: List<Game>) {
        val mechanics = allGames
            .flatMap { it.mechanics }
            .distinct()
            .sorted()
            .toMutableList()
            .apply { add(0, "Any") }

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            mechanics
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.gameTypeSpinner.adapter = spinnerAdapter
        binding.gameTypeSpinner.prompt = getString(R.string.random_type_prompt)
    }

    private fun filterGames(allGames: List<Game>): List<Game> {
        val games = allGames.filter { it.name.isNotBlank() }

        val numPlayersStr = binding.numberOfPlayersET.text.toString()
        val numPlayers    = numPlayersStr.toIntOrNull()
        val timeToPlayStr = binding.timeToPlayET.text.toString()
        val timeToPlay    = timeToPlayStr.toIntOrNull()
        val selectedMechanic = binding.gameTypeSpinner.selectedItem as? String

        return games.filter { game ->
            if ("Expansion for Base-game" in game.categories) return@filter false

            val matchesPlayers = numPlayers == null ||
                    (game.minPlayers <= numPlayers && game.maxPlayers >= numPlayers)

            val matchesTime = timeToPlay == null ||
                    game.playingTime <= timeToPlay

            val matchesType = selectedMechanic == "Any" ||
                    (selectedMechanic != null && selectedMechanic in game.mechanics)

            matchesPlayers && matchesTime && matchesType
        }
    }
}

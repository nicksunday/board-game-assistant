package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.nicksunday.boardgameassistant.MainActivity
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.data.model.Game
import com.nicksunday.boardgameassistant.databinding.FragmentRandomGameResultBinding

class RandomGameResultFragment : Fragment(R.layout.fragment_random_game_result) {
    private val args: RandomGameResultFragmentArgs by navArgs()
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            (requireActivity() as MainActivity).firestoreRepo
        )
    }
    private lateinit var binding: FragmentRandomGameResultBinding
    private lateinit var currentGame: Game
    private var previousFilterResults: List<Game>? = null
    private val shownGameIds = mutableSetOf<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentRandomGameResultBinding.bind(view)
        currentGame = args.game

        val shake = ObjectAnimator.ofFloat(binding.tryAgainButton, "rotation", 0f, -10f, 10f, -10f, 10f, 0f)
        shake.duration = 300

        showGame(currentGame)

        val filteredList = viewModel.getLastFilteredRandomGames()
        previousFilterResults = filteredList

        binding.returnToSearchButton.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.tryAgainButton.setOnClickListener {
            shake.start()
            val nextGame = previousFilterResults?.randomOrNull()

            if (nextGame != null) {
                currentGame = nextGame
                showGame(nextGame)
            } else {
                Toast.makeText(requireContext(), "No more matching games found.", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun showGame(game: Game) {
        shownGameIds.add(game.bggId) // Track it

        binding.randomGameSelectionTV.text = game.name

        // Fade out first
        binding.randomGameCover.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                // Change image and text
                Glide.with(requireContext())
                    .load(game.imageUrl)
                    .placeholder(R.drawable.placeholder_boxart)
                    .into(binding.randomGameCover)

                binding.randomGameSelectionTV.text = game.name

                // Fade back in
                binding.randomGameCover.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()

    }

}

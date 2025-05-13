package com.nicksunday.boardgameassistant.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.nicksunday.boardgameassistant.MainActivity
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.api.BGGApi
import com.nicksunday.boardgameassistant.api.BoardGame
import com.nicksunday.boardgameassistant.api.BoardGameRepository
import com.nicksunday.boardgameassistant.data.util.importBoardGameLibrary
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import com.nicksunday.boardgameassistant.databinding.FragmentImportLibraryBinding
import kotlinx.coroutines.launch

class ImportLibraryFragment : Fragment(R.layout.fragment_import_library) {
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(
            (requireActivity() as MainActivity).firestoreRepo
        )
    }
    private var importedGames: List<BoardGame> = emptyList()
    private lateinit var binding: FragmentImportLibraryBinding

    private val boardGameRepository by lazy { BoardGameRepository(BGGApi.create()) }
    private val firestoreRepository by lazy { FirestoreRepository() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentImportLibraryBinding.bind(view)

        binding.libraryImportBtn.isEnabled = false
        binding.libraryImportBtn.visibility = View.GONE
        binding.librarySearchResultTV.visibility = View.GONE

        binding.librarySearchBtn.setOnClickListener {
            (activity as MainActivity).hideKeyboard(view)
            val username = binding.librarySearchET.text.toString().trim()
            if (username.isEmpty()) {
                Toast.makeText(context, "Please enter a username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val boardGames = boardGameRepository.getUserCollection(username)
                    importedGames = boardGames
                    val gameCount = boardGames.size

                    binding.librarySearchResultTV.apply {
                        visibility = View.VISIBLE
                        text = getString(R.string.import_library_result, username, gameCount)
                    }
                    binding.libraryImportBtn.apply {
                        visibility = View.VISIBLE
                        isEnabled = gameCount > 0
                    }

                } catch (e: Exception) {
                    Log.e("ImportLibraryFragment", "Error fetching user collection", e)
                    Toast.makeText(context, "Error fetching library", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.libraryImportBtn.setOnClickListener {
            binding.librarySearchET.setText("")
            binding.importLogTV.text = ""
            binding.libraryImportBtn.apply {
                isEnabled = false
                text = "Importing..."
                alpha = 0.5f
            }

            lifecycleScope.launch {
                try {
                    val result = importBoardGameLibrary(
                        requireContext(),
                        importedGames,
                        boardGameRepo = boardGameRepository,
                        firestoreRepo = firestoreRepository
                    ) { line ->
                        if (isAdded) {
                            binding.importLogTV.append("$line\n")
                            binding.importLogScroll.post {
                                binding.importLogScroll.fullScroll(View.FOCUS_DOWN)
                            }
                        }
                    }

                    if (isAdded) {
                        binding.importLogTV.append("\n✅ ${result.added} added, ⏭️ ${result.skipped} skipped\n")
                        viewModel.refreshLibrary()
                    }

                } catch (e: Exception) {
                    Log.e("ImportLibraryFragment", "Error importing library", e)
                    if (isAdded) {
                        Toast.makeText(context, "Error importing library", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    if (isAdded) {
                        binding.libraryImportBtn.apply {
                            isEnabled = true
                            text = getString(R.string.import_library_button)
                            alpha = 1f
                        }
                    }
                }
            }
        }
    }
}

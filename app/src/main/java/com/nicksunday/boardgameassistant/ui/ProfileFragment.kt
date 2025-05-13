package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.data.model.Player
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import com.nicksunday.boardgameassistant.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private lateinit var binding: FragmentProfileBinding
    private val repo = FirestoreRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentProfileBinding.bind(view)

        val user = FirebaseAuth.getInstance().currentUser
        binding.profileEmail.text = user?.email ?: "No email"
        binding.displayNameEditText.setText(user?.displayName ?: "Anonymous")

        lifecycleScope.launch {
            val userDoc = repo.getUser()
            binding.profileFriendCode.text = userDoc.friendCode
        }

        // 1) Save Name + propagate to Player
        binding.saveNameButton.setOnClickListener {
            val newName = binding.displayNameEditText.text.toString().trim()
            if (newName.isEmpty()) return@setOnClickListener

            user?.let { firebaseUser ->
                // a) Update the FirebaseUser profile
                firebaseUser.updateProfile(userProfileChangeRequest {
                    displayName = newName
                }).addOnCompleteListener { profileTask ->
                    if (!profileTask.isSuccessful) {
                        Toast.makeText(context, "Name update failed", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }

                    // b) Update your users/{uid} doc *and* the players sub‐doc
                    viewLifecycleOwner.lifecycleScope.launch {
                        // update the User document’s “name” field
                        repo.updateUserName(newName)

                        // fetch the (unchanged) friendCode
                        val updatedUser = repo.getUser()
                        val selfPlayer = Player(
                            id = firebaseUser.uid,
                            name = newName,
                            friendCode = updatedUser.friendCode
                        )
                        // write the Player document under /users/{uid}/players/{uid}
                        repo.addOrUpdatePlayer(selfPlayer)

                        Toast.makeText(context, "Name updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }


        binding.resetPasswordButton.setOnClickListener {
            val email = FirebaseAuth.getInstance().currentUser?.email
            if (email != null) {
                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Check $email for reset link", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        binding.copyFriendCodeButton.setOnClickListener {
            val friendCode = binding.profileFriendCode.text.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Friend Code", friendCode))
            Toast.makeText(requireContext(), "Friend code copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}

package com.nicksunday.boardgameassistant.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.databinding.FragmentLoginBinding


class LoginFragment : Fragment() {
    private lateinit var binding: FragmentLoginBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Skip if already logged in
        auth.currentUser?.let {
            findNavController().navigate(R.id.action_loginFragment_to_libraryFragment)
            return
        }

        binding.registerButton.setOnClickListener {
            val name     = binding.nameEditText.text.toString().trim()
            val email    = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "Fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    // update FirebaseUser profile
                    auth.currentUser?.updateProfile(
                        userProfileChangeRequest { displayName = name }
                    )?.addOnCompleteListener {
                        // now navigate; MainActivity will pick up displayName
                        findNavController()
                            .navigate(R.id.action_loginFragment_to_libraryFragment)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Register failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        binding.loginButton.setOnClickListener {
            val email    = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "Fill in both fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    findNavController()
                        .navigate(R.id.action_loginFragment_to_libraryFragment)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // Forgot‑password flow
        binding.forgotPasswordTextView.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            if (email.isBlank()) {
                Toast.makeText(context, "Enter your email first", Toast.LENGTH_SHORT).show()
            } else {
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(context,
                            "Reset link sent to your email", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context,
                            "Reset failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
}


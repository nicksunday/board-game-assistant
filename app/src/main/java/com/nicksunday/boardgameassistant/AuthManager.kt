package com.nicksunday.boardgameassistant

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class AuthManager {
    private val auth = FirebaseAuth.getInstance()
    private val userLiveData = MutableLiveData<FirebaseUser?>()

    init {
        userLiveData.value = auth.currentUser
        auth.addAuthStateListener { firebaseAuth ->
            userLiveData.value = firebaseAuth.currentUser
        }
    }

    fun observeUser(): LiveData<FirebaseUser?> = userLiveData

    fun login(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message)
                }
            }
    }

    fun register(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message)
                }
            }
    }

    fun logout(activity: Activity) {
        auth.signOut()
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
}

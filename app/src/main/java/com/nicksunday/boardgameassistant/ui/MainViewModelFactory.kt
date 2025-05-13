package com.nicksunday.boardgameassistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository

class MainViewModelFactory(
    private val firestoreRepo: FirestoreRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(firestoreRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

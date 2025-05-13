package com.nicksunday.boardgameassistant.ui
// This file includes code developed with the assistance of OpenAI's ChatGPT (GPT-4),
// used to help implement features, resolve bugs, and improve structure.

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nicksunday.boardgameassistant.R
import com.nicksunday.boardgameassistant.data.model.Player
import com.nicksunday.boardgameassistant.data.repository.FirestoreRepository
import kotlinx.coroutines.launch

class AddPlayerBottomSheet : BottomSheetDialogFragment() {

    private var listener: ((Player) -> Unit)? = null

    fun setOnPlayerAddedListener(callback: (Player) -> Unit) {
        listener = callback
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_add_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val nameInput = view.findViewById<EditText>(R.id.playerNameInput)
        val friendCodeInput = view.findViewById<EditText>(R.id.playerFriendCodeInput)
        val addBtn = view.findViewById<Button>(R.id.addPlayerConfirmBtn)

        addBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val friendCode = friendCodeInput.text.toString().trim()

            if (name.isNotBlank()) {
                val newPlayer = Player(name = name, friendCode = friendCode)

                viewLifecycleOwner.lifecycleScope.launch {
                    FirestoreRepository().addOrUpdatePlayer(newPlayer)
                    listener?.invoke(newPlayer)
                    dismiss()
                }
            } else {
                nameInput.error = "Name is required"
            }
        }
    }
}

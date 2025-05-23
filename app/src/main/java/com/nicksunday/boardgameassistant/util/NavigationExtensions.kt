package com.nicksunday.boardgameassistant.util

import androidx.navigation.NavController
import androidx.navigation.NavDirections

fun NavController.safeNavigate(direction: NavDirections) {
    currentDestination?.getAction(direction.actionId)?.let {
        navigate(direction)
    }
}

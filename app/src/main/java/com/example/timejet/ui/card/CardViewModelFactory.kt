package com.timejet.bio.timejet.ui.card

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CardViewModelFactory(
    private val application: Application,
    private val taskId: Long,
    private val eventType: String,
    private val uid: Long,
    private val projectName: String,
    private val taskWorkingUsername: String,
    private val predecessorsIds: String
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CardViewModel(application, taskId, eventType, uid, projectName, taskWorkingUsername, predecessorsIds) as T
    }
}

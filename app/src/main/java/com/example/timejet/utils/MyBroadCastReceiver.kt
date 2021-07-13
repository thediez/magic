package com.timejet.bio.timejet.utils

import RVAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.timejet.bio.timejet.repository.RealmHandler
import kotlinx.coroutines.*

class MyBroadCastReceiver : BroadcastReceiver() {

    lateinit var realmHandler: RealmHandler
    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    override fun onReceive(context: Context?, intent: Intent?) {
        RealmHandler.ioScope.async {
            realmHandler = RealmHandler.getInstance()
            val itemList = realmHandler.checkIfNotification()
        }
    }
}
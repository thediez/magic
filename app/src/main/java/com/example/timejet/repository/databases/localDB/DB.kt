package com.timejet.bio.timejet.repository.databases.localDB

import android.content.Context
import android.os.Looper
import android.widget.Toast

var currentPossibleTasks:Int? = null
var prevPossibleTasks:Int? = null

fun setCurrentPossibleTasks(num:Int) {
    currentPossibleTasks?.let {
        prevPossibleTasks = it
    }
    currentPossibleTasks = num
}

fun showToast(message: String, context: Context) {
    if (Looper.myLooper() == null) {
        Looper.prepare()
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}



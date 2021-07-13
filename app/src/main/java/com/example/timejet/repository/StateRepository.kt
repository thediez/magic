package com.timejet.bio.timejet.repository

import android.annotation.SuppressLint
import android.content.Context
import androidx.preference.PreferenceManager
import android.util.Log
import com.timejet.bio.timejet.ui.main.LOG_TAG
import com.timejet.bio.timejet.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class StateRepository private constructor() {
    private val LOG_TAG = this::class.java.simpleName

    init {
        restore()
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: StateRepository? = null

        @SuppressLint("StaticFieldLeak")
        private lateinit var context: Context
        fun create(context: Context): StateRepository =
                INSTANCE ?: synchronized(this) {
                    this.context = context
                    getInstance()
                }

        fun getInstance(): StateRepository =
                INSTANCE ?: synchronized(this) {
                    INSTANCE ?: StateRepository().also { INSTANCE = it }
                }
    }

    var currentPTS: CurrentPTS? = null
        set(value) {
            if (checkAndReact(field, value)) {
                field = value
                save()
            }
        }

    private fun checkAndReact(field: CurrentPTS?, value: CurrentPTS?): Boolean {
        if (field == null && value == null) return false
        if (field != null && value != null && field.uid == value.uid && field.projectName == value.projectName) {
            if (field.event != value.event) setEvent(value.event)
            return false
        }
        value?.let {
            reactOnChangeEvent(it, it.event)
        }

        return true
    }

    fun setEvent(event: EventType) {
        currentPTS?.let { currentState ->
            if (currentState.event != event) {
                reactOnChangeEvent(currentState, event)
                saveEvent()
            }
        } ?: run {
        }
    }

    fun setDurationInCurrentPts(timePassed: Long) {
        getInstance().currentPTS?.setDuration(timePassed)
    }

    fun timeStartToNull() {
        getInstance().currentPTS?.let {
            Log.e(LOG_TAG, "timeStartToNull() ${(it.timerStart)}")
            if (it.timerStart != null) {
                RealmHandler.ioScope.launch {
                    RealmHandler.getInstance().addDurationTimeUserActivity(context, it.projectName, it.uid, Date().time - it.timerStart!!.time)
                    it.setDurationToNull()
                }
            } else {
                it.setDurationToNull()
            }
        }
    }

    private fun reactOnChangeEvent(currentState: CurrentPTS, event: EventType) {
        when (event) {
            EventType.START_EVENT -> {
                currentState.timerStart = Date()
                RealmHandler.ioScope.launch {
                    RealmHandler.getInstance().addTimeToUserActivityDB(context, currentState.projectName, currentState.uid)
                }
            }
            EventType.PAUSE_EVENT -> {
                currentState.let { currState ->
                    RealmHandler.ioScope.launch {
                        val projectName = currState.projectName
                        val uid = currState.uid
                        val duration = currState.durationTime
                        duration.let { it1 ->
                            RealmHandler.getInstance().addDurationTimePTSDB(context, projectName, uid, it1, true).let {
                                if (currentState.uid > 0) {
                                    RealmHandler.getInstance().alignAdditionalTimeByNegativeRemainingTime(context, projectName, uid).let {
                                        currState.timerStart = null
                                    }
                                } else {
                                    currState.timerStart = null
                                }
                            }
                        }
                    }
                    RealmHandler.ioScope.launch(Dispatchers.IO) {
                        RealmHandler.getInstance().addDurationTimeUserActivity(context, currState.projectName, currState.uid, currState.durationTime)
                    }
                }
            }
            EventType.END_EVENT -> {
                RealmHandler.ioScope.launch {
                    RealmHandler.getInstance().completeTask(context)
                }
                currentState.timerStart = null
            }
            EventType.PHONECALL_EVENT -> {
                currentState.timerStart = Date()
            }
            EventType.EMAIL_EVENT -> {
                currentState.timerStart = Date()
            }
            EventType.MEETING_EVENT -> {
                currentState.timerStart = Date()
            }
            EventType.TRAVEL_EVENT -> {
                currentState.timerStart = Date()
            }
            else -> return
        }
        currentState.event = event
        save()
    }

    fun getEvent(): EventType {
        currentPTS?.let { return it.event }
        return EventType.NONE
    }

    @SuppressLint("CommitPrefEdits")
    private fun save() {
        currentPTS?.let {
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit().putLong("newCurrentTaskUID", it.uid)
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit().putString("newCurrentEvent", it.event.name)
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit().putString("newCurrentProjectName", it.projectName)
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit().putLong("newTimerStart", it.timerStart?.time
                    ?: -1)
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit().apply()
        } ?: run {
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit().putLong("newCurrentTaskUID", -1)
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit().putString("newCurrentEvent", "")
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit().putString("newCurrentProjectName", "")
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit().apply()
        }
    }

    private fun saveEvent() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val editor = prefs.edit()
        currentPTS?.let {
            editor.putString("newCurrentEvent", it.event.name)
        } ?: run {
            editor.putString("newCurrentEvent", "")
        }

        editor.apply()
    }

    fun restore() {
        Log.w(LOG_TAG, "Restore")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val newCurrentProjectName = prefs.getString("newCurrentProjectName", "")!!
        val newCurrentTaskUID = prefs.getLong("newCurrentTaskUID", -1)
        val newCurrentEventStr = prefs.getString("newCurrentEvent", "")
        val newTimerStart = prefs.getLong("newTimerStart", -1L)
        val newCurrentEvent = try {
            newCurrentEventStr?.let { EventType.valueOf(it) }
        } catch (e: Exception) {
            EventType.NONE
        }
        Log.d(LOG_TAG, "Str: $newCurrentEventStr, newCurrentEvent: $newCurrentEvent")
        if (newCurrentTaskUID != -1L || newCurrentProjectName.isNotEmpty()) {
            currentPTS = newCurrentEvent?.let { CurrentPTS(newCurrentProjectName, newCurrentTaskUID, it) }
            currentPTS?.let {
                it.timerStart = if (newTimerStart > 0) Date(newTimerStart) else null
            }
        }
    }

    fun reset() {
        currentPTS = null
        save()
    }

}

class CurrentPTS(val projectName: String, val uid: Long) {
    var event: EventType = EventType.NONE
        internal set
    var timerStart: Date? = null

    val durationTime: Long
        get() {
            val time: Long
            time = if (timerStart != null) {
                Date().time - timerStart!!.time
            } else {
                0L
            }
            return time
        }

    fun setDuration(timePassed: Long) {
        this.timerStart = Date(Date().time - timePassed)
    }

    fun setDurationToNull() {
        this.timerStart = Date()
    }

    constructor(projectName: String, uid: Long, event: EventType) : this(projectName, uid) {
        this.event = event
    }
}

enum class EventType(val string: String) {
    START_EVENT("start"),
    PAUSE_EVENT("pause"),
    END_EVENT("end"),
    PHONECALL_EVENT("phonecall"),
    EMAIL_EVENT("email"),
    MEETING_EVENT("meeting"),
    TRAVEL_EVENT("travel"),
    NONE("")
}
package com.timejet.bio.timejet.ui.main

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timejet.repository.models.FirestorePTS_DB
import com.example.timejet.repository.models.PTS_DB
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.type.DateTime
import com.timejet.bio.timejet.R
import com.timejet.bio.timejet.config.CHANNEL_ID
import com.timejet.bio.timejet.config.TIME_PATTERN
import com.timejet.bio.timejet.repository.EventType
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.repository.StateRepository
import com.timejet.bio.timejet.repository.databases.localDB.currentPossibleTasks
import com.timejet.bio.timejet.repository.databases.localDB.prevPossibleTasks
import com.timejet.bio.timejet.repository.databases.localDB.showToast
import com.timejet.bio.timejet.ui.MainActivity
import com.timejet.bio.timejet.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Period
import org.joda.time.PeriodType
import org.joda.time.format.DateTimeFormat



var timeKoeff: Double = 1.0

const val LOG_TAG = "MainActivityUtils"

//private val ROLLBAR_API_KEY = "ffaf32ef34d3498594469fe5fcb20485"

lateinit var appContext: Context

var deleteAll = 0
internal const val TASK_ENDED = -1
internal const val EMPTY_EVENT = -2

const val ERROR_GET_UID = -1L

val uiDbyEvent: Long
    get() {
        StateRepository.getInstance().currentPTS?.let {
            Log.d(LOG_TAG, "UID: ${it.uid}")
            return when (it.event) {
                EventType.PHONECALL_EVENT -> PTS_DB.PTS_PHONECALL_UID
                EventType.EMAIL_EVENT -> PTS_DB.PTS_EMAIL_UID
                EventType.MEETING_EVENT -> PTS_DB.PTS_MEETING_UID
                EventType.TRAVEL_EVENT -> PTS_DB.PTS_TRAVEL_UID
                else -> it.uid
            }
        } ?: run {
            Log.w(LOG_TAG, "UID: $ERROR_GET_UID")
            return ERROR_GET_UID
        }
    }

//internal var projCounter = 0



fun calculateDuration(fromDate: DateTime, toDate: DateTime): String {
    val period = Period(fromDate, toDate, PeriodType.time())

    val duration = StringBuilder()
    val periodSec = period.seconds
    val periodMin = period.minutes
    val periodHrs = period.hours

    if (periodSec != 0) duration.append(periodSec).append(" sec, ")
    if (periodMin != 0) duration.append(periodMin).append(" min, ")
    if (periodHrs != 0) duration.append(periodHrs).append(" hours, ")

    return duration.toString()
}

fun calculateDurationHRS(fromDate: DateTime, toDate: DateTime): Double {
    val period = Period(fromDate, toDate, PeriodType.time())
    var time = 0.0
    if (period.seconds != 0) time += period.seconds / 3600.0
    if (period.minutes != 0) time += period.minutes / 60.0
    if (period.hours != 0) time += period.hours.toDouble()
    return time
}

val currentDateTime: String
    get() {
        return try {
            DateTimeFormat.forPattern(TIME_PATTERN).print(DateTime(DateTimeZone.UTC))
        } catch (e: Exception) {
            "".also {
                Log.d(LOG_TAG, e.message)
            }
        }
    }



fun calculatePlusHours(realmHandler: RealmHandler): Double {
    val lastUserActivity = realmHandler.fetchLastUserActivity() ?: return 0.0
    val correctEvent = when (lastUserActivity.eventName) {
        PTS_DB.PHONECALL_EVENT, PTS_DB.EMAIL_EVENT, PTS_DB.START_EVENT,
        PTS_DB.MEETING_EVENT, PTS_DB.TRAVEL_EVENT -> true
        else -> false
    }
    return if (lastUserActivity.eventDuration == null && correctEvent) {
        (System.currentTimeMillis() - lastUserActivity.curTimeMillis) / 3600000.0
    } else 0.0
}

fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    if (cm != null) {
        val netInfo = cm.activeNetworkInfo
        if (netInfo != null && netInfo.isConnected)
            return true
    }
    showToast("No internet", context)
    return false
}

fun getCleanTimeBudget(timeBudget: String): String {
    return timeBudget.replace("[^0-9.,]".toRegex(), "")
}

fun sendToCloudMyProgress(userEmail: String?, isOnline: Boolean, projectID: String?) {
    RealmHandler.ioScope.launch {
        //TODO: change String? to String
        val ptsItems = RealmHandler.getInstance().getTaskByWorkingUsername(userEmail!!)
        ptsItems?.let {
            val amountOfMyTasks = it.size
            Log.d(LOG_TAG, "sendToCloudMyProgress amountOfMyTasks: $amountOfMyTasks")
            if (amountOfMyTasks != 0) {
                for (step in 0 until amountOfMyTasks) {
                    val ptsItem = it[step] ?: continue
                    Log.d(LOG_TAG, "${ptsItem.projectName} uid: ${ptsItem.uid} ptsItem.ptSprogress ${ptsItem.ptSprogress}")
                    ///val ptsProgress = ptsItem.ptSprogress.trim { it <= ' ' }
                    //val zeroProgress = ptsProgress.toDouble() == 0.0 || ptsProgress.isEmpty()
                    val isUserOk = ptsItem.usersAssigned.contains(userEmail) ||
                            ptsItem.taskWorkingUsername.contains(userEmail)
                    if (isUserOk && /*(!zeroProgress *//*|| ptsItem.isPTScompleted*//*) &&*/ isOnline) {
                        if (projectID == null || projectID.isEmpty()) continue
                        val firestorePTSDb = FirestorePTS_DB(ptsItem.uid, ptsItem.projectName, userEmail)
                        firestorePTSDb.read = ptsItem.isRead // FIXME to Illya for WHY this are here?
                        if (firestorePTSDb.projectName == null) continue
                        val name = if (firestorePTSDb.uid == PTS_DB.PTS_PHONECALL_UID
                            || firestorePTSDb.uid == PTS_DB.PTS_EMAIL_UID
                            || firestorePTSDb.uid == PTS_DB.PTS_MEETING_UID
                            || firestorePTSDb.uid == PTS_DB.PTS_TRAVEL_UID) {
                            val emailUser = Utils.getEmail(firestorePTSDb.userWorking)
                            firestorePTSDb.taskName + ", " + emailUser
                        } else {
                            firestorePTSDb.uid.toString()
                        }
                        withContext(Dispatchers.IO) {
                            FirebaseFirestore
                                .getInstance(FirebaseApp.getInstance(projectID))
                                .collection(firestorePTSDb.projectName)
                                .document(name)
                                .set(firestorePTSDb)
                                .addOnSuccessListener { Log.d(ContentValues.TAG, "DocumentSnapshot added with ID: " + firestorePTSDb.uid ) }//+ documentReference.getId()))
                                // TODO: 29.11.2018 send XX, success YY message
                                .addOnCompleteListener { Log.d(ContentValues.TAG, "complete ") }
                                .addOnFailureListener { e -> Log.d(ContentValues.TAG, "Error adding document", e) }
                        }
                    }
                }
            }

        }

    }
}

fun sendNotification(context: Context, taskId: Long, isDeadLine: Boolean) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = NotificationChannel(CHANNEL_ID, "TimeJet", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "TimeJet task ends soon"
        channel.enableVibration(true)
        notificationManager.createNotificationChannel(channel)
    }
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    val pendingIntent = PendingIntent.getActivity(context,0,intent, 0)
    var description = ""

    if (isDeadLine){
        description = "Task with uid: ${taskId} deadline will end less than 20 hours. Hurry up!"
    } else {
        description = "Task with uid: ${taskId} less than 20 hours remain. Hurry up!"
    }

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.icon)
            .setAutoCancel(true)
            .setContentText("TimeJet")
            .setContentText(description)
            .setContentIntent(pendingIntent)
    with(NotificationManagerCompat.from(context)) {
        notify(1,builder.build())
    }

}

fun sendNotification(context: Context, possibleTasks:Int, difference:Int) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = NotificationChannel(CHANNEL_ID, "TimeJet", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "TimeJet tasks"
        channel.enableVibration(true)
        notificationManager.createNotificationChannel(channel)
    }
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    val pendingIntent = PendingIntent.getActivity(context,0,intent, 0)


    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.icon)
            .setAutoCancel(true)
            .setContentText("TimeJet")
            .setContentText("Tasks you can Start: ${createStyledText(currentPossibleTasks!!, (currentPossibleTasks ?: 0) - (prevPossibleTasks ?: 0) )}")
            .setContentIntent(pendingIntent)
    with(NotificationManagerCompat.from(context)) {
        notify(1,builder.build())
    }
}

fun createStyledText(possibleTasks: Int, difference:Int):Spannable {

    val stylishText = SpannableString("$possibleTasks ($difference) ")
    val possibleTasksLength = possibleTasks.toString().length
    val diffrenceLength = difference.toString().length

    Log.d(LOG_TAG,"possibleTaskLength=$possibleTasksLength , differenceLength=$diffrenceLength")

    stylishText.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            possibleTasksLength,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    stylishText.setSpan(
            ForegroundColorSpan(Color.RED),
            possibleTasksLength + 2,
            possibleTasksLength + 2 + (diffrenceLength),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    return  stylishText
}


fun autoUpdateMilestoneState() {
    RealmHandler.ioScope.launch {
        val listOfMilestones = RealmHandler.getInstance().getMilestonesList()
        listOfMilestones?.let {
            for (ptsDb in listOfMilestones) {
                val predecessorsUIDlist = ptsDb.predecessorsIDlist
                val projectName = ptsDb.projectName
                val stringPredecessorsList = RealmHandler.getInstance().getAllSublist(predecessorsUIDlist, projectName)
                val allPredecessorsFinished = stringPredecessorsList.toString().contains("[N]")
                if (allPredecessorsFinished || stringPredecessorsList.toString().isEmpty() || stringPredecessorsList.toString().contains("\\\n"))
                    continue
                RealmHandler.getInstance().updateMilestoneState(ptsDb.id)
            }

        }

    }
}

fun isPossibleTasksInc():Boolean {
    Log.d("MA Model","cur: ${currentPossibleTasks}, prev: ${prevPossibleTasks}")
    if(currentPossibleTasks != null && prevPossibleTasks != null) {
        return currentPossibleTasks!! > prevPossibleTasks!!
    }

    if(prevPossibleTasks == null && currentPossibleTasks != null) {
        return true
    }
    return false
}
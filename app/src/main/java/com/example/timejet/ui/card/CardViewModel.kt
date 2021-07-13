package com.timejet.bio.timejet.ui.card

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.timejet.bio.timejet.R
import com.timejet.bio.timejet.config.TIMER_PERIOD
import com.timejet.bio.timejet.repository.*
import com.timejet.bio.timejet.repository.databases.localDB.currentPossibleTasks
import com.timejet.bio.timejet.repository.databases.localDB.prevPossibleTasks
import com.timejet.bio.timejet.repository.databases.localDB.showToast
import com.timejet.bio.timejet.repository.dropbox.uploadFileToDropbox
import com.timejet.bio.timejet.repository.models.PTS_DB
import com.timejet.bio.timejet.ui.main.*
import com.timejet.bio.timejet.utils.Event
import com.timejet.bio.timejet.utils.LocalUserInfo
import com.timejet.bio.timejet.utils.Utils
import com.timejet.bio.timejet.utils.Utils.Companion.calculateTime
import com.timejet.bio.timejet.writeXLSXFile
import kotlinx.coroutines.*
import org.joda.time.LocalDate
import java.text.SimpleDateFormat
import java.util.*


class CardViewModel(
        application: Application,
        taskId: Long,
        eventType: String,
        uid: Long,
        projectName: String,
        taskWorkingUsername: String,
        predecessorsIds: String
) : AndroidViewModel(application) {
    private val LOG_TAG = this::class.java.simpleName
    private var context: Context = application
    private var dropboxRepository: DropboxRepository
    private var realmHandler: RealmHandler
    var idOfTask = taskId
    private val summary = MutableLiveData<StringBuilder>()
    private val summaryTotalProjects = MutableLiveData<Int>()
    private val summaryAllYourTask = MutableLiveData<Int>()
    private val summaryFinishedTask = MutableLiveData<Int>()

    private val summaryTasksStart = MutableLiveData<Pair<Int, Int>>()
    val timeBudget: MutableLiveData<String> = MutableLiveData()
    private val remainingTime: MutableLiveData<String> = MutableLiveData()
    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    private var timer: Timer? = null
    private lateinit var timerTask: TimerTask
    private var jobSummaryStringTaskStart: Job? = null
    var mp = MediaPlayer.create(context, R.raw.clack)
    private val firestoreSyncResult = MutableLiveData<Event<Boolean>>()
    private val snackbarMsg = MutableLiveData<Event<String>>()

    private val uid = MutableLiveData<String>()
    private val predecessorsIds = MutableLiveData<String>()
    private val taskWorkingUsername = MutableLiveData<String>()
    private val projectName = MutableLiveData<String>()
    private val taskName = MutableLiveData<String>()
    private val stepName = MutableLiveData<String>()
    private val timeProgress = MutableLiveData<String>()
    private val taskDeadLine = MutableLiveData<String>()
    private val isRead = MutableLiveData<Boolean>()
    private val isDone = MutableLiveData<Boolean>()
    private var task: PTS_DB? = null
    private var phoneTime = MutableLiveData<String>()
    private var emailTime = MutableLiveData<String>()
    private var meetingTime = MutableLiveData<String>()
    private var travelTime = MutableLiveData<String>()
    private var globalMinutes = MutableLiveData<String>()
    private var globalHours = MutableLiveData<String>()
    private var isPhoneButtonAnimate = MutableLiveData<Event<Boolean>>()
    private var isEmailButtonAnimate = MutableLiveData<Event<Boolean>>()
    private var isMeetingButtonAnimate = MutableLiveData<Event<Boolean>>()
    private var isTravellingButtonAnimate = MutableLiveData<Event<Boolean>>()
    private var isSyncAnimate = MutableLiveData<Boolean>()
    private var isStartAnimate = MutableLiveData<Event<Boolean>>()
    private var isPauseAnimate = MutableLiveData<Event<Boolean>>()
    private var startPressed = MutableLiveData<Boolean>()
    private var pausePressed = MutableLiveData<Boolean>()
    private var endPressed = MutableLiveData<Boolean>()
    private var isEndAnimate = MutableLiveData<Event<Boolean>>()
    private var isShowSpinnerDialog = MutableLiveData<EventType>()
    private var isPopUpWindowClosed = MutableLiveData<EventType>()
    private var isPopUpWindowOpend = MutableLiveData<Boolean>()
    private var isOverBudget = MutableLiveData<Boolean>()
    private var isConfirmEnd = MutableLiveData<Boolean>()
    private var phoneProject = MutableLiveData<String>()
    private var emailProject = MutableLiveData<String>()
    private var meetingProject = MutableLiveData<String>()
    private var travelProject = MutableLiveData<String>()
    private var currentTime = MutableLiveData<String>()
    private var btnClickedTimestamp: Long = 0


    fun uid(): LiveData<String> = uid
    fun taskWorkingUsername(): LiveData<String> = taskWorkingUsername
    fun projectName(): LiveData<String> = projectName
    fun taskName(): LiveData<String> = taskName
    fun stepName(): LiveData<String> = stepName
    fun timeProgress(): LiveData<String> = timeProgress
    fun timeBudget(): LiveData<String> = timeBudget
    fun taskDeadline(): LiveData<String> = taskDeadLine
    fun isRead(): LiveData<Boolean> = isRead
    fun phoneTime(): LiveData<String> = phoneTime
    fun emailTime(): LiveData<String> = emailTime
    fun meetingTime(): LiveData<String> = meetingTime
    fun travelTime(): LiveData<String> = travelTime
    fun globalMinutes(): LiveData<String> = globalMinutes
    fun globalHours(): LiveData<String> = globalHours
    fun isDone(): LiveData<Boolean> = isDone
    fun isPhoneButtonAnimate(): LiveData<Event<Boolean>> = isPhoneButtonAnimate
    fun isEmailButtonAnimate(): LiveData<Event<Boolean>> = isEmailButtonAnimate
    fun isMeetingButtonAnimate(): LiveData<Event<Boolean>> = isMeetingButtonAnimate
    fun isTravellingButtonAnimate(): LiveData<Event<Boolean>> = isTravellingButtonAnimate
    fun isSyncAnimate(): LiveData<Boolean> = isSyncAnimate
    fun isStartAnimate(): LiveData<Event<Boolean>> = isStartAnimate
    fun isPauseAnimate(): LiveData<Event<Boolean>> = isPauseAnimate
    fun isEndAnimate(): LiveData<Event<Boolean>> = isEndAnimate
    fun isShowSpinnerDialog(): LiveData<EventType> = isShowSpinnerDialog
    fun isPopUpWindowClosed(): LiveData<EventType> = isPopUpWindowClosed
    fun isPopUpWindowOpend(): LiveData<Boolean> = isPopUpWindowOpend
    fun startPressed(): LiveData<Boolean> = startPressed
    fun pausePressed(): LiveData<Boolean> = pausePressed
    fun endPressed(): LiveData<Boolean> = endPressed
    fun isOverBudget(): LiveData<Boolean> = isOverBudget
    fun isConfirmEnd(): LiveData<Boolean> = isConfirmEnd
    fun firestoreSyncResult(): LiveData<Event<Boolean>> = firestoreSyncResult
    fun snackbarMsg(): LiveData<Event<String>> = snackbarMsg

    init {
        Log.d(LOG_TAG, "init:")
        dropboxRepository = DropboxRepository.getInstance(application)
        realmHandler = RealmHandler.getInstance()
        when (StateRepository.getInstance().getEvent()) {
            EventType.PHONECALL_EVENT -> {
                isPhoneButtonAnimate.value = Event(content = true, alreadyHandled = true)
                isStartAnimate.value = Event(content = false, alreadyHandled = true)
                isPauseAnimate.value = Event(content = true, alreadyHandled = true)
                isEndAnimate.value = Event(content = false, alreadyHandled = true)
                startPressed.value = false
                pausePressed.value = true
                endPressed.value = false
            }
            EventType.EMAIL_EVENT -> {
                isEmailButtonAnimate.value = Event(content = true, alreadyHandled = true)
                isStartAnimate.value = Event(content = false, alreadyHandled = true)
                isPauseAnimate.value = Event(content = true, alreadyHandled = true)
                isEndAnimate.value = Event(content = false, alreadyHandled = true)
                startPressed.value = false
                pausePressed.value = true
                endPressed.value = false
            }
            EventType.MEETING_EVENT -> {
                isMeetingButtonAnimate.value = Event(content = true, alreadyHandled = true)
                isStartAnimate.value = Event(content = false, alreadyHandled = true)
                isPauseAnimate.value = Event(content = true, alreadyHandled = true)
                isEndAnimate.value = Event(content = false, alreadyHandled = true)
                startPressed.value = false
                pausePressed.value = true
                endPressed.value = false
            }
            EventType.TRAVEL_EVENT -> {
                isTravellingButtonAnimate.value = Event(content = true, alreadyHandled = true)
                isStartAnimate.value = Event(content = false, alreadyHandled = true)
                isPauseAnimate.value = Event(content = true, alreadyHandled = true)
                isEndAnimate.value = Event(content = false, alreadyHandled = true)
                startPressed.value = false
                pausePressed.value = true
                endPressed.value = false
            }
            EventType.START_EVENT -> {

            }
            else -> {
                isStartAnimate.value = Event(content = false, alreadyHandled = true)
                isPauseAnimate.value = Event(content = true, alreadyHandled = true)
                isEndAnimate.value = Event(content = false, alreadyHandled = true)
                startPressed.value = false
                pausePressed.value = false
                endPressed.value = false
            }
        }

        isShowSpinnerDialog.value = EventType.NONE
        isOverBudget.value = false
        firestoreSyncResult.observeForever {
            firestoreSyncDone(it)
        }
        reScheduleTimer()
        restoreState(taskId, eventType, uid, projectName, taskWorkingUsername, predecessorsIds)
    }

    private fun reinitViews(event: EventType) {
        when (event) {
            EventType.PAUSE_EVENT -> {
                isStartAnimate.value = Event(content = false, alreadyHandled = true)
                task?.isPTScompleted?.let { completed ->
                    getSummaryAllYourTask()
                    getSummaryFinishedTask()
                    getSummaryStringTaskStart()
                    if (!completed) {
                        isPauseAnimate.value = Event(content = true, alreadyHandled = true)
                        pausePressed.value = true
                        isEndAnimate.value = Event(content = false, alreadyHandled = true)
                        endPressed.value = false
                        isDone.value = false
                    } else {
                        isPauseAnimate.value = Event(content = false, alreadyHandled = true)
                        pausePressed.value = false
                        isEndAnimate.value = Event(content = true, alreadyHandled = true)
                        endPressed.value = true
                        isDone.value = true
                    }
                }
                startPressed.value = false
                isPhoneButtonAnimate.value = Event(false, true)
                isEmailButtonAnimate.value = Event(false, true)
                isMeetingButtonAnimate.value = Event(false, true)
                isTravellingButtonAnimate.value = Event(false, true)
                updatePTSTimeProgress()
                updatePhoneCallEmailMeetingDuration(EventType.NONE)

            }
            else -> return
        }
    }

    fun setPopupWindowState(opened: Boolean) {
        isPopUpWindowOpend.postValue(opened)
        if (!opened) {
            isPopUpWindowClosed.postValue(StateRepository.getInstance().getEvent())
        }
    }

    fun clickPhone() {
        mp.start()
        if (isSyncAnimate.value == true) {
            Toast.makeText(getApplication(), R.string.sync_is_running, Toast.LENGTH_LONG).show()
            return
        }

        val currEv = StateRepository.getInstance().getEvent()
        StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
        task?.let {
            reloadTaskById(it.id)
        }

        if (currEv == EventType.PHONECALL_EVENT || currEv == EventType.EMAIL_EVENT || currEv == EventType.MEETING_EVENT || currEv == EventType.TRAVEL_EVENT) {
            reinitViews(EventType.PAUSE_EVENT)
            stopAnimate()
            isPopUpWindowClosed.postValue(currEv)
            return
        }
        StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
        reinitViews(EventType.PAUSE_EVENT)
        isShowSpinnerDialog.value = EventType.PHONECALL_EVENT
        isPhoneButtonAnimate.value = Event(true)
        btnClickedTimestamp = System.currentTimeMillis()
    }

    fun clickEmail() {
        mp.start()
        if (isSyncAnimate.value == true) {
            Toast.makeText(getApplication(), R.string.sync_is_running, Toast.LENGTH_LONG).show()
            return
        }
        val currEv = StateRepository.getInstance().getEvent()
        StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
        task?.let {
            reloadTaskById(it.id)
        }
        if (currEv == EventType.PHONECALL_EVENT || currEv == EventType.EMAIL_EVENT || currEv == EventType.MEETING_EVENT || currEv == EventType.TRAVEL_EVENT) {
            reinitViews(EventType.PAUSE_EVENT)
            stopAnimate()
            isPopUpWindowClosed.postValue(currEv)
            return
        }
        StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
        reinitViews(EventType.PAUSE_EVENT)
        isShowSpinnerDialog.value = EventType.EMAIL_EVENT
        isEmailButtonAnimate.value = Event(true)
        btnClickedTimestamp = System.currentTimeMillis()
    }

    fun clickMeeting() {
        mp.start()
        if (isSyncAnimate.value == true) {
            Toast.makeText(getApplication(), R.string.sync_is_running, Toast.LENGTH_LONG).show()
            return
        }
        val currEv = StateRepository.getInstance().getEvent()
        StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
        task?.let {
            reloadTaskById(it.id)
        }
        if (currEv == EventType.PHONECALL_EVENT || currEv == EventType.EMAIL_EVENT || currEv == EventType.MEETING_EVENT || currEv == EventType.TRAVEL_EVENT) {
            reinitViews(EventType.PAUSE_EVENT)
            stopAnimate()
            isPopUpWindowClosed.postValue(currEv)
            return
        }
        StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
        reinitViews(EventType.PAUSE_EVENT)
        isShowSpinnerDialog.value = EventType.MEETING_EVENT
        isMeetingButtonAnimate.value = Event(true)
        btnClickedTimestamp = System.currentTimeMillis()
    }

    fun clickTravel() {
        mp.start()
        if (isSyncAnimate.value == true) {
            Toast.makeText(getApplication(), R.string.sync_is_running, Toast.LENGTH_LONG).show()
            return
        }
        val currEv = StateRepository.getInstance().getEvent()
        StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
        task?.let {
            reloadTaskById(it.id)
        }

        if (currEv == EventType.PHONECALL_EVENT || currEv == EventType.EMAIL_EVENT || currEv == EventType.MEETING_EVENT || currEv == EventType.TRAVEL_EVENT) {
            reinitViews(EventType.PAUSE_EVENT)
            stopAnimate()
            isPopUpWindowClosed.postValue(currEv)
            return
        }
        StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
        reinitViews(EventType.PAUSE_EVENT)
        isShowSpinnerDialog.value = EventType.TRAVEL_EVENT
        isTravellingButtonAnimate.value = Event(true)
        btnClickedTimestamp = System.currentTimeMillis()
    }

    fun stopAnimate() {
        if (isPhoneButtonAnimate.value?.peekContent() == true) {
            isPhoneButtonAnimate.value = Event(false)
        }
        if (isEmailButtonAnimate.value?.peekContent() == true) {
            isEmailButtonAnimate.value = Event(false)
        }
        if (isMeetingButtonAnimate.value?.peekContent() == true) {
            isMeetingButtonAnimate.value = Event(false)
        }
        if (isTravellingButtonAnimate.value?.peekContent() == true) {
            isTravellingButtonAnimate.value = Event(false)
        }
    }


    fun onButtonSyncClick() {
        if (isSyncAnimate.value == true) {
            Toast.makeText(getApplication(), R.string.sync_is_running, Toast.LENGTH_LONG).show()
            return
        }
        if (!isOnline(context)) {
            Toast.makeText(getApplication(), R.string.no_internet, Toast.LENGTH_LONG).show()
            return
        }
        RealmHandler.ioScope.launch {
            val prjNames = realmHandler.getAllProjectNamesFromDB()
            if (prjNames == null) {
                uiScope.launch {
                    Toast.makeText(getApplication(), "Error, do download and parse first", Toast.LENGTH_LONG).show()
                }
            } else {
                uiScope.launch {
                    isSyncAnimate.value = true
                    val savedState = StateRepository.getInstance().getEvent()
                    StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
                    RealmHandler.ioScope.launch {
                        sendToCloudMyProgress(
                                LocalUserInfo.getUserEmail(context),
                                isOnline(context),
                                LocalUserInfo.getUserDomain(context)
                        )
                        firestoreSync()
                        reloadTaskById(task?.id!!)
                        StateRepository.getInstance().setEvent(savedState)
                    }

                }
            }
        }
    }

    fun firestoreSync() {
        Log.d(LOG_TAG, "firestoreSync()")
        val userProjectID = LocalUserInfo.getUserDomain(context) ?: return
        val userEmail = LocalUserInfo.getUserEmail(context)
        val projectsNames = realmHandler.getAllProjectNamesFromDB() ?: return
        var hasError = false
        var projCounter = 0
        for (prjName in projectsNames) {
            FirebaseFirestore.getInstance(FirebaseApp.getInstance(userProjectID)).collection(prjName)
                    .get()
                    .addOnCompleteListener { task1 ->
                        if (task1.isSuccessful) {
                            RealmHandler.ioScope.launch {
                                projCounter++
                                val documentSnapshot = task1.result
                                val data = documentSnapshot?.documents
                                data?.let {
                                    RealmHandler.run { getInstance().handleRealmTransactions(context, userEmail, data) }
                                    if (projectsNames.size == projCounter) {
                                        autoUpdateMilestoneState()
                                        Log.d(LOG_TAG, "firestoreSyncResult result: ${!hasError} ")
                                        firestoreSyncResult.postValue(Event(!hasError))
                                    }
                                }
                            }
                        } else {
                            projCounter++
                            hasError = true
                        }
                    }
        }
    }


    fun firestoreSyncDone(event: Event<Boolean>) {
        Log.d(LOG_TAG, "firestoreSyncDone")
        event.getContentIfNotHandled()?.let {
            Log.d(LOG_TAG, "firestoreSyncDone result: $it ")
            if (it) {
                if (isOnline(getApplication())) {
                    RealmHandler.ioScope.launch {
                        val excelReportPath2File = writeXLSXFile(context)
                        excelReportPath2File?.let {
                            val result = uploadFileToDropbox(excelReportPath2File)
                            result?.let { s ->
                                isSyncAnimate.postValue(false)
                                snackbarMsg.postValue(Event(s))
                            } ?: run {
                                snackbarMsg.postValue(Event("Dropbox sync OK"))
                                isSyncAnimate.postValue(false)
                            }
                        } ?: run {
                            snackbarMsg.postValue(Event("Generate Excel error"))
                            isSyncAnimate.postValue(false)
                        }
                    }
                } else {
                    snackbarMsg.postValue(Event("No internet"))
                    isSyncAnimate.postValue(false)
                }
            }
        }
    }


    fun onSpinnerDialogSelected(projectName: String, uid: Long, event: EventType, context: Context) {
        RealmHandler.ioScope.launch {
            if (btnClickedTimestamp > 0) {
                val timePassed = System.currentTimeMillis() - btnClickedTimestamp
                StateRepository.getInstance().setDurationInCurrentPts(timePassed)
                RealmHandler.getInstance().addDurationTimePTSDB(context, projectName, uid, timePassed, false)
            }
        }
        if (btnClickedTimestamp > 0) {
            onSpinnerDialogSelected(projectName, uid, event)
            val timePassed = System.currentTimeMillis() - btnClickedTimestamp
            StateRepository.getInstance().setDurationInCurrentPts(timePassed)
            btnClickedTimestamp = 0
        } else {
            onSpinnerDialogSelected(projectName, uid, event)
            btnClickedTimestamp = 0
        }
    }

    fun onSpinnerDialogSelected(projectName: String, uid: Long, event: EventType) {
        Log.d(LOG_TAG, "onSpinnerDialogSelected($projectName, $uid, $event)")


        StateRepository.getInstance().currentPTS = CurrentPTS(projectName, uid, event)
        isShowSpinnerDialog.value = EventType.NONE
        reScheduleTimer()
        when (event) {
            EventType.PHONECALL_EVENT -> {
                isPhoneButtonAnimate.value = Event(true)
            }
            EventType.EMAIL_EVENT -> {
                isEmailButtonAnimate.value = Event(true)
            }
            EventType.MEETING_EVENT -> {
                isMeetingButtonAnimate.value = Event(true)
            }
            EventType.TRAVEL_EVENT -> {
                isTravellingButtonAnimate.value = Event(true)
            }
            else -> {
            }
        }
    }

    fun onSpinnerDialogShowed() {
        isShowSpinnerDialog.value = EventType.NONE
    }

    fun buttonStartTaskPressed() {
        task?.isPTScompleted?.let { completed -> if (completed) return }
        if (StateRepository.getInstance().getEvent() == EventType.START_EVENT) return
        Log.e(LOG_TAG, "buttonStartTaskPressed 1 Thread: ${Thread.currentThread().name}, id: ${Thread.currentThread().id}")
        reScheduleTimer()
        StateRepository.getInstance().currentPTS?.let {
            StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
        }
        task?.let {
            StateRepository.getInstance().currentPTS = CurrentPTS(it.projectName, it.uid)
            reinitViews(EventType.PAUSE_EVENT)
            StateRepository.getInstance().currentPTS?.let { currentPTS ->
                RealmHandler.ioScope.launch {
                    StateRepository.getInstance().setEvent(EventType.START_EVENT)
                    RealmHandler.getInstance().checkSetActualStart(context)
                    uiScope.launch {
                        startPressed.value = true
                        pausePressed.value = false
                        endPressed.value = false
                        isStartAnimate.value = Event(true)
                        isPauseAnimate.value = Event(false, true)
                        isEndAnimate.value = Event(false, true)
                        task?.let { task ->
                            reloadTaskById(task.id)
                        }
                    }
                }
            }
        }
    }

    fun buttonPauseTaskPressed() {
        if (checkSelfClickOrEnd() && StateRepository.getInstance().getEvent() != EventType.PAUSE_EVENT) {
            StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
            uiScope.launch {
                startPressed.value = false
                pausePressed.value = true
                endPressed.value = false
                isStartAnimate.value = Event(false, true)
                isEndAnimate.value = Event(false, true)
                isPauseAnimate.value = Event(true)
            }

        }
    }

    fun buttonEndTaskPressed() {
        if (!isCurrentTaskCompleted() && StateRepository.getInstance().getEvent() != EventType.END_EVENT) {
            isConfirmEnd.postValue(true)
        }
    }

    fun isConfirmEndPostFalse() {
        isConfirmEnd.postValue(false)
    }

    fun endConfirmed() {

        StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
        StateRepository.getInstance().currentPTS = task?.let { CurrentPTS(it.projectName, it.uid) }

        RealmHandler.ioScope.launch {
            StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
            uiScope.launch {
                reinitViews(EventType.PAUSE_EVENT)
            }
            StateRepository.getInstance().setEvent(EventType.END_EVENT)
            updateRemainingWorkAdditionalTime()
            autoUpdateMilestoneState()
            uiScope.launch {
                endPressed.value = true
                startPressed.value = false
                pausePressed.value = false
                isStartAnimate.value = Event(false, true)
                isPauseAnimate.value = Event(false, true)
                isEndAnimate.value = Event(true)
                isDone.value = true
                task?.let {
                    reloadTaskById(it.id)
                }
            }
        }
    }

    fun getAdditionalTimeForCurrentTask(): Double? {
        var additionalTime: Double? = null
        getCurrentPTSDB()?.let {
            additionalTime = it.taskAdditionalTime
        }
        return additionalTime
    }

    fun setAdditionalTime(time: Double?) {
        val tempTime = if (time == 0.0) null else time
        task?.let { pts ->
            pts.taskAdditionalTime = tempTime
            updateBudgetTime()
            RealmHandler.ioScope.launch {
                RealmHandler.getInstance().setAdditionalTime(pts.id, tempTime)
                uiScope.launch {
                    reloadTaskById(pts.id)
                }
            }
        }
    }

    fun checkForOverBudget(time: Double?, id: Long) {
        if (checkOverBudget(time)) {
            task?.let {
                if (it != null) {
                    it.isCheckOverBugetIsShown = true
                }
            }
            RealmHandler.ioScope.launch {
                if (!RealmHandler.getInstance().getTaskFromDb(id)!!.isCheckOverBugetIsShown) {
                    RealmHandler.getInstance().setIsOverBudgetIsShown(id, true)
                    isOverBudget.postValue(true)
                }
            }
        }
    }

    fun rollbackCompleted() {
        task?.isPTScompleted = false
        isConfirmEnd.postValue(false)
        RealmHandler.ioScope.launch {
            task?.let {
                RealmHandler.getInstance().savePTS(task!!)
            } ?: Log.e(LOG_TAG, "rollbackCompleted: task not found")
            uiScope.launch {
                StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
                reinitViews(EventType.PAUSE_EVENT)
            }
        }
        updateRemainingWorkAdditionalTime()
    }

    fun setNote(note: String) {
        val df = SimpleDateFormat("dd/MM/yyyy")
        df.format(Calendar.getInstance().time).toString()
        if (task?.taskNote == "" && task?.taskNote.isNullOrEmpty()) {
            task?.taskNote = task?.taskNote?.plus(df.format(Calendar.getInstance().time).toString() + ": " + note + "; ")
        } else {
            task?.taskNote = task?.taskNote?.plus(System.lineSeparator() + df.format(Calendar.getInstance().time).toString() + ": " + note + "; ")
        }
        RealmHandler.ioScope.launch {
            RealmHandler.getInstance().savePTS(task!!)
        }
    }

    private fun checkSelfClickOrEnd(): Boolean {
        if (isCurrentTaskCompleted()) {
            showToast(context.getString(R.string.task_already_ended), context)
            return false
        }
        return true
    }

    fun getSummaryString(): MutableLiveData<StringBuilder> {
        RealmHandler.ioScope.launch {
            val stringSummary = StringBuilder("Summary:\n")
            val projectNames = realmHandler.getAllProjectNamesFromDB()
            val projectNum = projectNames?.size
            val yourTasksNum = realmHandler.getAllYourTasksNum(context)
            val yourFinishedTasks = realmHandler.getAllTasksFinished(context)
            stringSummary.append("Total Projects: $projectNum \n")
            stringSummary.append("All Your Tasks: $yourTasksNum \n")
            stringSummary.append("Your Finished Tasks: $yourFinishedTasks")
            summary.postValue(stringSummary)
        }
        return summary
    }

    fun getSummaryStringTaskStart(): MutableLiveData<Pair<Int, Int>> {
        jobSummaryStringTaskStart = RealmHandler.ioScope.launch {
            Log.d(LOG_TAG, "taskYouCanStart processing...")
            var taskYouCanStart = realmHandler.getNumPossibleStartTask(context)
            currentPossibleTasks = taskYouCanStart
            summaryTasksStart.postValue(Pair(taskYouCanStart, taskYouCanStart - (prevPossibleTasks
                    ?: 0)))
        }
        return summaryTasksStart
    }

    fun getSummaryProjects(): MutableLiveData<Int> {
        RealmHandler.ioScope.launch {
            val projectNames = realmHandler.getAllProjectNamesFromDB()
            val projectNum = projectNames?.size
            summaryTotalProjects.postValue(projectNum)
        }
        return summaryTotalProjects
    }

    fun getSummaryAllYourTask(): MutableLiveData<Int> {
        RealmHandler.ioScope.launch {
            val yourTasksNum = realmHandler.getAllYourTasksNum(context)
            summaryAllYourTask.postValue(yourTasksNum)
        }
        return summaryAllYourTask
    }

    fun getSummaryFinishedTask(): MutableLiveData<Int> {
        RealmHandler.ioScope.launch {
            val yourFinishedTasks = realmHandler.getAllTasksFinished(context)
            summaryFinishedTask.postValue(yourFinishedTasks)
        }
        return summaryFinishedTask
    }

    fun updateGlobalClock() {
        RealmHandler.ioScope.launch(Dispatchers.IO) {
            globalMinutes.postValue(String.format("%s", calculateGlobalTime().subSequence(3, 5)))
            globalHours.postValue(String.format("%s", calculateGlobalTime().subSequence(0, 2)))
        }
    }

    fun updatePTSTimeProgress() {
        val currentEvent = StateRepository.getInstance().getEvent()
        if (currentEvent != EventType.PHONECALL_EVENT
                && currentEvent != EventType.EMAIL_EVENT
                && currentEvent != EventType.MEETING_EVENT
                && currentEvent != EventType.TRAVEL_EVENT
        ) {
            task?.let {
                RealmHandler.getInstance().getTaskFromDb(it.id)?.let { pts ->
                    val ptsProgress: Double = pts.ptSprogress ?: 0.0
                    val plusDurationTime = StateRepository.getInstance().currentPTS?.durationTime?.let { durationTime ->
                        durationTime * (timeKoeff) / 3600000.0
                    } ?: 0.0

                    currentTime.postValue(calculateTime(ptsProgress + plusDurationTime))
                    timeProgress.postValue(calculateTime(ptsProgress + plusDurationTime))
                    val remainingTimeStr =
                            pts.taskRemainingTime?.let { time -> calculateTime(time - plusDurationTime) }
                                    ?: ""

                    remainingTime.postValue(remainingTimeStr)
                    if (!task!!.isCheckOverBugetIsShown) {
                        checkForOverBudget(ptsProgress + plusDurationTime, pts.id)
                    }
                    updateBudgetTime()
                }
            }
        } else {
            task?.let {
                timeProgress.postValue(calculateTime(it.ptSprogress ?: 0.0))
                remainingTime.postValue(calculateTime(it.taskRemainingTime ?: 0.0))
                updateBudgetTime()
            }
        }
    }

    private fun updateBudgetTime() {
        var tempTimeBudget = ""
        task?.let { pts ->
            pts.timeBudget?.let {
                tempTimeBudget = "${calculateTime(it)}h"
                pts.taskAdditionalTime?.let { it1 ->
                    tempTimeBudget += " +${calculateTime(it1)}h"
                }
            }
        }
        timeBudget.postValue(tempTimeBudget)
    }

    fun calculateTime(currentEvent: EventType): String {
        StateRepository.getInstance().currentPTS?.let {
            var plusHRS = 0.0
            it.durationTime?.let { it1 ->
                plusHRS = it1.div(3600000.0 / timeKoeff)
                val ptsDb = realmHandler.getPTSbyUIDProjectNameUserNameComaEmail(
                        it.uid,
                        it.projectName, LocalUserInfo.getUserEmail(context).toString())

                if (ptsDb == null) {
                    StateRepository.getInstance().reset()
                    uiScope.launch(Dispatchers.Main) {
                        reinitViews(EventType.PAUSE_EVENT)
                    }
                    return "?"
                }

                val duration = if (ptsDb.ptSprogress == null) 0.0 else ptsDb.ptSprogress!!
                return calculateTime(duration + plusHRS)
            }
        } ?: return "?"
    }

    fun calculateGlobalTime(): String {
        var userActivity = realmHandler.getUserActivityByDay(true)
        var totalMilliseconds = 0L
        if (userActivity.isNotEmpty() || userActivity != null) {
            for (item in userActivity) {
                totalMilliseconds += item.eventDuration
            }
        }
        StateRepository.getInstance().currentPTS?.let {
            var plusHRS: Long
            it.durationTime?.let { it1 ->
                plusHRS = it1
                if (userActivity != null) {
                    if (userActivity.size == 0) {
                        if (StateRepository.getInstance().getEvent() == EventType.TRAVEL_EVENT
                                || StateRepository.getInstance().getEvent() == EventType.MEETING_EVENT
                                || StateRepository.getInstance().getEvent() == EventType.EMAIL_EVENT
                                || StateRepository.getInstance().getEvent() == EventType.PHONECALL_EVENT) {
                            val df = SimpleDateFormat("dd MMM yyy")
                            val date = Date(Calendar.getInstance().timeInMillis - totalMilliseconds - plusHRS)
                            val format = SimpleDateFormat("dd")
                            if (format.format(date).toInt() != Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) {
                                return Utils.calculateTimeForGlobal(Calendar.getInstance().timeInMillis.minus(LocalDate.now().toDateTimeAtStartOfDay().millis))
                            }
                        } else {
                            if (StateRepository.getInstance().getEvent() == EventType.NONE) {
                                return Utils.calculateTimeForGlobal(totalMilliseconds)
                            } else {
                                return Utils.calculateTimeForGlobal(Calendar.getInstance().timeInMillis.minus(LocalDate.now().toDateTimeAtStartOfDay().millis))
                            }
                        }
                    }
                    return Utils.calculateTimeForGlobal(totalMilliseconds + plusHRS)
                }
                return Utils.calculateTimeForGlobal(plusHRS)
            }
        } ?: return Utils.calculateTimeForGlobal(totalMilliseconds)
    }

    fun calculateTimeForCommunications(currentEvent: EventType): String {
        StateRepository.getInstance().currentPTS?.let {
            var plusHRS = 0.0
            it.durationTime?.let { it1 ->
                plusHRS = it1.div(3600000.0 / timeKoeff)
                val ptsDb = realmHandler.getPTSbyUIDProjectNameUserNameComaEmail(
                        it.uid,
                        it.projectName, LocalUserInfo.getUserEmail(context).toString())

                if (ptsDb == null) {
                    StateRepository.getInstance().reset()
                    uiScope.launch(Dispatchers.Main) {
                        reinitViews(EventType.PAUSE_EVENT)
                    }
                    return "?"
                }

                val duration = if (ptsDb.ptSprogress == null) 0.0 else ptsDb.ptSprogress!!
                return Utils.calculateTimeForCommunications(duration + plusHRS)
            }
        } ?: return "?"
    }

    fun isCurrentTaskCompleted(): Boolean {
        Log.d(LOG_TAG, "isCurrentTaskCompleted $uiDbyEvent")
        return task!!.isPTScompleted
    }

    private fun checkOverBudget(time: Double?): Boolean {
        if (task == null) return true
        val taskBudget = task?.timeBudget ?: 0.0
        if (task?.isCheckOverBugetIsShown == true) return false
        if (time != null) {
            return time > taskBudget && task?.taskNote!!.trim().isEmpty()
        } else {
            return false
        }
    }

    fun updateRemainingWorkAdditionalTime() {
        RealmHandler.ioScope.launch {
            updatePTSTimeProgress()
        }
    }

    fun reloadTaskById(id: Long) {
        RealmHandler.ioScope.launch {
            realmHandler.getTaskFromDb(id).let {
                task = it
                uiScope.launch(Dispatchers.Main) {
                    updatePTSTimeProgress()
                }
            }
        }
    }

    @Synchronized
    fun reScheduleTimer() {
        timer?.let {
            timer?.cancel()
            timer?.purge()
        }
        timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {
                uiScope.launch() {
                    val eventType = StateRepository.getInstance().getEvent()
                    withContext(Dispatchers.IO) {
                        if (eventType == EventType.PHONECALL_EVENT || eventType == EventType.EMAIL_EVENT ||
                                eventType == EventType.MEETING_EVENT || eventType == EventType.TRAVEL_EVENT) {
                            updatePhoneCallEmailMeetingDuration(eventType)
                            updateGlobalClock()
                        } else {
                            if (eventType == EventType.START_EVENT) {
                                updatePTSTimeProgress()
                                updateGlobalClock()
                            }
                        }
                    }
                }
            }
        }
        timer!!.schedule(timerTask, 0, TIMER_PERIOD)
    }

    fun jobsStop() {
        viewModelJob.cancel()
        jobSummaryStringTaskStart?.cancel()
    }

    fun updatePhoneCallEmailMeetingDuration(curEvent: EventType) {
        RealmHandler.ioScope.launch {
            val ct = calculateTimeForCommunications(curEvent)
            uiScope.launch {
                when (curEvent) {
                    EventType.PHONECALL_EVENT -> {
                        phoneTime.value = String.format("%s %s", ct, context.getString(R.string.hours))
                        emailTime.value = ""
                        meetingTime.value = ""
                        travelTime.value = ""
                        phoneProject.value = StateRepository.getInstance().currentPTS?.projectName
                        emailProject.value = ""
                        meetingProject.value = ""
                        travelProject.value = ""
                    }
                    EventType.EMAIL_EVENT -> {
                        emailTime.value = String.format("%s %s", ct, context.getString(R.string.hours))
                        phoneTime.value = ""
                        meetingTime.value = ""
                        travelTime.value = ""
                        phoneProject.value = ""
                        emailProject.value = StateRepository.getInstance().currentPTS?.projectName
                        meetingProject.value = ""
                        travelProject.value = ""
                    }
                    EventType.MEETING_EVENT -> {
                        meetingTime.value = String.format("%s %s", ct, context.getString(R.string.hours))
                        phoneTime.value = ""
                        emailTime.value = ""
                        travelTime.value = ""
                        phoneProject.value = ""
                        emailProject.value = ""
                        meetingProject.value = StateRepository.getInstance().currentPTS?.projectName
                        travelProject.value = ""
                    }
                    EventType.TRAVEL_EVENT -> {
                        meetingTime.value = ""
                        phoneTime.value = ""
                        emailTime.value = ""
                        travelTime.value = String.format("%s %s", ct, context.getString(R.string.hours))
                        phoneProject.value = ""
                        emailProject.value = ""
                        meetingProject.value = ""
                        travelProject.value = StateRepository.getInstance().currentPTS?.projectName
                    }
                    else -> {
                        phoneTime.value = ""
                        emailTime.value = ""
                        meetingTime.value = ""
                        travelTime.value = ""

                        phoneProject.value = ""
                        emailProject.value = ""
                        meetingProject.value = ""
                        travelProject.value = ""
                    }
                }
            }
        }
    }

    fun updatePhoneCallEmailMeetingDurationNotLoop(curEvent: EventType, durations: Double) {
        RealmHandler.ioScope.launch {
            StateRepository.getInstance().currentPTS?.let {
                it.durationTime?.let { it1 ->
                    val ptsDb = realmHandler.getPTSbyUIDProjectNameUserNameComaEmail(
                            it.uid,
                            it.projectName, LocalUserInfo.getUserEmail(context).toString()
                    )
                    if (ptsDb != null) {
                        realmHandler.addDurationTimePTSDBNotLoop(context, it.projectName, it.uid, durations)
                    }
                    realmHandler.setUserActivityByUIDProjectNameUserNameComaEmail(it.uid, it.projectName, "", "",
                            LocalUserInfo.getUserEmail(context).toString(), it1)
                }
                it.setDurationToNull()
            }
            uiScope.launch {
                when (curEvent) {
                    EventType.PHONECALL_EVENT -> {
                        phoneTime.value = String.format("%s %s", durations, context.getString(R.string.hours))
                        emailTime.value = ""
                        meetingTime.value = ""
                        travelTime.value = ""
                        phoneProject.value = StateRepository.getInstance().currentPTS?.projectName
                        emailProject.value = ""
                        meetingProject.value = ""
                        travelProject.value = ""
                    }
                    EventType.EMAIL_EVENT -> {
                        emailTime.value = String.format("%s %s", durations, context.getString(R.string.hours))
                        phoneTime.value = ""
                        meetingTime.value = ""
                        travelTime.value = ""
                        phoneProject.value = ""
                        emailProject.value = StateRepository.getInstance().currentPTS?.projectName
                        meetingProject.value = ""
                        travelProject.value = ""
                    }
                    EventType.MEETING_EVENT -> {
                        meetingTime.value = String.format("%s %s", durations, context.getString(R.string.hours))
                        phoneTime.value = ""
                        emailTime.value = ""
                        travelTime.value = ""
                        phoneProject.value = ""
                        emailProject.value = ""
                        meetingProject.value = StateRepository.getInstance().currentPTS?.projectName
                        travelProject.value = ""
                    }
                    EventType.TRAVEL_EVENT -> {
                        meetingTime.value = ""
                        phoneTime.value = ""
                        emailTime.value = ""
                        travelTime.value = String.format("%s %s", durations, context.getString(R.string.hours))
                        phoneProject.value = ""
                        emailProject.value = ""
                        meetingProject.value = ""
                        travelProject.value = StateRepository.getInstance().currentPTS?.projectName
                    }
                    else -> {
                        phoneTime.value = ""
                        emailTime.value = ""
                        meetingTime.value = ""
                        travelTime.value = ""

                        phoneProject.value = ""
                        emailProject.value = ""
                        meetingProject.value = ""
                        travelProject.value = ""
                    }
                }
            }
        }

    }

    fun getProgresss(): Double {
        var timeBuget = task?.ptSprogress ?: 0.0
        if (task == null) {
            RealmHandler.ioScope.launch {
                StateRepository.getInstance().currentPTS?.let {
                    timeBuget = RealmHandler.getInstance().getProgress(idOfTask)!!
                }
            }
        }
        return timeBuget
    }

    fun getProgress(): Double = task?.ptSprogress ?: 0.0

    fun getTaskRemainingTime(): Double? {
        var taskRemainingTime = task?.taskRemainingTime ?: 0.0
        if (getTaskCompleated()) return 0.0
        if (getTimeBuget() != null && getProgresss() != null) {
            var allTimeBudget: Double? = getTimeBuget()
            if (allTimeBudget != null) {
                if (getAddTime() != null) allTimeBudget += getAddTime()!!
            }
            taskRemainingTime = allTimeBudget!! - getProgresss()
        }
        return taskRemainingTime
    }

    fun setOverBugetFalse() {
        isOverBudget.postValue(false)
    }

    fun getTaskCompleated(): Boolean {
        var isCompleated = false
        RealmHandler.ioScope.launch {
            StateRepository.getInstance().currentPTS?.let {
                withContext(Dispatchers.IO) {
                    isCompleated = RealmHandler.getInstance().getIsCompleted(idOfTask)!!
                }
            }
        }
        return isCompleated
    }

    fun getTimeBuget(): Double {
        var timeBuget = task?.timeBudget ?: 0.0
        if (task == null) {
            RealmHandler.ioScope.launch(Dispatchers.Main) {
                StateRepository.getInstance().currentPTS?.let {
                    val timeBuget1 = RealmHandler.getInstance().getTimeBudget(idOfTask)
                    if (timeBuget1 == null) {
                        timeBuget = 0.0
                    } else {
                        timeBuget = timeBuget1
                    }
                }
            }
        }
        return timeBuget
    }

    fun getAddTime(): Double? {
        var additionalTime = task?.taskAdditionalTime ?: 0.0
        if (task == null) {
            RealmHandler.ioScope.launch(Dispatchers.Main) {
                StateRepository.getInstance().currentPTS?.let {
                    val additionalTime1 = RealmHandler.getInstance().getAdditionalTime(idOfTask)
                    additionalTime = if (additionalTime1 == null) {
                        Log.d(LOG_TAG, "additionalTime Card: $additionalTime")
                        0.0
                    } else {
                        additionalTime1
                    }
                }
            }
        }
        return additionalTime
    }

    fun setProgress(progress: Double?) {
        task?.let {
            it.setPTSProgress(progress)
            RealmHandler.ioScope.launch {
                RealmHandler.getInstance().savePTS(task!!)
            }
            reloadTaskById(it.id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
        timer?.cancel()
        timer = null
    }

    fun getCurrentPTSDB(): PTS_DB? = task

    fun restoreState(id: Long, eventType: String?, uidRestored: Long, projectNameRestored: String?, taskWorkingUsernameRestore: String, predecessorsIdsRestore: String) {
        val event = EventType.valueOf(eventType ?: EventType.NONE.name)
        RealmHandler.ioScope.launch(Dispatchers.Main) {
            realmHandler.getTaskFromDb(id).let {
                task = it
                uid.postValue(it?.uid.toString())
                projectName.postValue(it?.projectName)
                taskName.postValue(it?.taskName)
                stepName.postValue(it?.stepName)
                if (it?.taskWorkingUsername == "") {
                    taskWorkingUsername.postValue(taskWorkingUsernameRestore)
                } else {
                    taskWorkingUsername.postValue(it?.taskWorkingUsername)
                }
                updatePTSTimeProgress()
                updateBudgetTime()
                if (it?.taskDeadline == null) {
                    taskDeadLine.postValue("")
                } else {
                    taskDeadLine.postValue(it.taskDeadline.toString())
                }
                if (!it?.isRead!!) {
                    RealmHandler.getInstance().saveAsRead(id)
                    isRead.postValue(true)
                } else {
                    isRead.postValue(it.isRead)
                }
                if (it.isPTScompleted) {
                    isDone.postValue(true)
                    startPressed.postValue(false)
                    isStartAnimate.postValue(Event(content = false, alreadyHandled = true))
                    pausePressed.postValue(false)
                    isPauseAnimate.postValue(Event(content = false, alreadyHandled = true))
                    endPressed.postValue(true)
                    isEndAnimate.postValue(Event(content = true, alreadyHandled = true))
                } else {
                    isDone.postValue(false)
                    when (event) {
                        EventType.START_EVENT -> {
                            startPressed.postValue(true)
                            isStartAnimate.postValue(Event(content = true, alreadyHandled = true))
                            pausePressed.postValue(false)
                            isPauseAnimate.postValue(Event(content = false, alreadyHandled = true))
                            endPressed.postValue(false)
                            isEndAnimate.postValue(Event(content = false, alreadyHandled = true))
                        }
                        else -> {
                            startPressed.postValue(false)
                            isStartAnimate.postValue(Event(content = false, alreadyHandled = true))
                            pausePressed.postValue(true)
                            isPauseAnimate.postValue(Event(content = true, alreadyHandled = true))
                            endPressed.postValue(false)
                            isEndAnimate.postValue(Event(content = false, alreadyHandled = true))
                        }
                    }
                }
                if (event != EventType.PHONECALL_EVENT
                        && event != EventType.EMAIL_EVENT
                        && event != EventType.MEETING_EVENT
                        && event != EventType.TRAVEL_EVENT
                ) {
                    StateRepository.getInstance().currentPTS = it.let { it1 -> CurrentPTS(it1.projectName, it.uid) }
                } else {
                    Log.d(LOG_TAG, "restoreState: ${calculateTime(it.ptSprogress ?: 0.0)}")
                    timeProgress.postValue(calculateTime((it.ptSprogress ?: 0.0)))
                    remainingTime.postValue(calculateTime(it.taskRemainingTime ?: 0.0))
                    updateBudgetTime()
                }
            }
        }

        when (event) {
            EventType.PHONECALL_EVENT -> {
                onSpinnerDialogSelected(projectNameRestored!!, uidRestored, EventType.PHONECALL_EVENT)
            }
            EventType.MEETING_EVENT -> {
                onSpinnerDialogSelected(projectNameRestored!!, uidRestored, EventType.MEETING_EVENT)
            }
            EventType.EMAIL_EVENT -> {
                onSpinnerDialogSelected(projectNameRestored!!, uidRestored, EventType.EMAIL_EVENT)
            }
            EventType.TRAVEL_EVENT -> {
                onSpinnerDialogSelected(projectNameRestored!!, uidRestored, EventType.TRAVEL_EVENT)
            }
            EventType.START_EVENT -> {
            }
            else -> {
            }
        }
    }
}
package com.timejet.bio.timejet.ui.main

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
import com.timejet.bio.timejet.repository.*
import com.timejet.bio.timejet.repository.dropbox.uploadFileToDropbox
import com.timejet.bio.timejet.repository.models.PTS_DB
import com.timejet.bio.timejet.repository.parsers.ParserMPP
import com.timejet.bio.timejet.utils.Event
import com.timejet.bio.timejet.utils.LocalUserInfo
import com.timejet.bio.timejet.utils.Utils
import com.timejet.bio.timejet.utils.Utils.Companion.calculateTime
import com.timejet.bio.timejet.writeXLSXFile
import kotlinx.coroutines.*
import org.joda.time.LocalDate
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val LOG_TAG = this::class.java.simpleName

    private var context: Context = app.applicationContext
    private var dropboxRepository: DropboxRepository
    private var realmHandler : RealmHandler

    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    private val backgroundScope = CoroutineScope(Dispatchers.IO + viewModelJob)
    var ptsItems: MutableLiveData<List<PTS_DB>> = MutableLiveData()
    val allUsers: MutableLiveData<Boolean> = MutableLiveData()
    var isNotNavigated:Boolean = true
    private var timer: Timer? = null
    private lateinit var timerTask: TimerTask
    var mp = MediaPlayer.create(context, R.raw.clack)
    private val phoneTime = MutableLiveData<String>()
    private val emailTime = MutableLiveData<String>()
    private val meetingTime = MutableLiveData<String>()
    private val travelTime = MutableLiveData<String>()
    private var isPopUpWindowClosed = MutableLiveData<EventType>()
    private var isPopUpWindowOpend = MutableLiveData<Boolean>()
    private val isPhoneButtonAnimate = MutableLiveData<Event<Boolean>>()
    private val isEmailButtonAnimate = MutableLiveData<Event<Boolean>>()
    private val isMeetingButtonAnimate = MutableLiveData<Event<Boolean>>()
    private val isTravelButtonAnimate = MutableLiveData<Event<Boolean>>()
    private val isSyncAnimate = MutableLiveData<Boolean>()
    private val isShowSpinnerDialog = MutableLiveData<EventType>()
    private val firestoreSyncResult = MutableLiveData<Event<Boolean>>()
    private val snackbarMsg = MutableLiveData<Event<String>>()
    private var phoneProject = MutableLiveData<String>()
    private val isKeyboardIsShowen = MutableLiveData<Boolean>()
    private var emailProject = MutableLiveData<String>()
    private var meetingProject = MutableLiveData<String>()
    private var travelProject = MutableLiveData<String>()
    private var globalMinutes = MutableLiveData<String>()
    private var globalHours = MutableLiveData<String>()
    private val errors = MutableLiveData<Event<String>>()
    private val zeroFilesImport : MutableLiveData<Boolean> = MutableLiveData()
    private val parsedFilesNumber: MutableLiveData<Event<Int>> = MutableLiveData()
    private val isLoading: MutableLiveData<Boolean> = MutableLiveData()
    val refreshMainFragment : MutableLiveData<Boolean> = MutableLiveData()

    private var searchByProject = ""
    fun setProjectSearch(searchByProject:String){
        this.searchByProject=searchByProject
    }
    private var searchByUser = ""
    fun setUserSearch(searchByUser:String){
        this.searchByUser=searchByUser
    }
    private var searchByTask = ""
    fun setTaskNameSearch(searchByTask:String){
        this.searchByTask=searchByTask
    }
    private var searchByStep = ""
    fun setStepName(searchByStep:String){
        this.searchByStep=searchByStep
    }
    private var searchByProgress = ""
    fun setProgress(searchByProgress:String){
        this.searchByProgress=searchByProgress
    }
    private var orderByDeadline = false
    fun setOrderByDeadline(order: Boolean) {
        this.orderByDeadline = order
    }
    fun getOrderByDeadline(): Boolean {
        return this.orderByDeadline
    }
    private var btnClickedTimestamp: Long = 0

    fun phoneTime(): LiveData<String> = phoneTime
    fun emailTime(): LiveData<String> = emailTime
    fun meetingTime(): LiveData<String> = meetingTime
    fun travelTime(): LiveData<String> = travelTime
    fun isPhoneButtonAnimate(): LiveData<Event<Boolean>> = isPhoneButtonAnimate
    fun isPopUpWindowClosed(): LiveData<EventType> = isPopUpWindowClosed
    fun isPopUpWindowOpend(): LiveData<Boolean> = isPopUpWindowOpend
    fun isEmailButtonAnimate(): LiveData<Event<Boolean>> = isEmailButtonAnimate
    fun isMeetingButtonAnimate(): LiveData<Event<Boolean>> = isMeetingButtonAnimate
    fun isTravelButtonAnimate(): LiveData<Event<Boolean>> = isTravelButtonAnimate
    fun isSyncAnimate(): LiveData<Boolean> = isSyncAnimate
    fun isShowSpinnerDialog(): LiveData<EventType> = isShowSpinnerDialog
    fun firestoreSyncResult(): LiveData<Event<Boolean>> = firestoreSyncResult
    fun snackbarMsg(): LiveData<Event<String>> = snackbarMsg
    fun globalMinutes(): LiveData<String> = globalMinutes
    fun globalHours(): LiveData<String> = globalHours
    fun isAllUsers() : LiveData<Boolean> = allUsers
    fun isKeyboardIsShowen() : LiveData<Boolean> = isKeyboardIsShowen
    fun errors() : LiveData<Event<String>> = errors
    fun getZeroFilesImport() : LiveData<Boolean> = zeroFilesImport
    fun parsedFilesNumber(): LiveData<Event<Int>> = parsedFilesNumber
    fun isLoading() : LiveData<Boolean> = isLoading
    fun refreshMainFragment() : MutableLiveData<Boolean> = refreshMainFragment

    init {
        dropboxRepository = DropboxRepository.getInstance(getApplication())
        realmHandler = RealmHandler.getInstance()
        allUsers.value = false
        zeroFilesImport.value = false
        isLoading.value = false
        Log.e(LOG_TAG, "INIT MAIN VIEW MODEL Thread: Thread: ${Thread.currentThread().name}")
        getPtsItems()
        reScheduleTimer()
        restoreView()
        isShowSpinnerDialog.value = EventType.NONE
        isPopUpWindowOpend.postValue(true)
        firestoreSyncResult.observeForever {
            firestoreSyncDone(it)
        }
    }

    fun clickGetProjects() {
        getFilesFromDropbox()
    }

    fun getFilesFromDropbox() {
        isLoading.value = true
        RealmHandler.ioScope.launch {
            Log.d(LOG_TAG, "dropboxRepository.getFilesFromDropbox")
            if(dropboxRepository.getFilesFromDropbox()) {
                realmHandler.deleteDB(PTS_DB::class.java)
                val parseCounter = ParserMPP.importPTS(getApplication())
                if(parseCounter == 0) {
                    Log.d(LOG_TAG, "importPTS : $parseCounter")
                    zeroFilesImport.postValue(true)
                } else {
                    Utils.deleteAllMPPFiles(getApplication())
                    uiScope.launch {
                        ParserMPP.getAllCloudTasks(getApplication()).observeForever {

                            if(it) {
                                parsedFilesNumber.postValue(Event(parseCounter))
                                refreshMainFragment.value = true
                                isLoading.value = false
                            }
                        }
                    }
                }
            } else {
                uiScope.launch {
                    isLoading.value = false
                    zeroFilesImport.value = true
                }
            }
        }
    }

    fun clickPhone() {
        mp.start()
        if (isSyncAnimate.value == true) {
            Toast.makeText(getApplication(), R.string.sync_is_running, Toast.LENGTH_LONG).show()
            return
        }
        val currEv = StateRepository.getInstance().getEvent()
        if (currEv == EventType.PHONECALL_EVENT || currEv == EventType.EMAIL_EVENT ||
                currEv == EventType.MEETING_EVENT || currEv == EventType.TRAVEL_EVENT) {
            StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
            stopAnimate()
            isPopUpWindowClosed.postValue(currEv)
            return
        }
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
        if (currEv == EventType.PHONECALL_EVENT || currEv == EventType.EMAIL_EVENT || currEv == EventType.MEETING_EVENT || currEv == EventType.TRAVEL_EVENT) {
            StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
            isPopUpWindowClosed.postValue(currEv)
            stopAnimate()
            return
        }
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
        if (currEv == EventType.PHONECALL_EVENT || currEv == EventType.EMAIL_EVENT || currEv == EventType.MEETING_EVENT || currEv == EventType.TRAVEL_EVENT) {
            StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
            isPopUpWindowClosed.postValue(currEv)
            stopAnimate()
            return
        }
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
        if (currEv == EventType.PHONECALL_EVENT || currEv == EventType.EMAIL_EVENT || currEv == EventType.MEETING_EVENT || currEv == EventType.TRAVEL_EVENT) {
            StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
            isPopUpWindowClosed.postValue(currEv)
            stopAnimate()
            return
        }
        isShowSpinnerDialog.value = EventType.TRAVEL_EVENT
        isTravelButtonAnimate.value = Event(true)
        btnClickedTimestamp = System.currentTimeMillis()
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
        StateRepository.getInstance().currentPTS = CurrentPTS(projectName, uid, event)
        isShowSpinnerDialog.value = EventType.NONE
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
                isTravelButtonAnimate.value = Event(true)
            }
        }
        updatePhoneCallEmailMeetingDuration(event)
    }

    fun onSpinnerDialogShowed() {
        isShowSpinnerDialog.value = EventType.NONE
    }

    fun stopAnimate() {
        if (isPhoneButtonAnimate.value?.peekContent() == true) { isPhoneButtonAnimate.value = Event(false)}
        if (isEmailButtonAnimate.value?.peekContent() == true) { isEmailButtonAnimate.value = Event(false)}
        if (isMeetingButtonAnimate.value?.peekContent() == true) { isMeetingButtonAnimate.value = Event(false)}
        updatePhoneCallEmailMeetingDuration(StateRepository.getInstance().getEvent())
    }

    fun onButtonSyncClick() {
        if (isSyncAnimate.value == true) {
            Toast.makeText(getApplication(), "Sync is running!", Toast.LENGTH_LONG).show()
            return
        }
        if (!isOnline(context)) {
            Toast.makeText(getApplication(), "No internet", Toast.LENGTH_LONG).show()
            return
        }
        val savedEvent = StateRepository.getInstance().getEvent()
        StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
        RealmHandler.ioScope.launch {
            val prjNames = realmHandler.getAllProjectNamesFromDB()
            if (prjNames == null) {
                uiScope.launch {
                    Toast.makeText(getApplication(), "Error, do download and parse first", Toast.LENGTH_LONG).show()
                }
            } else {
                uiScope.launch {
                    isSyncAnimate.value = true
//                    when (StateRepository.getInstance().getEvent()) {
//                        EventType.PHONECALL_EVENT -> {
//                            isPhoneButtonAnimate.value = Event(false)
//                        }
//                        EventType.EMAIL_EVENT -> {
//                            isEmailButtonAnimate.value = Event(false)
//                        }
//                        EventType.MEETING_EVENT -> {
//                            isMeetingButtonAnimate.value = Event(false)
//                        }
//                        else -> { }
//                    }

                    StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
                    RealmHandler.ioScope.launch {
                        sendToCloudMyProgress(LocalUserInfo.getUserEmail(context), isOnline(context), LocalUserInfo.getUserDomain(context))
                        firestoreSync()
                        StateRepository.getInstance().setEvent(savedEvent)
                    }
                }
            }
        }
    }

    fun getPtsItems() {
        if (deleteAll == 1) return
        val allUsersValue = allUsers.value ?: false
        Log.e(LOG_TAG, "getPtsItems START")
        RealmHandler.ioScope.launch() {
            withContext(Dispatchers.IO) {
                realmHandler.getFromRealm(allUsersValue, searchByProgress, searchByUser, searchByProject, searchByTask, searchByStep, orderByDeadline).let { realmResults ->
                    realmResults.let {
                        it.forEach { ff ->
                            Log.e(LOG_TAG, "getPtsITEMS progress: ${ff.ptSprogress} , name: ${ff.stepName} , Thread: ${Thread.currentThread().name}, id: ${Thread.currentThread().id}")
                        }
                        ptsItems.postValue(it)
                    }
                }
            }
        }
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
                        restoreView()
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
            var plusHRS = 0L
            it.durationTime?.let{ it1 ->
                plusHRS = it1
                if (userActivity != null){
                    if(userActivity.size == 0){
                        if(StateRepository.getInstance().getEvent() == EventType.TRAVEL_EVENT
                                || StateRepository.getInstance().getEvent() == EventType.MEETING_EVENT
                                || StateRepository.getInstance().getEvent() == EventType.EMAIL_EVENT
                                || StateRepository.getInstance().getEvent() == EventType.PHONECALL_EVENT){
                            val df = SimpleDateFormat("dd MMM yyy")
                            val date = Date(Calendar.getInstance().timeInMillis - totalMilliseconds - plusHRS)
                            val format = SimpleDateFormat("dd")
                            if(format.format(date).toInt() != Calendar.getInstance().get(Calendar.DAY_OF_MONTH)){
                                return Utils.calculateTimeForGlobal(Calendar.getInstance().timeInMillis.minus(LocalDate.now().toDateTimeAtStartOfDay().millis))
                            }
                        } else {
                            if(StateRepository.getInstance().getEvent() == EventType.NONE) {
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
                plusHRS = it1.div(3600000.0)
                val ptsDb = realmHandler.getPTSbyUIDProjectNameUserNameComaEmail(
                        it.uid,
                        it.projectName, LocalUserInfo.getUserEmail(context).toString())

                if (ptsDb == null) {
                    StateRepository.getInstance().reset()
                    uiScope.launch(Dispatchers.Main) {
                        restoreView()
                    }
                    return "?"
                }

                val duration = if (ptsDb.ptSprogress == null) 0.0 else ptsDb.ptSprogress!!
                return Utils.calculateTimeForCommunications(duration + plusHRS)
            }
        } ?: return "?"
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
                                RealmHandler.getInstance().handleRealmTransactions(context, userEmail, data)
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
                            }?: run {
                                snackbarMsg.postValue(Event("Dropbox sync OK"))
                                isSyncAnimate.postValue(false)
                            }
                        }?: run {
                            snackbarMsg.postValue(Event("Generate Excel error"))
                            isSyncAnimate.postValue(false)
                        }
                    }
                } else {
                    snackbarMsg.postValue(Event("No internet"))
                    isSyncAnimate.postValue(false)
                }
                uiScope.launch {
                    getPtsItems()
                }
            }
        }
    }

    fun isNotYourTask(position: Int) : Boolean {
        val ptsItems2 = ptsItems.value
        val uidDocument = ptsItems2!![position].uid
        val prjName = ptsItems2[position].projectName
        val userEmail = LocalUserInfo.getUserEmail(context)!!.toLowerCase()
//        val ptsDb = realmHandler.getPTSbyUIDProjectNameUserNameComaEmail(uidDocument, prjName, userEmail)
        val ptsDb = realmHandler.getTaskByUidAndProjectName(uidDocument, prjName)
        return ptsDb == null || !ptsDb.usersAssigned.contains(userEmail)
    }

    fun isNotAllPredecessorsAreFinished(position: Int) : Boolean {
        val ptsItems2 = ptsItems.value
        val uidDocument = ptsItems2!![position].uid
        val prjName = ptsItems2[position].projectName
        val userEmail = LocalUserInfo.getUserEmail(context)!!.toLowerCase()
//        val ptsDb = realmHandler.getPTSbyUIDProjectNameUserNameComaEmail(uidDocument, prjName, userEmail)
        val ptsDb = realmHandler.getTaskByUidAndProjectName(uidDocument, prjName)
        var allFinished = true
        val predecessorsUIDList = ptsDb?.predecessorsIDlist
        predecessorsUIDList?.let {
            val stringBuilder = RealmHandler.getInstance().getAllSublist(it, prjName)
            if (stringBuilder.toString().contains("[N]")) allFinished = false
        }
        return !allFinished
    }

    fun setShowAllUsers(checked:Boolean) {
        Log.d(LOG_TAG, "setShowAllUsers: $checked")
        allUsers.value = checked
        getPtsItems()
    }


    @Synchronized
    fun reScheduleTimer() {
        timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {
                StateRepository.getInstance().getEvent().let {
                    backgroundScope.launch {
                        when (it) {
                            EventType.EMAIL_EVENT, EventType.PHONECALL_EVENT, EventType.MEETING_EVENT, EventType.TRAVEL_EVENT -> {
                                updatePhoneCallEmailMeetingDuration(StateRepository.getInstance().getEvent())
                                isKeyboardIsShowen.postValue(true)
                            }
                            else -> {
                            }
                        }
                        updateGlobalClock()
                    }
                }
            }
        }
        timer!!.schedule(timerTask, 0, 1000)
    }

    fun timerStop() {
        timer?.cancel()
        timer = null
    }

    fun updateGlobalClock() {
        RealmHandler.ioScope.launch {
            var ct = calculateGlobalTime()
            uiScope.launch {
                globalMinutes.value = String.format("%s", ct.subSequence(3,5))
                globalHours.value = String.format("%s", ct.subSequence(0,2))
            }
        }
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
                    Log.d(LOG_TAG, "updatePhoneCallEmailMeetingDurationNotLoop() ${it1}")
                    if (ptsDb != null) {
                        RealmHandler.getInstance().addDurationTimePTSDBNotLoop(context, it.projectName, it.uid, durations)
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



    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
        timer?.cancel()
        timer = null
    }

    fun buttonPauseTaskPressed() {
        Log.d(LOG_TAG, "buttonPauseTaskPressed()")
        RealmHandler.ioScope.launch {
            if (StateRepository.getInstance().getEvent() != EventType.PAUSE_EVENT) {
                uiScope.launch {
                    restoreView(EventType.PAUSE_EVENT)
                }
                StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
            }
        }
    }

    fun restoreView(event: EventType) {
        when(event){
            EventType.PAUSE_EVENT -> {
                isPhoneButtonAnimate.value = Event(false, true)
                isEmailButtonAnimate.value = Event(false, true)
                isMeetingButtonAnimate.value = Event(false, true)
                isTravelButtonAnimate.value = Event(false, true)
                updatePhoneCallEmailMeetingDuration(EventType.NONE)

            }
        }
    }


    fun restoreView() {
        when (StateRepository.getInstance().getEvent()) {
            EventType.PHONECALL_EVENT -> {
                isPhoneButtonAnimate.value = Event(true, true)
                isEmailButtonAnimate.value = Event(false, true)
                isMeetingButtonAnimate.value = Event(false, true)
                isTravelButtonAnimate.value = Event(false, true)
            }
            EventType.EMAIL_EVENT -> {
                isPhoneButtonAnimate.value = Event(false, true)
                isEmailButtonAnimate.value = Event(true, true)
                isMeetingButtonAnimate.value = Event(false, true)
                isTravelButtonAnimate.value = Event(false, true)
            }
            EventType.MEETING_EVENT -> {
                isPhoneButtonAnimate.value = Event(false, true)
                isEmailButtonAnimate.value = Event(false, true)
                isMeetingButtonAnimate.value = Event(true, true)
                isTravelButtonAnimate.value = Event(false, true)
            }
            EventType.TRAVEL_EVENT -> {
                isPhoneButtonAnimate.value = Event(false, true)
                isEmailButtonAnimate.value = Event(false, true)
                isMeetingButtonAnimate.value = Event(false, true)
                isTravelButtonAnimate.value = Event(true, true)
            }
            else -> {
                isPhoneButtonAnimate.value = Event(false, true)
                isEmailButtonAnimate.value = Event(false, true)
                isMeetingButtonAnimate.value = Event(false, true)
                isTravelButtonAnimate.value = Event(false, true)
                phoneProject.value = ""
                emailProject.value = ""
                meetingProject.value = ""
                travelProject.value = ""
                phoneTime.value = ""
                emailTime.value = ""
                meetingTime.value = ""
                travelTime.value = ""
            }
        }
        updatePhoneCallEmailMeetingDuration(StateRepository.getInstance().getEvent())
    }
}
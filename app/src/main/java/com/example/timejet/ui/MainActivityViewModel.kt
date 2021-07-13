package com.timejet.bio.timejet.ui

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.example.timejet.repository.models.PTS_DB
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.timejet.bio.timejet.repository.DropboxRepository
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.repository.databases.onlineDB.FirebaseOnlineDB
import com.timejet.bio.timejet.repository.parsers.ParserMPP
import com.timejet.bio.timejet.repository.parsers.ParserMPP.getAllCloudTasks
import com.timejet.bio.timejet.ui.main.autoUpdateMilestoneState
import com.timejet.bio.timejet.ui.main.isOnline
import com.timejet.bio.timejet.ui.main.sendToCloudMyProgress
import com.timejet.bio.timejet.utils.Event
import com.timejet.bio.timejet.utils.LocalUserInfo
import com.timejet.bio.timejet.utils.Utils
import com.timejet.bio.timejet.utils.Utils.Companion.deleteAllMPPFiles
import kotlinx.coroutines.*

class MainActivityViewModel(app: Application, private val savedStateHandler: SavedStateHandle) : AndroidViewModel(app) {
    private val LOG_TAG = this::class.java.simpleName
    private val AFTER_RESTART_KEY = "after_restart_key"

    //val afterRestart : LiveData<Boolean> = savedStateHandler.getLiveData(AFTER_RESTART_KEY)
    private var afterRestart:MutableLiveData<Boolean> = MutableLiveData()
    val navigatoToLogin : MutableLiveData<Boolean> = MutableLiveData()
    val refreshMainFragment : MutableLiveData<Boolean> = MutableLiveData()
    private val isLoading: MutableLiveData<Boolean> = MutableLiveData<Boolean>()
    val parsedFilesNumber: MutableLiveData<Event<Int>> = MutableLiveData()
    private val zeroFilesImport : MutableLiveData<Boolean> = MutableLiveData()
    private val firestoreSyncResult = MutableLiveData<Event<Boolean>>()

    fun firestoreSyncResult(): LiveData<Event<Boolean>> = firestoreSyncResult

    var realmHandler:RealmHandler
    var dropboxRepository:DropboxRepository
    private var context:Context
    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    var restarted: Boolean

    fun getZeroFilesImport() : LiveData<Boolean> = zeroFilesImport

    init {
        context = app.applicationContext
        dropboxRepository = DropboxRepository.getInstance(context as Application)
        realmHandler = RealmHandler.getInstance()
        isLoading.value = false
        zeroFilesImport.value = false
        restarted = false
    }

    fun afterRestart():LiveData<Boolean> {
        afterRestart.value =  true
        return afterRestart
    }

    fun saveAfterRestart(state:Boolean) {
        Log.d(LOG_TAG, "saveAfterRestart(): $state")
        savedStateHandler.set(AFTER_RESTART_KEY, state)
    }

    fun getAllProjectsMPPClick() {
        getFilesFromDropbox()
    }

    fun isLoading() : LiveData<Boolean> = isLoading

    fun syncTaskStateWithFirebase() {
        Log.d(LOG_TAG,"SyncTaskStateWithFirebase running....")
        getAllCloudTasks(getApplication())
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
                    deleteAllMPPFiles(getApplication())
                    uiScope.launch {
                        getAllCloudTasks(getApplication()).observeForever {
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

    fun onButtonSyncClick() {
        if (isLoading.value == true) {
            Toast.makeText(getApplication(), "Sync is running!", Toast.LENGTH_LONG).show()
            return
        }
        if (!isOnline(context)) {
            Toast.makeText(getApplication(), "No internet", Toast.LENGTH_LONG).show()
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
                    isLoading.value = true
                    RealmHandler.ioScope.launch {
                        sendToCloudMyProgress(LocalUserInfo.getUserEmail(context), isOnline(context), LocalUserInfo.getUserDomain(context))
                        firestoreSync()
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
                                RealmHandler.getInstance().handleRealmTransactions(context, userEmail, data)
                                if (projectsNames.size == projCounter) {
                                    autoUpdateMilestoneState()
                                    Log.d(LOG_TAG, "firestoreSyncResult result: ${!hasError} ")
                                    firestoreSyncResult.postValue(Event(!hasError))
                                    isLoading.postValue(false)
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

    fun logout() {
        FirebaseAuth.getInstance(FirebaseOnlineDB(context).firebaseApp!!).signOut()
        Utils.deleteLocalFile("google-services.json", context)
        navigatoToLogin.value = true
    }
}
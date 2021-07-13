package com.timejet.bio.timejet.ui.login

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.FileAsyncHttpResponseHandler
import com.timejet.bio.timejet.repository.databases.localDB.showToast
import com.timejet.bio.timejet.repository.databases.onlineDB.FirebaseOnlineDB
import com.timejet.bio.timejet.utils.LocalUserInfo
import com.timejet.bio.timejet.utils.Utils
import org.apache.http.Header
import java.io.File

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var enteredEmail: String
    private lateinit var enteredPassowrd: String

    val isLoading: MutableLiveData<Boolean> = MutableLiveData<Boolean>()
    val showProgressBar: MutableLiveData<Boolean> = MutableLiveData<Boolean>()
    val isAnimationPlaying: MutableLiveData<Boolean> = MutableLiveData<Boolean>()

    fun showProgressBar(): LiveData<Boolean> = showProgressBar
    fun loginAttemptResult(): LiveData<Boolean> = loginAttemptResult

    var loginAttemptResult: MutableLiveData<Boolean> = MutableLiveData<Boolean>().also {
        it.value = false
    }

    var configFetchFail: MutableLiveData<Pair<String, String>> = MutableLiveData<Pair<String, String>>().also {
        it.value = Pair("", "")
    }

    var configFetchSuccess: MutableLiveData<Pair<String, File?>> = MutableLiveData<Pair<String, File?>>().also {
        it.value = Pair("", null)
    }

    val firebaseApp: MutableLiveData<FirebaseApp> by lazy {
        MutableLiveData<FirebaseApp>().also {
            it.value = FirebaseOnlineDB(getApplication()).firebaseApp
        }
    }

    val userEmail: MutableLiveData<String> by lazy {
        MutableLiveData<String>().also {
            it.value = LocalUserInfo.getUserEmail(getApplication())
        }
    }

    val configFilesUrl: MutableLiveData<String> by lazy {
        MutableLiveData<String>().also {
            it.value = LocalUserInfo.getConfigFilesUrl(getApplication())
        }
    }

    val userGroup: MutableLiveData<String> by lazy {
        MutableLiveData<String>().also {
            it.value = LocalUserInfo.getUserGroup(getApplication())
        }
    }

    init {
        isLoading.postValue(false)
        isAnimationPlaying.postValue(false)
    }

    fun attemptLogin(email: String, password: String) {
        enteredEmail = email
        enteredPassowrd = password
        isLoading.postValue(true)
        if (FirebaseOnlineDB(getApplication()).attemptToInitCloudIsSuccess(email, password))
            login(email, password)
        else {
            getConfigFile(getApplication())
        }
    }

    init {
        val onlineDB = FirebaseOnlineDB(getApplication())
        try {
            onlineDB.attemptToInitCloudIsSuccess("", "")
        } catch (e: Exception) {
        }
    }

    fun login(email: String, password: String) {
        if (password.isNotEmpty()) {
            val firebaseAuth: FirebaseAuth?
            val onlineFirestore: FirebaseFirestore?
            val projectID = Utils.getProjectID(getApplication())
            val firebaseApp = FirebaseApp.getInstance(projectID!!)
            firebaseAuth = FirebaseAuth.getInstance(firebaseApp)
            onlineFirestore = FirebaseFirestore.getInstance(firebaseApp)
            if (onlineFirestore != null) {
                firebaseAuth!!.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            isLoading.postValue(false)
                            if (task.isSuccessful) {
                                val idToken = task.result!!.user!!.getIdToken(false).result!!.token.toString()
                                Utils.saveTokenFirebase(getApplication(), idToken)
                                loginAttemptResult.value = true
                            }
                        }
                        .addOnFailureListener { e ->
                            isLoading.postValue(false)
                            if (e.message != null) showToast(e.message!!, getApplication())
                        }
            }
        } else {
            isLoading.postValue(false)
        }
    }

    @Throws(Exception::class)
    fun getConfigFile(applicationContext: Context) {
        val configFile: String? = Utils.getConfigFilesUrl(applicationContext)
        if (!configFile.isNullOrEmpty()) {
            val url: String? = "https://$configFile"
            val client = AsyncHttpClient()
            Utils.deleteLocalFile(applicationContext, "config.zip")
            val file = File(applicationContext.filesDir, "config.zip")
            client.get(applicationContext, url, object : FileAsyncHttpResponseHandler(file) {
                override fun onFailure(statusCode: Int, headers: Array<out Header>?, throwable: Throwable?, file: File?) {
                    isLoading.postValue(false)
                    if (throwable?.message != null)
                        configFetchFail.value = Pair("get_config_fail", throwable.message!!)
                }

                override fun onSuccess(statusCode: Int, headers: Array<out Header>?, file: File?) {
                    configFetchSuccess.value = Pair("get_config_ok", file)
                    if (FirebaseOnlineDB(getApplication()).attemptToInitCloudIsSuccess(enteredEmail, enteredPassowrd))
                        login(enteredEmail, enteredPassowrd)
                }
            })
        } else {
            isLoading.postValue(false)
        }
    }
}
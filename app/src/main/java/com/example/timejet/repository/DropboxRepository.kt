package com.timejet.bio.timejet.repository

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2

import com.timejet.bio.timejet.ui.main.*
import com.timejet.bio.timejet.utils.LocalUserInfo
import com.timejet.bio.timejet.utils.Utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

const val TOKEN_FAILED = 0
const val TOKEN_EMPTY = 1
const val TOKEN_OK = 2

class DropboxRepository(app: Application) {
    private val LOG_TAG = DropboxRepository::class.java.simpleName
    private val app: Application

    init {
        this.app = app
    }
    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    var token: MutableLiveData<Int> = MutableLiveData()

    fun getTokenChangeObservable(): MutableLiveData<Int> {
        return token
    }

    fun getFilesFromDropbox() : Boolean{
        if (!isOnline(app)) return false
        if (!checkToken()) {
            uiScope.launch(Dispatchers.Main) {
                Toast.makeText(app, "Dropbox Token FAIL,\nRelogin", Toast.LENGTH_LONG).show()
            }
            return false
        }

        try {
            val dbxClientV2 = authDropbox()
            val group = LocalUserInfo.getUserGroup(app)
            val listFiles = dbxClientV2.files().listFolder("//input_tasks//" + group!!)
            for (metadata in listFiles.entries) {
                val name = metadata.name
                if (!name.contains(".mpp")) continue

                val file = File(app.filesDir, name)
                val downloadFile = FileOutputStream(file)

                dbxClientV2.files().downloadBuilder(metadata.pathDisplay).download(downloadFile)
            }
        } catch (e: Exception) {
            Log.d(LOG_TAG, e.message)
            return false
        }
        return true
    }

    @Throws(Exception::class)
    private fun authDropbox(): DbxClientV2 {
        val authAccessToken: String? = Utils.getTokenDropbox(app)
        val requestConfig: DbxRequestConfig? = DbxRequestConfig("TJ/0.1")
        return DbxClientV2(requestConfig, authAccessToken)
    }

    private fun checkToken(): Boolean {
        val tokenDropbox = Utils.getTokenDropbox(app)
        if (tokenDropbox.isNullOrEmpty()) {
            uiScope.launch(Dispatchers.Main) {
                Toast.makeText(app, "Dropbox Token is Empty", Toast.LENGTH_LONG).show()
            }
            return false
        }
        return true
    }

    companion object {
        private var INSTANCE:DropboxRepository? = null
        fun getInstance(app:Application):DropboxRepository {
            if(INSTANCE == null) {
                INSTANCE = DropboxRepository(app)
            }
            return INSTANCE as DropboxRepository
        }
    }

}
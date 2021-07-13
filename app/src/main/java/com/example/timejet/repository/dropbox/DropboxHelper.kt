package com.timejet.bio.timejet.repository.dropbox

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import android.view.View
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import com.example.timejet.repository.models.PTS_DB
import com.google.android.material.snackbar.Snackbar
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.ui.main.LOG_TAG
import com.timejet.bio.timejet.ui.main.appContext

import com.timejet.bio.timejet.utils.LocalUserInfo
import com.timejet.bio.timejet.utils.Utils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Throws(Exception::class)
fun authDropbox(): DbxClientV2 {
    val authAccessToken: String = Utils.getTokenDropbox(appContext)!!.trim()
    val requestConfig: DbxRequestConfig = DbxRequestConfig("TJ/0.1")
    val dbxClientV2 = DbxClientV2(requestConfig, authAccessToken)
    return dbxClientV2
}

fun checkToken(appView: View): Boolean {
    val tokenDropbox = Utils.getTokenDropbox(appContext)
    if (tokenDropbox.isNullOrEmpty()) {
        Snackbar.make(appView, "Dropbox Token is Empty", Snackbar.LENGTH_LONG).show()
        return false
    }
    return true
}

fun checkToken(): Boolean {
    val tokenDropbox = Utils.getTokenDropbox(appContext)
    if (tokenDropbox.isNullOrEmpty()) {
        return false
    }
    return true
}

internal fun getFilesFromDropbox(appContext: Context, appView: View) {
    if (!checkToken(appView)) {
        Snackbar.make(appView, "Dropbox Token FAIL,\nRelogin", Snackbar.LENGTH_LONG).show()
        return
    }

    try {
        val dbxClientV2 = authDropbox()
        val group = LocalUserInfo.getUserGroup(appContext)

        AsyncTask.execute {
            try {
                val listFiles = dbxClientV2.files().listFolder("//input_tasks//" + group!!)
                for (metadata in listFiles.entries) {
                    val name = metadata.name
                    if (!name.contains(".mpp")) continue

                    val file = File(appContext.filesDir, name)
                    val downloadFile = FileOutputStream(file)

                    dbxClientV2.files().downloadBuilder(metadata.pathDisplay).download(downloadFile)
                }
                RealmHandler.getInstance().deleteDB(PTS_DB::class.java)
            } catch (e: Exception) {
            }
        }
    } catch (e: Exception) {
    }
}

fun uploadFileToDropbox(excelReportPath2File: String) : String? {
    if (!checkToken()) return "Dropbox token error"
    try {
        val dbxClientV2 = authDropbox()
        val group = LocalUserInfo.getUserGroup(appContext)
        dbxClientV2.files()
                .uploadBuilder("/reports/$group/report.xlsx")
                .withMode(WriteMode.OVERWRITE)
                .withAutorename(false)
                .uploadAndFinish(FileInputStream(excelReportPath2File))
    } catch (e: Exception) {
        Log.e(LOG_TAG, e.message)
        return e.localizedMessage
    }
    return null
}


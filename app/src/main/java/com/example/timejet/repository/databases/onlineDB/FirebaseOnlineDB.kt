package com.timejet.bio.timejet.repository.databases.onlineDB

import android.content.Context
import android.util.Log
import com.example.timejet.repository.databases.onlineDB.OnlineDB
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.timejet.bio.timejet.repository.databases.localDB.showToast
import com.timejet.bio.timejet.utils.Utils
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset


class FirebaseOnlineDB(private var appContext: Context) : OnlineDB {
    companion object {
        val LOG_TAG = FirebaseOnlineDB::class.java.simpleName
    }
    private var projectID: String? = null
    val firebaseApp: FirebaseApp?
        get() {
            var app: FirebaseApp? = null
            val prjID = getProjectID()
            try {
                if(prjID.isNullOrEmpty()){
                    return app
                } else {
                    app = FirebaseApp.getInstance(prjID!!)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return app
        }

    private fun getProjectID(): String? {
        val config: File?
        try {
            config = getAppConfig("google-services.json")
            val fileTxt = loadFromAsset(config)
            if(!fileTxt.isEmpty()){
                val jsonObj = JSONObject(fileTxt)
                projectID = jsonObj.getJSONObject("project_info").get("project_id").toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return projectID
    }

    private fun getAppConfig(configName: String): File {
        val file = File(appContext.filesDir, configName)
        if (!file.exists()) {
        }
        return file
    }

    fun loadFromAsset(ifile: File?): String {
        val json: String
        try {
            val `is` = FileInputStream(ifile)
            val size = `is`.available()
            val buffer = ByteArray(size)
            `is`.read(buffer)
            `is`.close()
            json = String(buffer, Charset.forName("UTF-8"))
        } catch (ex: Exception) {
            ex.printStackTrace()
            return ""
        }
        return json
    }

    override fun attemptToInitCloudIsSuccess(loginEmail: String, loginPassword: String): Boolean {
        if (googlePlayServiceAreAvailable() && alreadyHasConfig()) {
            val configFileContent = readTheConfigFile()
            if (configFileContent.isNotEmpty()) {
                if (initializeFirebaseIsSuccessful(configFileContent, appContext) && loginEmail.isNotEmpty() && loginPassword.isNotEmpty())
                    return true
            }
        }
        return false
    }

    private fun initializeFirebaseIsSuccessful(fileTxt: String, context: Context): Boolean {
        try {
            val jsonObj = JSONObject(fileTxt)
            val appID: String = jsonObj.getJSONArray("client").getJSONObject(0).getJSONObject("client_info").get("mobilesdk_app_id").toString()
            val apiKey: String = jsonObj.getJSONArray("client").getJSONObject(0).getJSONArray("api_key").getJSONObject(0).get("current_key").toString()
            val projectID: String = jsonObj.getJSONObject("project_info").get("project_id").toString()

            Utils.saveProjectID(context, projectID)

            val options = FirebaseOptions.Builder()
                    .setApplicationId(appID)
                    .setApiKey(apiKey)
                    .setDatabaseUrl("https://$projectID.firebaseio.com")
                    .setProjectId(projectID)
                    .build()

            val app = try {
                if (FirebaseApp.getApps(context).size < 2){
                    FirebaseApp.initializeApp(context, options, projectID)
                    FirebaseApp.getInstance(projectID)
                } else {
                    FirebaseApp.getInstance(projectID)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                FirebaseApp.initializeApp(context, options, projectID)
                FirebaseApp.getInstance(projectID)
            }

            val onlineFirestore = FirebaseFirestore.getInstance(app)
            onlineFirestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun readTheConfigFile(): String {
        val config = getAppConfig("google-services.json")
        val fileTxt = loadFromAsset(config)
        if (fileTxt == null || fileTxt.isEmpty()) {
            Utils.deleteLocalFile(appContext, "google-services.json")
            return ""
        }
        return fileTxt
    }

    private fun alreadyHasConfig(): Boolean {
        try {
            val appConfig = File(appContext.filesDir, "google-services.json")
            if (!appConfig.exists() || appConfig.isDirectory) {
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    private fun googlePlayServiceAreAvailable(): Boolean {
        val playServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        val connectionResult = ConnectionResult(playServicesAvailable)
        if (!connectionResult.isSuccess) {
            val message = connectionResult.toString()
            showToast(message, appContext)
            return false
        }
        return true
    }
}

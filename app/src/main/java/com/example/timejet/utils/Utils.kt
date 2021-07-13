package com.timejet.bio.timejet.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.util.Log
import com.timejet.bio.timejet.config.REALMBASE_NAME
import org.apache.commons.compress.archivers.ArchiveException
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.io.*
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile


class Utils {

    class DirectoryCleaner(private val mFile: File?) {

        fun clean() {
            if (null == mFile || !mFile.exists() || !mFile.isDirectory) return
            for (file in mFile.listFiles()) {
                delete(file)
            }
        }

        private fun delete(file: File) {
            if (file.isDirectory) {
                for (child in file.listFiles()) {
                    delete(child)
                }
            }
            file.delete()

        }
    }

    companion object {

        private var userEmail: String? = ""

        fun getUserEmail(appContext: Context): String? {
            val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
            userEmail = prefs.getString("userEmail", userEmail)
            return userEmail
        }

        fun getUserName(appContext: Context): String? = PreferenceManager.getDefaultSharedPreferences(appContext).getString("userName", "")

        fun saveLoginData(email: String, configFilesUrl: String, userGroup: String, context: Context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putString("userEmail", email.toLowerCase())
                putString("configFilesUrl", configFilesUrl)
                putString("userGroup", userGroup)
            }.apply()
            Utils.userEmail = email
            Utils.configFilesUrl = configFilesUrl
            Utils.userGroup = userGroup
            LocalUserInfo.reset()
        }

//        var username = ""
//            private set

//        var currentTaskUID: String? = ""

        // обозначает текущее действие/событие: старт, пауза, закончил, телефон, е-мейл
//        var currentEvent: String? = ""

        // добавить имя проекта для сравнения текущего и другой строки с нужным uid,
        // имена проектов должны совпадать
//        var currentProjectName: String = ""


        // имя проекта при нажатии на PHONE, EMAIL, MEETING
//        var currentProjectNameAdd: String? = ""


        private var configFilesUrl: String? = ""
        private var userGroup: String? = ""

        fun getConfigFilesUrl(appContext: Context): String? {
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
                configFilesUrl = prefs.getString("configFilesUrl", configFilesUrl)
            } catch (e: Exception) {
            }

            return configFilesUrl
        }

        fun getUserGroup(appContext: Context): String? {
            val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
            userGroup = prefs.getString("userGroup", userGroup)

            return userGroup
        }


        // переделать этот ужас
        // private доступ ?
/*        fun save(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

            val editor = prefs.edit()
            StateRepository.getInstance().currentPTS?.let {
                editor.putLong("newCurrentTaskUID", it.uid)
                editor.putString("newCurrentEvent", it.event.string)
                editor.putString("newCurrentProjectName", it.projectName)
            } ?: run {
                editor.putLong("newCurrentTaskUID", -1)
                editor.putString("newCurrentEvent", "")
                editor.putString("newCurrentProjectName", "")
            }
//            editor.putString("currentTaskUID", currentTaskUID)
//            editor.putString("currentEvent", currentEvent)
//            editor.putString("currentProjectName", currentProjectName)
//            editor.putString("currentProjectNameAdd", currentProjectNameAdd)

            editor.apply()
        }*/

        fun saveTokenDropbox(context: Context, tokenDropbox: String) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = prefs.edit()
            editor.putString("tokenDropbox", tokenDropbox.trim())
            editor.apply()
        }

        fun getTokenDropbox(context: Context): String? {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            var tokenDropbox: String? = ""
            tokenDropbox = prefs.getString("tokenDropbox", "")
            tokenDropbox?.trim()
            return tokenDropbox
        }

        fun saveProjectID(context: Context, projectID: String) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = prefs.edit()
            editor.putString("id", projectID)
            editor.apply()
        }

        fun getProjectID(appContext: Context): String? {
            val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
            return prefs.getString("id", "")
        }

        fun saveTokenFirebase(context: Context, token: String) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                putString("tokenFirebase", token)
            }.apply()
        }

/*        fun restore(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val newCurrentProjectName = prefs.getString("newCurrentProjectName", "")!!
            val newCurrentTaskUID = prefs.getLong("newCurrentTaskUID", -1)
            val newCurrentEventStr = prefs.getString("newCurrentEvent", "")
            Log.d(LOG_TAG, "restor newCurrentEventStr: $newCurrentEventStr" )
            val newCurrentEvent = try {
                EventType.valueOf(newCurrentEventStr!!)
            } catch (e: Exception) {
                EventType.NONE
            }
            if (newCurrentTaskUID != -1L || newCurrentProjectName.isNotEmpty()) {
                StateRepository.getInstance().currentPTS =
                    CurrentPTS(newCurrentProjectName, newCurrentTaskUID, newCurrentEvent)
            }
        }*/

        // перезапускаю приложение
        fun doRestart(c: Context?) {
            try {
                //check if the app is given
                if (c != null) {
                    //fetch the packagemanager so we can get the default launch activity
                    // (you can replace this intent with any other activity if you want
                    val pm = c.packageManager
                    //check if we got the PackageManager
                    if (pm != null) {
                        //create the intent with the default start activity for your application
                        val mStartActivity = pm.getLaunchIntentForPackage(
                                c.packageName
                        )
                        //mStartActivity.putExtra("Restart", true)
                        if (mStartActivity != null) {
                            mStartActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            //create a pending intent so the application is restarted after System.exit(0) was called.
                            // We use an AlarmManager to call this intent in 100ms
                            val mPendingIntentId = 223344
                            val mPendingIntent = PendingIntent
                                    .getActivity(c, mPendingIntentId, mStartActivity,
                                            PendingIntent.FLAG_CANCEL_CURRENT)
                            val mgr = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent)
                            //kill the application
                            System.exit(0)
                        } else {
                            Log.e(TAG, "Was not able to restart application, mStartActivity null")
                        }
                    } else {
                        Log.e(TAG, "Was not able to restart application, PM null")
                    }
                } else {
                    Log.e(TAG, "Was not able to restart application, Context null")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Was not able to restart application")
            }
        }



        fun deleteLocalFile(context: Context, file2delete: String) {
            try {
                File(context.filesDir, file2delete).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @Synchronized
        fun deleteAllMPPFiles(context: Context) {
            val arr = context.fileList()
            if (arr != null)
                for (f in arr)
                    if (f != null && f.contains(".mpp")) {
                        Utils.deleteLocalFile(context, f)
                    }
        }

        @Throws(IOException::class, ArchiveException::class)
        fun unzip(zipFile: File, targetDirectory: File) {
            val zip = ZipFile(zipFile)
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                val destFilePath = File(targetDirectory, entry.name)
                destFilePath.parentFile.mkdirs()
                if (entry.isDirectory)
                    continue
                val bufferedIs = BufferedInputStream(zip.getInputStream(entry))
                bufferedIs.use {
                    destFilePath.outputStream().buffered(1024).use { bos ->
                        bufferedIs.copyTo(bos)
                    }
                }
            }
        }

        fun getEmail(nameEmailComa: String): String {
            val email = try {
                nameEmailComa.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
            } catch (e: Exception) {
                nameEmailComa
            }

            return email.trim()
        }

        fun deleteLocalFile(file2delete: String, appContext: Context): Boolean {
            val f = File(appContext.filesDir, file2delete)
            var deleted = false
            try {
                DirectoryCleaner(File(appContext.filesDir.toString() + file2delete)).clean()
                deleted = f.delete()
            } catch (e: Exception) {

            }

            return deleted
        }

        // удаляем все xlsx/backup/mpp/realm файлы
        // сделать возвращение успех/не успех
        fun deleteAllLocalFiles(appContext: Context) {
            val arr = appContext.fileList()
            if (arr != null)
                for (f in arr)
                    if (f != null && (f.contains("xlsx") ||
                                    f.contains("backup") ||
                                    f.contains("txt") ||
                                    f.contains("realm") ||
                                    f.contains("mpp"))) {

                        deleteLocalFile(f, appContext)
                    }

            deleteLocalFile("/$REALMBASE_NAME.management/access_control.control.mx", appContext)
            deleteLocalFile("/$REALMBASE_NAME.management/access_control.new_commit.cv", appContext)
            deleteLocalFile("/$REALMBASE_NAME.management/access_control.pick_writer.cv", appContext)
            deleteLocalFile("/$REALMBASE_NAME.management/access_control.write.mx", appContext)

        }

        fun formatTime3digit(timeHrs: Double): String {
            return String.format(Locale.ENGLISH, "%10.3f", timeHrs).trim { it <= ' ' }
        }

        fun calculateTime(timeHrs: Double): String {
            return calculateTime((timeHrs * 3600000.0).toLong())
        }

        fun calculateTimeForCommunications(timeHrs: Double): String {
            return calculateTimeForCommunications((timeHrs * 3600000.0).toLong())
        }

        fun calculateTimeForGlobal(timeHrs: Long): String {
            return calculateTimeForCommunications(timeHrs)
        }


        fun parseDateTime(input: String): DateTime? {
            // Sat Apr 14 17:00:00 GMT+03:00 2018
            val jodaTimePattern = "EEE MMM d HH:mm:ss 'GMT'Z y"

            var dateTime: DateTime? = null
            try {
                dateTime = DateTime.parse(input, DateTimeFormat.forPattern(jodaTimePattern))
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return dateTime
        }

        fun calculateTime(totalMilliseconds: Long): String {
            var returnedString = ""
            if (totalMilliseconds != 0L) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMilliseconds)
                if (minutes == 0L) {
                    returnedString = "00:01"
                } else {
                    if (minutes >= 60L) {
                        val hours = TimeUnit.MINUTES.toHours(minutes)
                        val minutesAfter = minutes - (hours * 60L)
                        if (hours < 10L) {
                            returnedString = "0" + hours.toString() + ":"
                        } else {
                            returnedString = hours.toString() + ":"
                        }
                        if(minutesAfter <10L){
                            returnedString += "0"+minutesAfter.toString()
                        } else {
                            returnedString += minutesAfter.toString()
                        }
                    } else {
                        if(minutes < 10L){
                            returnedString = "00:0" + minutes.toString()
                        } else {
                            returnedString = "00:" + minutes.toString()
                        }
                    }
                }
            } else {
                returnedString = "00:00"
            }
            return returnedString
        }

        fun calculateTimeForCommunications(totalMilliseconds: Long): String {
            return String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(totalMilliseconds),
                    TimeUnit.MILLISECONDS.toMinutes(totalMilliseconds) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(totalMilliseconds)),
                    TimeUnit.MILLISECONDS.toSeconds(totalMilliseconds) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(totalMilliseconds)))
        }

    }
}
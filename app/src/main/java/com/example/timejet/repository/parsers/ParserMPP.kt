package com.timejet.bio.timejet.repository.parsers

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.repository.models.FirestorePTS_DB.*
import com.timejet.bio.timejet.utils.LocalUserInfo
import com.timejet.bio.timejet.utils.Utils.Companion.getEmail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.sf.mpxj.Duration
import net.sf.mpxj.ProjectFile
import net.sf.mpxj.mpp.MPPReader
import java.io.File
import java.util.*
import kotlin.collections.HashSet

object ParserMPP {
    private const val LOG_TAG = "ParserMPP"

    private val job = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + job)

    private var realmHandler = RealmHandler.getInstance()

    private fun listMPPFiles(dir: File): ArrayList<String> {
        val files = ArrayList<String>()

        for (file in dir.listFiles()) {
            val fname = file.name
            if (fname.contains(".mpp") && !fname.contains("out_"))
                files.add(file.name)
        }
        return files
    }

    fun importPTS(context: Context): Int {
        Log.d(LOG_TAG, "Scope: ${Thread.currentThread().name}, id: ${Thread.currentThread().id}")
        var successParseCounter:Int
        var projectName = ""

        val reader = MPPReader()
        val file = context.filesDir
        val fileNames = listMPPFiles(file)
        successParseCounter = fileNames.size

        var id: Long = 0

        for (fname in fileNames) {
            var projectFile: ProjectFile?
            val path2file = "${context.filesDir}/$fname"

            try {
                projectFile = reader.read(path2file)
            } catch (e: Exception) {
                successParseCounter -= 1
                continue
            }

            val resNames: HashSet<String> = HashSet()
            for (resource in projectFile.resources) {
                val resourceName = resource.name ?: continue
                resNames.add(resourceName)
            }

            var maxOutlineLevel = 0
            for (task in projectFile.tasks) {
                if (task.outlineLevel > maxOutlineLevel)
                    maxOutlineLevel = task.outlineLevel!!
            }

            var taskName = ""
            var nextLevel = 0
            val nameStack = LinkedList<String>()

            for (task in projectFile.tasks) {
                val uid = task.uniqueID!!.toLong()

                var name = task.name
                val isActive = task.active
                val project = task.project
                if (name.isNullOrEmpty() && project.isNullOrEmpty()) continue

                val rollup = task.rollup
                val predecessors = task.predecessors
                val ls = ArrayList<Long>()
                if (predecessors.size != 0) {
                    for (rel in predecessors) {
                        ls.add(rel.targetTask.uniqueID.toLong())
                    }
                }

                val outlineLevel = task?.outlineLevel
                val outlineNumber = task?.outlineNumber
                val resName = task?.resourceNames
                val deadline = task?.deadline
                val isMilestone = task?.milestone

                var baseline1Duration: Duration? = null
                baseline1Duration = task.getBaselineDuration(1)

                val listTask = task.childTasks
                val resNames = task.resourceNames
                val resource = task.resourceAssignments

                var assignedUsers = ""
                val size = resource.size

                for (i in 0 until size) {
                    var resourceName: String? = ""
                    try {
                        resourceName = resource[i].resource.name.toString().toLowerCase()
                    } catch (e: Exception) {
                        Log.d(LOG_TAG, "resourceName failed: ${e.message}")
                    }
                    assignedUsers += resourceName
                    if (i < size - 1) assignedUsers += ";"
                }

                if (outlineLevel == 1) projectName = name
                if (uid == 0L) projectName = ""


                if (outlineLevel != null) {
                    if ((outlineLevel > 1) && (outlineLevel != nextLevel)) {
                        taskName = nameStack.pop() ?: ""
                    }
                }

                if (rollup) {
                    nameStack.push(taskName)
                    if (outlineLevel != null) {
                        nextLevel = outlineLevel + 1
                    }
                    taskName = name
                }

                val stepName = "$outlineNumber, $name"

                var zeroProgress: Double? = 0.0
                if (rollup) zeroProgress = null

                var remainingTime = baseline1Duration?.toString()
                if (!remainingTime.isNullOrEmpty()) remainingTime = remainingTime.replace("""[^0-9.]""","", true)


                    realmHandler.saveRealmObject(
                        projectName,
                        taskName,
                        stepName,
                        outlineLevel!!,
                        deadline,
                        assignedUsers,
                        ls,
                        baseline1Duration?.duration,
                        zeroProgress,
                        rollup,
                        isMilestone!!,
                        uid,
                        id,
                        isActive
                    )

                id++
            }

            for (resName in resNames) {
                realmHandler.savePhoneEmailMeeting(id++, projectName, resName)
                id++
                id++
            }
        }
        return successParseCounter
    }

    fun getAllCloudTasks(appContext: Context) : LiveData<Boolean> {
        val completed: MutableLiveData<Boolean> = MutableLiveData(false)
        RealmHandler.ioScope.launch {
            val projectNames = realmHandler.getAllProjectNamesFromDB()
            val userProjectID: String? = LocalUserInfo.getUserDomain(appContext)
            val loggedEmailUsername = LocalUserInfo.getUserEmail(appContext)

            if (projectNames != null) {
                val app = FirebaseApp.getInstance(userProjectID.toString())
                val onlineFirestore = FirebaseFirestore.getInstance(app)
                var counter = 0
                for (projectName in projectNames) {
                    if (projectName.isEmpty()) continue

                    onlineFirestore.collection(projectName)
                        .get()
                        .addOnCompleteListener { task1 ->

                            if (task1.isSuccessful) {
                                RealmHandler.ioScope.launch {
                                    counter++
                                    Log.d(LOG_TAG, "Scope: ${Thread.currentThread().name}")
                                    val documentSnapshot = task1.result
                                    val data = documentSnapshot?.documents

                                    if (data != null) {
                                        for (dataObj in data) {
                                            val data = dataObj.data
                                            val uid = data?.get(FB_UID) as Long?
                                            val projectName = data?.get(FB_PROJECT_NAME) as String?
                                            if (projectName == null) continue
                                            val emailAssigned = data?.get(FB_USERS_ASSIGNED) as String?
                                            val emailWorking = data?.get(FB_USER_WORKING) as String?
                                            val tEmail: String? = getEmail(emailWorking!!)

                                            var ptsDB = realmHandler.getPTSbyUIDProjectNameUserNameComaEmail(
                                                uid,
                                                projectName,
                                                tEmail
                                            )

                                            ptsDB?.let {
                                                realmHandler.savePTS(
                                                    it,
                                                    getEmail(data?.get(FB_USERS_ASSIGNED).toString()),
                                                    getEmail(data?.get(FB_USER_WORKING).toString()),
                                                    try { data?.get(FB_TIME_PROGRESS).toString().toDouble()
                                                        } catch (e : java.lang.NumberFormatException) {0.0},
                                                    data?.get(FB_TASK_NOTE).toString(),
                                                    data?.get(FB_ACTUAL_START).toString(),
                                                    data?.get(FB_ACTUAL_FINISH).toString(),
                                                    try { data?.get(FB_ADDITIONAL_TIME).toString().toDouble()
                                                        } catch (e : NumberFormatException ) { 0.0 },
                                                    data?.get(FB_IS_COMPLETED) as Boolean,
                                                    (data.get(FB_IS_READ) ?: false) as Boolean
                                                )
                                            }
                                        }
                                    }
                                    if(counter == projectNames.size) {
                                        completed.postValue(true)
                                    }
                                }

                            } else {
                                counter++

                                try {
                                    val documentSnapshot = task1.result
                                } catch (e: Exception) {
                                    val detailMessage = e.message
                                    Log.d(LOG_TAG, e.message)
                                }
                            }
                        }
                }
            }
        }
        return completed
    }
}
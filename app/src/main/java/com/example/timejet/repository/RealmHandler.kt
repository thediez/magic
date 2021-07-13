package com.timejet.bio.timejet.repository

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.timejet.repository.models.FirestorePTS_DB
import com.example.timejet.repository.models.PTS_DB
import com.example.timejet.repository.models.UserActivity_DB
import com.google.firebase.firestore.DocumentSnapshot
import com.timejet.bio.timejet.repository.databases.localDB.currentPossibleTasks
import com.timejet.bio.timejet.repository.databases.localDB.prevPossibleTasks
import com.timejet.bio.timejet.repository.databases.localDB.setCurrentPossibleTasks
import com.timejet.bio.timejet.ui.main.*
import com.timejet.bio.timejet.utils.LocalUserInfo
import com.timejet.bio.timejet.utils.Utils
import io.realm.*
import io.realm.Realm.getDefaultInstance
import io.realm.exceptions.RealmPrimaryKeyConstraintException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.newSingleThreadContext
import org.joda.time.LocalDate
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.ceil

class RealmHandler {
    private val LOGTAG = RealmHandler::class.java.simpleName
    private val realm: Realm

    init {
        Realm.init(appContext);
        realm = getDefaultInstance()
    }

    fun addTimeToUserActivityDB(appContext: Context, projectName: String, uid: Long) {
        val userEmail = LocalUserInfo.getUserEmail(appContext)
        val ptsCurrentState = getPtsByUidProjectNameUser(uid, projectName, userEmail)
        ptsCurrentState?.taskName?.let {
            setUserActivityByUIDProjectNameUserNameComaEmail(uid, projectName, it, ptsCurrentState.stepName, userEmail, 0L)
        }
    }

    fun addDurationTimePTSDB(appContext: Context, projectName: String, uid: Long, durationTime: Long, fromStateRepository: Boolean) {
        val userEmail = LocalUserInfo.getUserEmail(appContext)
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                getPTSbyUIDProjectNameUserNameComaEmail(uid, projectName, userEmail).let { pts ->
                    if (fromStateRepository) {
                        pts?.setPTSProgress((pts.ptSprogress
                                ?: 0.0) + durationTime.div(3600000.0 / timeKoeff))
                        pts?.taskWorkingUsername = userEmail
                        bgRealm.copyToRealmOrUpdate(pts)
                    }
                }
            }
        }
    }

    fun addDurationTimePTSDBNotLoop(appContext: Context, projectName: String, uid: Long, durationTime: Double) {
        getDefaultInstance().use { realm->
            realm.executeTransactionAsync { bgRealm->
                getPTSbyUIDProjectNameUserNameComaEmail(uid, projectName, LocalUserInfo.getUserEmail(appContext)).let { ptsDb ->
                    ptsDb?.setPTSProgress(durationTime)
                    if (ptsDb != null) {
                        ptsDb.taskWorkingUsername = LocalUserInfo.getUserEmail(appContext)
                    }
                    bgRealm.copyToRealmOrUpdate(ptsDb)
                }
            }
        }
    }

    fun addDurationTimeUserActivity(appContext: Context, projectName: String, uid: Long, duration: Long) {
        val userEmail = LocalUserInfo.getUserEmail(appContext)
        val ptsCurrentState = getPtsByUidProjectNameUser(uid, projectName, userEmail)
        if (uid < 0L) {
            setUserActivityByUIDProjectNameUserNameComaEmail(uid, projectName, "", "", userEmail, duration)
        } else {
            ptsCurrentState.taskName.let {
                setUserActivityByUIDProjectNameUserNameComaEmail(uid, projectName, it, ptsCurrentState.stepName, userEmail, duration)
            }
        }
    }

    fun alignAdditionalTimeByNegativeRemainingTime(appContext: Context, projectName: String, uid: Long) {
        val userEmail = LocalUserInfo.getUserEmail(appContext)
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                getPTSbyUIDProjectNameUserNameComaEmail(uid, projectName, userEmail)?.let { task ->
                    val remainingTime = task.timeBudget + (task.taskAdditionalTime
                            ?: 0.0) - (task.ptSprogress ?: 0.0)
                    if (remainingTime < 0) {
                        task.taskAdditionalTime = ceil((task.taskAdditionalTime
                                ?: 0.0) + abs(remainingTime))
                    }
                    bgRealm.copyToRealmOrUpdate(task)
                }
            }
        }
    }

    fun completeTask(context: Context) {
        if (uiDbyEvent == ERROR_GET_UID) return
        val loggedUserEmail = LocalUserInfo.getUserEmail(context)
        StateRepository.getInstance().currentPTS?.let { pts ->
            getDefaultInstance().use { realm ->
                realm.executeTransactionAsync { bgRealm ->
                    val ptsDb = getPTSbyUIDProjectNameUserNameComaEmail(uiDbyEvent, pts.projectName, loggedUserEmail)
                            ?: return@executeTransactionAsync
                    ptsDb.isPTScompleted = true
                    ptsDb.taskWorkingUsername = loggedUserEmail
                    if (ptsDb.taskFinishDateTime == "") {
                        if (ptsDb.taskStartDateTime == "") ptsDb.taskStartDateTime = currentDateTime
                        ptsDb.taskFinishDateTime = currentDateTime
                    }
                    bgRealm.copyToRealmOrUpdate(ptsDb)
                }
            }
        }
    }

    fun updateMilestoneState(taskId: Long) {
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                getTaskFromDb(taskId)?.let { task ->
                    task.isPTScompleted = true
                    bgRealm.copyToRealmOrUpdate(task)
                }
            }
        }
    }

    private fun isAllPredecessorsCompleted(predecessorsUIDList: RealmList<Long>, projectName: String): Boolean {
        val list = Array(predecessorsUIDList.size) { i -> predecessorsUIDList[i] }
        getDefaultInstance().use { realm ->
            val ptsDbsCount = realm.where(PTS_DB::class.java)
                    .`in`(PTS_DB.REALM_TASK_UID, list)
                    .equalTo(PTS_DB.REALM_TASK_COMPLETED, false)
                    .equalTo(PTS_DB.REALM_PROJECT_NAME, projectName)
                    .count()
            return ptsDbsCount == 0L
        }
    }

    fun isAllPredecessorsCompletedWithString(predecessorsUIDlist: RealmList<Long>, projectName: String): Pair<Boolean, String> {
        getDefaultInstance().use { realm ->
            val list = Array(predecessorsUIDlist.size) { i -> predecessorsUIDlist[i] }
            val ptsDbs = realm.where(PTS_DB::class.java)
                    .`in`(PTS_DB.REALM_TASK_UID, list)
                    .equalTo(PTS_DB.REALM_TASK_COMPLETED, false)
                    .equalTo(PTS_DB.REALM_PROJECT_NAME, projectName)
                    .findAll()
            var parentsIsCompleted = true
            val stringBuilder = StringBuilder("")
            for (ptsDb in ptsDbs) {
                stringBuilder.append(ptsDb.uid)
                if (!ptsDb.isPTScompleted) {
                    parentsIsCompleted = false
                    stringBuilder.append("[N] - ${ptsDb.usersAssigned};\n")
                } else {
                    stringBuilder.append("[Y] - ${ptsDb.usersAssigned};\n")
                }
            }
            return Pair(parentsIsCompleted, stringBuilder.toString())
        }
    }

    // собираем строку, в которой собраны все задания-предшественники от которых зависит это задание
    fun getAllSublist(predecessorsUIDlist: RealmList<Long>, prjName: String): StringBuilder {
        // начинаем собирать строку с UID предшественников и отметкой выполнено/нет
        val stringBuilder = StringBuilder("")
        // берем UID по-порядку и смотрим, это задание или каталог подзаданий, узнаем их готовность
        // собираем строку в sub1 одного предшественника
        for (predecessorUID in predecessorsUIDlist) {
            stringBuilder.append(predecessorUID)
            val ptsDb = getTaskByUidAndProjectName(predecessorUID, prjName) ?: continue
            // запоминаем уровень вложенности этого UID
            val baseOutline = ptsDb.outlineLevel
            // rollup это каталог, их пропускаем
            val rollup1 = ptsDb.isRollup
            // прописываем ответственных юзеров
            val taskResponsibleUsername = ptsDb.usersAssigned
            // если это одиночное задание, то проставляем ему завершено или нет
            if (!rollup1) {
                val ptsCompleted = ptsDb.isPTScompleted
                if (ptsCompleted)
                    stringBuilder.append("[Y] - $taskResponsibleUsername;\n")
                else
                    stringBuilder.append("[N] - $taskResponsibleUsername;\n")
            }
            // берем максимальный индекс в базе заданий, чтобы в переборе не выйти за границы
            val maxID = getUniqueId(PTS_DB::class.java)
            // перебираю элементы в базе по-порядку, по ID
            var id = ptsDb.id
            var currentOutline = 0
            var sub1: String
            // если это каталог, то переходим к след элементу
            if (rollup1) id++
            val sub1Builder = StringBuilder()
            do {
                var subCatalogPTS: PTS_DB? = null
                var uid: Long = 0
                try {
                    subCatalogPTS = getTaskByUidAndProjectName(uid, prjName)
                    if (subCatalogPTS != null) {
                        uid = subCatalogPTS.uid
                        currentOutline = subCatalogPTS.outlineLevel
                        // пропускаем текущий уровень, если заступ за следующий элемент
                        // фильтр
                        if (currentOutline == baseOutline) {
                            continue
                        }
                        val rollup2 = subCatalogPTS.isRollup
                        val PTS2completed = subCatalogPTS.isPTScompleted
                        // это уже вылет за пределы, пропускаем
                        // фильтр
                        if (rollup2) {
                            // если это каталог, пропускаем и переходим к след элементу
                            continue
                        }
                        // пишем идентификатор подзадания
                        sub1Builder.append(uid)
                        // прописываем ответственных юзеров
                        val taskResponsibleUsernameSub = subCatalogPTS.usersAssigned
                        // дополняем индекс задания выполнено/нет Y/N
                        if (PTS2completed)
                            sub1Builder.append("[Y] - $taskResponsibleUsernameSub;\n")
                        else
                            sub1Builder.append("[N] - $taskResponsibleUsernameSub;\n")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // проходим по всем элементам до следующего, у которого outline равна как у первого
            } while (id++ <= maxID && currentOutline != baseOutline)
            // если это каталог, то проставляем UID внутри и их статус
            sub1 = sub1Builder.toString()
            if (sub1 != "") {
                stringBuilder.append(" {\n")
                stringBuilder.append(sub1)
                stringBuilder.append("}\n")
            }
        }
        return stringBuilder
    }

    private fun getUniqueId(className: Class<*>): Long {
        getDefaultInstance().use { realm ->
            val number = realm.where(className as Class<RealmModel>).max(PTS_DB.REALM_TASK_ID)
            return if (number == null)
                0
            else
                number as Long + 1
        }
    }

    fun checkSetActualStart(context: Context) {
        val uid = uiDbyEvent
        if (uiDbyEvent == ERROR_GET_UID || uid < 0) return
        StateRepository.getInstance().currentPTS?.let { currentPts ->
            getDefaultInstance().use { realm ->
                realm.executeTransactionAsync { bgRealm ->
                    getPTSbyUIDProjectNameUserNameComaEmail(uid, currentPts.projectName,
                            LocalUserInfo.getUserEmail(context)).let { pts ->
                        if (pts != null) {
                            pts.taskStartDateTime = currentDateTime
                            bgRealm.copyToRealmOrUpdate(pts)
                        }
                    }
                }
            }
        }
    }

    fun deleteAllRealmDB() {
        getDefaultInstance().use { realm ->
            realm.deleteAll()
        }
    }

    fun getTaskFromDb(taskId: Long): PTS_DB? {
        getDefaultInstance().use { realm ->
            val r = realm.copyFromRealm(realm.where(PTS_DB::class.java).equalTo(PTS_DB.REALM_TASK_ID, taskId).findFirst())
            val d = realm.where(PTS_DB::class.java).equalTo(PTS_DB.REALM_TASK_ID, taskId).findFirst()
            Log.e(LOGTAG, "getTaskFromDb result: ${r?.ptSprogress} Thread: ${Thread.currentThread().name}, id: ${Thread.currentThread().id}")
            Log.e(LOGTAG, "getTaskFromDb result calculated: ${r?.ptSprogress?.let { Utils.calculateTime(it) }} Thread: ${Thread.currentThread().name}, id: ${Thread.currentThread().id}")
            return r
        }
    }

    fun getAllYourTasksNum(appContext: Context): Int? {
        val userEmail = LocalUserInfo.getUserEmail(appContext)
        getDefaultInstance().use { realm ->
            val ptsDb = realm.where(PTS_DB::class.java)
                    ?.contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, userEmail)
                    ?.findAll()
            return ptsDb?.size
        }
    }

    private fun getPtsByUidProjectNameUser(uid: Long?, projectName: String, userNameComaEmail: String?
    ): PTS_DB? {

        getDefaultInstance().use { bgrealm ->
            val result = bgrealm.copyFromRealm(bgrealm.where(PTS_DB::class.java)
                            .equalTo(PTS_DB.REALM_TASK_UID, uid)
                            .contains(PTS_DB.REALM_PROJECT_NAME, projectName)
                            .contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, userNameComaEmail)
                            .findFirst())
            return result
        }
    }

    @SuppressLint("SimpleDateFormat")
    fun setUserActivityByUIDProjectNameUserNameComaEmail(uid: Long?, projectName: String, taskName: String,
                                                         stepName: String, userNameComaEmail: String?, durationWorked: Long
    ) {
        val userEmail = Utils.getEmail(userNameComaEmail!!)
        val df = SimpleDateFormat("dd MMM yyy")
        val calendar = Calendar.getInstance()
        val date = Date(calendar.timeInMillis - durationWorked)
        val format = SimpleDateFormat("dd")
        if (format.format(date).toInt() == calendar.get(Calendar.DAY_OF_MONTH)) {
            getDefaultInstance().use { realm ->
                var userActivity: UserActivity_DB?
                userActivity = realm.where(UserActivity_DB::class.java)
                        .equalTo(UserActivity_DB.TASK_UID_FIELD, uid)
                        .contains(UserActivity_DB.PROJECT_NAME, projectName)
                        .contains(UserActivity_DB.USER_ASSIGNED, userEmail)
                        .contains(UserActivity_DB.DATE_TIME, df.format(calendar.time).toString())
                        .findFirst()
                if (userActivity == null) {
                    val currentIdNum = realm.where(UserActivity_DB::class.java).max(UserActivity_DB.ID_FIELD)
                    val nextId: Long = if (currentIdNum == null) {
                        1
                    } else {
                        currentIdNum.toLong() + 1
                    }
                    userActivity = uid?.let {
                        createEmptyObjectForUserActivity(nextId, it, projectName,
                                when (uid) {
                                    PTS_DB.PTS_EMAIL_UID -> PROJECT_EMAIL
                                    PTS_DB.PTS_MEETING_UID -> PROJECT_MEETING
                                    PTS_DB.PTS_PHONECALL_UID -> PROJECT_PHONECALL
                                    PTS_DB.PTS_TRAVEL_UID -> PROJECT_TRAVEL
                                    else -> "PROJECT_START"
                                }, userEmail, df.format(calendar.time).toString(), calendar.time, taskName, stepName, durationWorked)
                    }
                    if (userActivity != null) {
                        saveNewUserActivity(userActivity)
                    }
                } else {
                    if (uid != null) {
                        getDefaultInstance().use { realm1 ->
                            realm1.executeTransactionAsync { bgRealm ->
                                val userActivityTask = bgRealm.where(UserActivity_DB::class.java)
                                        .equalTo(UserActivity_DB.TASK_UID_FIELD, uid)
                                        .contains(UserActivity_DB.PROJECT_NAME, projectName)
                                        .contains(UserActivity_DB.USER_ASSIGNED, userEmail)
                                        .contains(UserActivity_DB.DATE_TIME, df.format(calendar.time).toString())
                                        .findFirst()
                                if(userActivityTask != null){
                                    userActivityTask.eventDuration += durationWorked
                                    bgRealm.copyToRealmOrUpdate(userActivityTask)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            getDefaultInstance().use { realm ->
                var userActivity: UserActivity_DB?
                val todayUserActivity = realm.where(UserActivity_DB::class.java)
                        .equalTo(UserActivity_DB.TASK_UID_FIELD, uid)
                        .contains(UserActivity_DB.PROJECT_NAME, projectName)
                        .contains(UserActivity_DB.USER_ASSIGNED, userEmail)
                        .contains(UserActivity_DB.DATE_TIME, df.format(calendar.time).toString())
                        .findFirst()
                val yesterdayUserActivity = realm.where(UserActivity_DB::class.java)
                        .equalTo(UserActivity_DB.TASK_UID_FIELD, uid)
                        .contains(UserActivity_DB.PROJECT_NAME, projectName)
                        .contains(UserActivity_DB.USER_ASSIGNED, userEmail)
                        .contains(UserActivity_DB.DATE_TIME, df.format(Date(calendar.timeInMillis - (60000 * 60 * 24))).toString())
                        .findFirst()
                if (todayUserActivity == null) {
                    val currentIdNum = realm.where(UserActivity_DB::class.java).max(UserActivity_DB.ID_FIELD)
                    val nextId: Long = if (currentIdNum == null) {
                        1
                    } else {
                        currentIdNum.toLong() + 1
                    }
                    userActivity = uid?.let {
                        createEmptyObjectForUserActivity(nextId, it, projectName,
                                when (uid) {
                                    PTS_DB.PTS_EMAIL_UID -> PROJECT_EMAIL
                                    PTS_DB.PTS_MEETING_UID -> PROJECT_MEETING
                                    PTS_DB.PTS_PHONECALL_UID -> PROJECT_PHONECALL
                                    PTS_DB.PTS_TRAVEL_UID -> PROJECT_TRAVEL
                                    else -> "PROJECT_START"
                                }, userEmail, df.format(calendar.time).toString(), Date(calendar.timeInMillis),
                                taskName, stepName, calendar.timeInMillis.minus(LocalDate.now().toDateTimeAtStartOfDay().millis))
                    }
                    userActivity?.let { saveNewUserActivity(it) }
                } else {
                    userActivity = realm.where(UserActivity_DB::class.java)
                            .equalTo(UserActivity_DB.TASK_UID_FIELD, uid)
                            .contains(UserActivity_DB.PROJECT_NAME, projectName)
                            .contains(UserActivity_DB.USER_ASSIGNED, userEmail)
                            .contains(UserActivity_DB.DATE_TIME, df.format(calendar.time).toString())
                            .findFirst()
                    getDefaultInstance().use { realm ->
                        realm.executeTransactionAsync { bgRealm ->
                            userActivity.let { task ->
                                task?.eventDuration = (task?.eventDuration
                                        ?: 0) + calendar.timeInMillis.minus(LocalDate.now().toDateTimeAtStartOfDay().millis)
                                bgRealm.copyToRealmOrUpdate(task)
                            }
                        }
                    }
                }
                if (yesterdayUserActivity == null) {
                    val currentIdNum = realm.where(UserActivity_DB::class.java).max(UserActivity_DB.ID_FIELD)
                    val nextId: Long = if (currentIdNum == null) {
                        1
                    } else {
                        currentIdNum.toLong() + 1
                    }

                    userActivity = uid?.let {
                        createEmptyObjectForUserActivity(nextId, it, projectName,
                                when (uid) {
                                    PTS_DB.PTS_EMAIL_UID -> PROJECT_EMAIL
                                    PTS_DB.PTS_MEETING_UID -> PROJECT_MEETING
                                    PTS_DB.PTS_PHONECALL_UID -> PROJECT_PHONECALL
                                    PTS_DB.PTS_TRAVEL_UID -> PROJECT_TRAVEL
                                    else -> "PROJECT_START"
                                }, userEmail, df.format(Date(Calendar.getInstance().timeInMillis - (60000 * 60 * 24))).toString(), Date(Calendar.getInstance().timeInMillis - (60000 * 60 * 24)),
                                taskName, stepName, durationWorked - Calendar.getInstance().timeInMillis.minus(
                                LocalDate.now().toDateTimeAtStartOfDay().millis))
                    }
                    userActivity?.let { saveNewUserActivity(it) }
                } else {
                    userActivity = realm.where(UserActivity_DB::class.java)
                            .equalTo(UserActivity_DB.TASK_UID_FIELD, uid)
                            .contains(UserActivity_DB.PROJECT_NAME, projectName)
                            .contains(UserActivity_DB.USER_ASSIGNED, userEmail)
                            .contains(UserActivity_DB.DATE_TIME, df.format(Date(Calendar.getInstance().timeInMillis - (60000 * 60 * 24))).toString())
                            .findFirst()
                    getDefaultInstance().use { realm ->
                        realm.executeTransactionAsync { bgRealm ->
                            userActivity.let { task ->
                                task?.eventDuration = (task?.eventDuration
                                        ?: 0) + (durationWorked - Calendar.getInstance().timeInMillis.minus(LocalDate.now().toDateTimeAtStartOfDay().millis))
                                bgRealm.copyToRealmOrUpdate(task)
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    fun getUserActivityByDay(daily: Boolean): ArrayList<UserActivity_DB> {
        val userEmail = LocalUserInfo.getUserEmail(appContext)
        val df = SimpleDateFormat("dd MMM yyyy")
        val list = ArrayList<UserActivity_DB>()
        getDefaultInstance().use { realm ->
            if (daily) {
                val pts = realm.where(UserActivity_DB::class.java)
                        .contains(UserActivity_DB.DATE_TIME, df.format(Calendar.getInstance().time).toString())
                        .contains(UserActivity_DB.USER_ASSIGNED, userEmail)
                        .findAll()
                list.addAll(realm.copyFromRealm(pts));
                return list
            } else {
                val pts = realm.where(UserActivity_DB::class.java)
                        .between(UserActivity_DB.DATE_DATE, getDaysAgo(), Calendar.getInstance().time)
                        .contains(UserActivity_DB.USER_ASSIGNED, userEmail)
                        .findAll()
                list.addAll(realm.copyFromRealm(pts))
                val newList = ArrayList<UserActivity_DB>()
                list.forEachIndexed { index, s ->
                    if (index == 0) {
                        newList.add(s)
                    } else {
                        val b = newList.filter { o -> o.projectName == s.projectName && o.taskUID == s.taskUID }
                        if (b.isEmpty()) {
                            newList.add(s)
                        } else {
                            newList.forEach {
                                if (it.projectName == s.projectName && it.taskUID == s.taskUID) {
                                    it.eventDuration += s.eventDuration
                                }
                            }
                        }
                    }
                }
                return newList
            }
        }
    }

    private fun saveNewUserActivity(pts: UserActivity_DB) {
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                bgRealm.insert(pts)
            }
        }
    }

    fun checkIfNotification() {
        val userEmail = LocalUserInfo.getUserEmail(appContext)
        getDefaultInstance().use {
            val query = realm.where(PTS_DB::class.java)
            query.equalTo(PTS_DB.REALM_TASK_COMPLETED, false).and().isNotEmpty(PTS_DB.REALM_TASK_WORKING_USERNAME)
            query.contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, userEmail)
            query.equalTo(PTS_DB.IS_NOTIFICATION_SHOWED, false)
            query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_PHONECALL_UID)
            query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_EMAIL_UID)
            query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_MEETING_UID)
            query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_TRAVEL_UID)
            query.notEqualTo(PTS_DB.REALM_TASK_UID, 0L)
            val a = query.findAll()
            val uidToExclude = TreeSet<Long>()
            for (pts in a) {
                val predecessors = pts.predecessorsIDlist
                var flag: Boolean
                if (predecessors.isEmpty()) {
                    flag = true
                } else {
                    flag = true
                    for (it in predecessors) {
                        val c = realm.where(PTS_DB::class.java)
                                .contains(PTS_DB.REALM_PROJECT_NAME, pts.projectName)
                                .equalTo(PTS_DB.REALM_TASK_UID, it)
                                .findFirst()
                        if (!c!!.isPTScompleted) {
                            flag = false
                            break
                        }
                    }
                }
                if (pts.taskDeadline != null) {
                    val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)
                    val deadLine = dateFormat.parse(pts.taskDeadline.toString())
                    if (deadLine.time + 36 * 100000 * 20 < Date().time) {
                        flag = true
                    }
                }
                if (!flag && !uidToExclude.contains(pts.id)) {
                    uidToExclude.add(pts.id)
                }
            }
            uidToExclude.forEach { query.notEqualTo(PTS_DB.REALM_TASK_ID, it) }
            val tasksInProgress = query.findAll()
            for (task in tasksInProgress) {
                if (task.taskDeadline != null) {
                    val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)
                    val deadLine = dateFormat.parse(task.taskDeadline.toString())
                    if (deadLine.time + 36 * 100000 * 20 < Date().time && deadLine.time > Date().time) {
                        sendNotification(appContext, task.uid, true)
                        setIsShowNotification(task.id, true)
                        continue
                    }
                } else {
                    if (task.taskRemainingTime < 20.0) {
                        sendNotification(appContext, task.uid, false)
                        setIsShowNotification(task.id, true)
                    }
                }
            }
        }
    }

    fun setIsOverBudgetIsShown(id: Long, isShown: Boolean) {
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                getTaskFromDb(id)?.let {
                    it.isCheckOverBugetIsShown = isShown
                    bgRealm.copyToRealmOrUpdate(it)
                }
            }
        }
    }

    private fun setIsShowNotification(id: Long, isShowen: Boolean) {
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                getTaskFromDb(id)?.let { ptsDb ->
                    ptsDb.isNotificationShowen = isShowen
                    bgRealm.copyToRealmOrUpdate(ptsDb)
                }
            }
        }
    }

    private fun getDaysAgo(): Date {
        val calendar = Calendar.getInstance()
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        calendar.add(Calendar.DAY_OF_YEAR, -dayOfMonth)
        return calendar.time
    }

    fun getPTSbyUIDProjectNameUserNameComaEmail(uid: Long?, projectName: String, userNameComaEmail: String?): PTS_DB? {
        val userEmail = Utils.getEmail(userNameComaEmail!!)
        return if (uid == PTS_DB.PTS_PHONECALL_UID || uid == PTS_DB.PTS_EMAIL_UID || uid == PTS_DB.PTS_MEETING_UID
                || uid == PTS_DB.PTS_TRAVEL_UID) {
            getDefaultInstance().use { realm ->
                var pts = realm.where(PTS_DB::class.java)
                        .equalTo(PTS_DB.REALM_TASK_UID, uid)
                        .contains(PTS_DB.REALM_PROJECT_NAME, projectName)
                        .contains(PTS_DB.REALM_TASK_WORKING_USERNAME, userEmail)
                        .findFirst()
                if (pts == null) {
                    val currentIdNum = realm.where(PTS_DB::class.java).max(PTS_DB.REALM_TASK_ID)
                    val nextId: Long = if (currentIdNum == null) {
                        1
                    } else {
                        currentIdNum.toLong() + 1
                    }
                    pts = createEmptyObject(nextId, uid, projectName, when (uid) {
                        PTS_DB.PTS_EMAIL_UID -> PROJECT_EMAIL
                        PTS_DB.PTS_MEETING_UID -> PROJECT_MEETING
                        PTS_DB.PTS_PHONECALL_UID -> PROJECT_PHONECALL
                        PTS_DB.PTS_TRAVEL_UID -> PROJECT_TRAVEL
                        else -> throw  IllegalArgumentException("")
                    }, userEmail)
                    try {
                        saveNewPTS(pts)
                    } catch (e: RealmPrimaryKeyConstraintException) {
                        e.printStackTrace()
                        pts = null
                    }
                }
                pts
            }
        } else {
            getDefaultInstance().use { realm ->
                val task = realm.copyFromRealm(realm.where(PTS_DB::class.java)
                        .equalTo(PTS_DB.REALM_TASK_UID, uid)
                        .contains(PTS_DB.REALM_PROJECT_NAME, projectName)
                        .contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, userEmail)
                        .findFirst())
//                val g = realm.where(PTS_DB::class.java)
//                        .equalTo(PTS_DB.REALM_TASK_UID, uid)
//                        .contains(PTS_DB.REALM_PROJECT_NAME, projectName)
//                        .contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, userEmail)
//                        .findFirst()
                return task
            }
        }
    }

    fun getAllTasksFinished(appContext: Context): Int? {
        val userEmail = LocalUserInfo.getUserEmail(appContext)
        getDefaultInstance().use { realm ->
            val ptsDb = realm.where(PTS_DB::class.java)
                    ?.equalTo(PTS_DB.REALM_TASK_COMPLETED, true)
                    ?.contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, userEmail)
                    ?.findAll()
            return ptsDb?.size
        }
    }

    fun getNumPossibleStartTask(appContext: Context): Int {
        val userEmail = LocalUserInfo.getUserEmail(appContext)
        var possibleTasksNums = 0
        userEmail?.let {
            getDefaultInstance().use { realm ->
                val ptsDb = realm.where(PTS_DB::class.java)
                        .equalTo(PTS_DB.REALM_TASK_COMPLETED, false)
                        .contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, userEmail)
                        .findAll()
                possibleTasksNums = ptsDb?.size as Int
                for (item in ptsDb) {
                    if (!isAllPredecessorsCompleted(item.predecessorsIDlist, item.projectName)) possibleTasksNums -= 1
                }
            }
        }
        return possibleTasksNums
    }

    fun getAllProjectNamesFromDB(): TreeSet<String>? {
        getDefaultInstance().use { realm ->
            val projectNamesUnFormatted = TreeSet<String>()
            val ptsItems = realm.where(PTS_DB::class.java)
                    .findAll()
            for (pts_db in ptsItems) {
                val prjName = pts_db.projectName
                if (prjName.isEmpty()) continue
                projectNamesUnFormatted.add(prjName)
            }
            val size = ptsItems.size
            if (size == 0) {
                return null
            }
            return projectNamesUnFormatted
        }
    }

    fun getAllProjectNamesFromDBMain(): TreeSet<String> {
        getDefaultInstance().use { realm ->
            val projectNamesUnFormatted = TreeSet<String>()
            val ptsItems = realm.where(PTS_DB::class.java)
                    .findAll()
            for (pts_db in ptsItems) {
                val prjName = pts_db.projectName
                if (prjName.isEmpty()) continue
                projectNamesUnFormatted.add(prjName)
            }
            val size = ptsItems.size
            if (size == 0) {
                return projectNamesUnFormatted
            }
            return projectNamesUnFormatted
        }
    }

    fun getAllProjectNamesFromDBByUser(): TreeSet<String> {
        getDefaultInstance().use { realm ->
            val email = LocalUserInfo.getUserEmail(appContext)
            val projectNamesUnFormatted = TreeSet<String>()
            val ptsItems = realm.where(PTS_DB::class.java)
                    .contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, email)
                    .findAll()
            for (pts_db in ptsItems) {
                val prjName = pts_db.projectName
                if (prjName.isEmpty()) continue
                projectNamesUnFormatted.add(prjName)
            }
            val size = ptsItems.size
            if (size == 0) {
                return projectNamesUnFormatted
            }
            return projectNamesUnFormatted
        }
    }

    fun getAllUsersAssignedFromDB(): TreeSet<String> {
        getDefaultInstance().use { realm ->
            val projectNamesUnFormatted = TreeSet<String>()
            val ptsItems = realm.where(PTS_DB::class.java)
                    .findAll()
            for (pts_db in ptsItems) {
                val prjName = pts_db.usersAssigned
                if (prjName.isEmpty()) continue
                projectNamesUnFormatted.add(prjName)
            }
            val size = ptsItems.size
            if (size == 0) {
                return projectNamesUnFormatted
            }
            return projectNamesUnFormatted
        }
    }

    fun getAllTasksFromDB(): TreeSet<String>? {
        getDefaultInstance().use { realm ->
            val projectNamesUnFormatted = TreeSet<String>()
            val ptsItems = realm.where(PTS_DB::class.java)
                    .findAll()
            for (pts_db in ptsItems) {
                val prjName = pts_db.taskName
                if (prjName.isEmpty()) continue
                projectNamesUnFormatted.add(prjName)
            }
            val size = ptsItems.size
            if (size == 0) {
                return projectNamesUnFormatted
            }
            return projectNamesUnFormatted
        }
    }

    fun getAllTasksFromDBByUser(): TreeSet<String> {
        getDefaultInstance().use { realm ->
            val email = LocalUserInfo.getUserEmail(appContext)
            val projectNamesUnFormatted = TreeSet<String>()
            val ptsItems = realm.where(PTS_DB::class.java)
                    .contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, email)
                    .findAll()
            for (pts_db in ptsItems) {
                val prjName = pts_db.taskName
                if (prjName.isEmpty()) continue
                projectNamesUnFormatted.add(prjName)
            }
            val size = ptsItems.size
            if (size == 0) {
                return projectNamesUnFormatted
            }
            return projectNamesUnFormatted
        }
    }

    fun savePTS(
            ptsDB: PTS_DB,
            email: String?,
            taskWorkingUserName: String?,
            PTSProgress: Double?,
            taskNote: String?,
            taskStartDateTime: String?,
            taskFinishDateTime: String?,
            taskAdditionalTime: Double?,
            isPTSCompleted: Boolean,
            taskIsRead: Boolean
    ) {
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                ptsDB.usersAssigned = email
                ptsDB.taskWorkingUsername = taskWorkingUserName
                ptsDB.setPTSProgress(PTSProgress)
                ptsDB.taskNote = taskNote
                ptsDB.taskStartDateTime = taskStartDateTime
                ptsDB.taskFinishDateTime = taskFinishDateTime
                ptsDB.taskAdditionalTime = taskAdditionalTime
                ptsDB.isPTScompleted = isPTSCompleted
                ptsDB.isRead = taskIsRead
                bgRealm.copyToRealmOrUpdate(ptsDB)
            }
        }
    }

    fun savePTS(pts: PTS_DB) {
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                bgRealm.copyToRealmOrUpdate(pts)
            }
        }
    }

    private fun saveNewPTS(pts: PTS_DB) {
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                bgRealm.insert(pts)
            }
        }
    }

    fun getFromRealm(allUsers: Boolean, searchByProgress: String, sortByUser: String, sortByProjectName: String,
                     sortByTaskName: String, stepName: String, orderDeadline: Boolean = false): List<PTS_DB> {
        if (allUsers) {
            if (searchByProgress.isEmpty() && sortByUser.isEmpty()
                    && sortByProjectName.isEmpty() && sortByTaskName.isEmpty() && stepName.isEmpty()) {
                if (!orderDeadline) {
                    getDefaultInstance().use { realm ->
                        val ptsDB: List<PTS_DB> = realm.copyFromRealm(realm.where(PTS_DB::class.java)
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_PHONECALL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_EMAIL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_MEETING_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_TRAVEL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, 0L)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, 1L)
                                .and()
                                .notEqualTo(PTS_DB.REALM_IS_MILESTONE, true)
                                .findAll())
                        return ptsDB
                    }
                } else {
                    getDefaultInstance().use { realm ->
                        val ptsDB: List<PTS_DB> = realm.copyFromRealm(realm.where(PTS_DB::class.java)
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_PHONECALL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_EMAIL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_MEETING_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_TRAVEL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, 0L)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, 1L)
                                .and()
                                .notEqualTo(PTS_DB.REALM_IS_MILESTONE, true)
                                .sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                                .findAll())
                        return ptsDB
                    }
                }
            } else {
                getDefaultInstance().use { realm ->
                    val query = realm.where(PTS_DB::class.java)
                    if (searchByProgress.isNotEmpty()) {
                        when (searchByProgress) {
                            "Ready to start" -> query.equalTo(PTS_DB.REALM_TASK_COMPLETED, false)
                            "In Progress" -> query.equalTo(PTS_DB.REALM_TASK_COMPLETED, false).and().isNotEmpty(PTS_DB.REALM_TASK_WORKING_USERNAME)
                            "Complete" -> query.equalTo(PTS_DB.REALM_TASK_COMPLETED, true)
                            "Locked" -> query.equalTo(PTS_DB.REALM_TASK_COMPLETED, false)
                        }
                    }
                    if (sortByUser.isNotEmpty()) {
                        query.contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, sortByUser)
                    }
                    if (sortByProjectName.isNotEmpty()) {
                        query.contains(PTS_DB.REALM_PROJECT_NAME, sortByProjectName)
                    }
                    if (sortByTaskName.isNotEmpty()) {
                        query.contains(PTS_DB.REALM_TASK_NAME, sortByTaskName)
                    }
                    if (stepName.isNotEmpty()) {
                        query.contains(PTS_DB.REALM_STEP_NAME, stepName)
                    }
                    query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_PHONECALL_UID)
                    query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_EMAIL_UID)
                    query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_MEETING_UID)
                    query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_TRAVEL_UID)
                    query.notEqualTo(PTS_DB.REALM_TASK_UID, 0L)
                    when (searchByProgress) {
                        "Ready to start" -> {
                            val a = query.findAll()
                            val uidToExclude = TreeSet<Long>()
                            for (pts in a) {
                                val predecessors = pts.predecessorsIDlist
                                var flag: Boolean
                                if (predecessors.isEmpty()) {
                                    flag = true
                                } else {
                                    flag = true
                                    for (it in predecessors) {
                                        val c = realm.where(PTS_DB::class.java)
                                                .contains(PTS_DB.REALM_PROJECT_NAME, pts.projectName)
                                                .equalTo(PTS_DB.REALM_TASK_UID, it)
                                                .findFirst()
                                        if (!c!!.isPTScompleted) {
                                            flag = false
                                            break
                                        }
                                    }
                                }
                                if (!flag) {
                                    uidToExclude.add(pts.id)
                                }
                            }
                            uidToExclude.forEach { query.notEqualTo(PTS_DB.REALM_TASK_ID, it) }
                            if (orderDeadline) query.sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                            return realm.copyFromRealm(query.findAll())
                        }
                        "In Progress" -> {
                            val a = query.findAll()
                            val uidToExclude = TreeSet<Long>()
                            for (pts in a) {
                                val predecessors = pts.predecessorsIDlist
                                var flag: Boolean
                                if (predecessors.isEmpty()) {
                                    flag = true
                                } else {
                                    flag = true
                                    for (it in predecessors) {
                                        val c = realm.where(PTS_DB::class.java)
                                                .contains(PTS_DB.REALM_PROJECT_NAME, pts.projectName)
                                                .equalTo(PTS_DB.REALM_TASK_UID, it)
                                                .findFirst()
                                        if (!c!!.isPTScompleted) {
                                            flag = false
                                            break
                                        }
                                    }
                                }
                                if (!flag) {
                                    uidToExclude.add(pts.id)
                                }
                            }
                            uidToExclude.forEach { query.notEqualTo(PTS_DB.REALM_TASK_ID, it) }
                            if (orderDeadline) query.sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                            return realm.copyFromRealm(query.findAll())
                        }
                        "Complete" -> {
                            if (orderDeadline) query.sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                            return realm.copyFromRealm(query.findAll())
                        }
                        "Locked" -> {
                            val a = query.findAll()
                            val uidToExclude = TreeSet<Long>()
                            for (pts in a) {
                                val predecessors = pts.predecessorsIDlist
                                var flag: Boolean
                                if (predecessors.isEmpty()) {
                                    flag = true
                                } else {
                                    flag = true
                                    for (it in predecessors) {
                                        val c = realm.where(PTS_DB::class.java)
                                                .contains(PTS_DB.REALM_PROJECT_NAME, pts.projectName)
                                                .equalTo(PTS_DB.REALM_TASK_UID, it)
                                                .findFirst()
                                        if (!c!!.isPTScompleted) {
                                            flag = false
                                            break
                                        }
                                    }
                                }
                                if (flag) {
                                    uidToExclude.add(pts.id)
                                }
                            }
                            uidToExclude.forEach { query.notEqualTo(PTS_DB.REALM_TASK_ID, it) }
                            if (orderDeadline) query.sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                            return realm.copyFromRealm(query.findAll())
                        }
                    }
                    if (orderDeadline) query.sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                    return realm.copyFromRealm(query.findAll())
                }
            }
        } else {
            val email = LocalUserInfo.getUserEmail(appContext)
            if (searchByProgress.isEmpty()
                    && sortByProjectName.isEmpty() && sortByTaskName.isEmpty() && stepName.isEmpty()) {
                if (!orderDeadline) {
                    getDefaultInstance().use { realm ->
                        val ptsDB: List<PTS_DB> = realm.copyFromRealm(realm.where(PTS_DB::class.java)
                                .contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, email)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_PHONECALL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_EMAIL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_MEETING_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_TRAVEL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, 0L)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, 1L)
                                .and()
                                .notEqualTo(PTS_DB.REALM_IS_MILESTONE, true)
                                .findAll())
                        return ptsDB
                    }
                } else {
                    getDefaultInstance().use { realm ->
                        val ptsDB: List<PTS_DB> = realm.copyFromRealm(realm.where(PTS_DB::class.java)
                                .contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, email)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_PHONECALL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_EMAIL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_MEETING_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_TRAVEL_UID)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, 0L)
                                .and()
                                .notEqualTo(PTS_DB.REALM_TASK_UID, 1L)
                                .and()
                                .notEqualTo(PTS_DB.REALM_IS_MILESTONE, true)
                                .sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                                .findAll())
                        return ptsDB
                    }
                }
            } else {
                getDefaultInstance().use { realm ->
                    val query = realm.where(PTS_DB::class.java)
                    if (searchByProgress.isNotEmpty()) {
                        when (searchByProgress) {
                            "Ready to start" -> query.equalTo(PTS_DB.REALM_TASK_COMPLETED, false)
                            "In Progress" -> query.equalTo(PTS_DB.REALM_TASK_COMPLETED, false).and().isNotEmpty(PTS_DB.REALM_TASK_WORKING_USERNAME)
                            "Complete" -> query.equalTo(PTS_DB.REALM_TASK_COMPLETED, true)
                            "Locked" -> query.equalTo(PTS_DB.REALM_TASK_COMPLETED, false)
                        }
                    }
                    query.contains(PTS_DB.REALM_TASK_USERS_ASSIGNED, email)

                    if (sortByProjectName.isNotEmpty()) {
                        query.contains(PTS_DB.REALM_PROJECT_NAME, sortByProjectName)
                    }
                    if (sortByTaskName.isNotEmpty()) {
                        query.contains(PTS_DB.REALM_TASK_NAME, sortByTaskName)
                    }
                    if (stepName.isNotEmpty()) {
                        query.contains(PTS_DB.REALM_STEP_NAME, stepName)
                    }
                    query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_PHONECALL_UID)
                    query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_EMAIL_UID)
                    query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_MEETING_UID)
                    query.notEqualTo(PTS_DB.REALM_TASK_UID, PTS_DB.PTS_TRAVEL_UID)
                    query.notEqualTo(PTS_DB.REALM_TASK_UID, 0L)
                    when (searchByProgress) {
                        "Ready to start" -> {
                            val a = query.findAll()
                            val uidToExclude = TreeSet<Long>()
                            for (pts in a) {
                                val predecessors = pts.predecessorsIDlist
                                var flag: Boolean
                                if (predecessors.isEmpty()) {
                                    flag = true
                                } else {
                                    flag = true
                                    for (it in predecessors) {
                                        val c = realm.where(PTS_DB::class.java)
                                                .contains(PTS_DB.REALM_PROJECT_NAME, pts.projectName)
                                                .equalTo(PTS_DB.REALM_TASK_UID, it)
                                                .findFirst()
                                        if (!c!!.isPTScompleted) {
                                            flag = false
                                            break
                                        }
                                    }
                                }
                                if (!flag) {
                                    uidToExclude.add(pts.id)
                                }
                            }
                            uidToExclude.forEach { query.notEqualTo(PTS_DB.REALM_TASK_ID, it) }
                            if (orderDeadline) query.sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                            return realm.copyFromRealm(query.findAll())

                        }
                        "In Progress" -> {
                            val a = query.findAll()
                            val uidToExclude = TreeSet<Long>()
                            for (pts in a) {
                                val predecessors = pts.predecessorsIDlist
                                var flag: Boolean
                                if (predecessors.isEmpty()) {
                                    flag = true
                                } else {
                                    flag = true
                                    for (it in predecessors) {
                                        val c = realm.where(PTS_DB::class.java)
                                                .contains(PTS_DB.REALM_PROJECT_NAME, pts.projectName)
                                                .equalTo(PTS_DB.REALM_TASK_UID, it)
                                                .findFirst()
                                        if (!c!!.isPTScompleted) {
                                            flag = false
                                            break
                                        }
                                    }
                                }
                                if (!flag) {
                                    uidToExclude.add(pts.id)
                                }
                            }
                            uidToExclude.forEach { query.notEqualTo(PTS_DB.REALM_TASK_ID, it) }
                            if (orderDeadline) query.sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                            return realm.copyFromRealm(query.findAll())

                        }
                        "Complete" -> {
                            if (orderDeadline) query.sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                            return realm.copyFromRealm(query.findAll())
                        }
                        "Locked" -> {
                            val a = query.findAll()
                            val uidToExclude = TreeSet<Long>()
                            for (pts in a) {
                                val predecessors = pts.predecessorsIDlist
                                var flag: Boolean
                                if (predecessors.isEmpty()) {
                                    flag = true
                                } else {
                                    flag = true
                                    for (it in predecessors) {
                                        val c = realm.where(PTS_DB::class.java)
                                                .contains(PTS_DB.REALM_PROJECT_NAME, pts.projectName)
                                                .equalTo(PTS_DB.REALM_TASK_UID, it)
                                                .findFirst()
                                        if (!c!!.isPTScompleted) {
                                            flag = false
                                            break
                                        }
                                    }
                                }
                                if (flag) {
                                    uidToExclude.add(pts.id)
                                }
                            }
                            uidToExclude.forEach { query.notEqualTo(PTS_DB.REALM_TASK_ID, it) }
                            if (orderDeadline) query.sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                            return realm.copyFromRealm(query.findAll())
                        }
                    }
                    if (orderDeadline) query.sort(PTS_DB.REALM_PROJECT_DEADLINE, Sort.ASCENDING)
                    return realm.copyFromRealm(query.findAll())
                }
            }
        }
    }

    fun getAllTasks(): RealmResults<PTS_DB> {
        getDefaultInstance().use { realm ->
            return realm
                    .where(PTS_DB::class.java)
                    .findAll()
        }
    }

    fun getProgress(taskId: Long): Double? {
        var progress: Double? = null
        getTaskFromDb(taskId)?.let {
            progress = it.ptSprogress
        }
        return progress
    }

    fun getIsCompleted(taskId: Long): Boolean? {
        var progress: Boolean? = false
        getTaskFromDb(taskId)?.let {
            progress = it.isPTScompleted
        }
        return progress
    }

    fun getTimeBudget(taskId: Long): Double? {
        var progress: Double? = null
        getTaskFromDb(taskId)?.let {
            progress = it.timeBudget
        }
        return progress
    }

    fun getTaskByUidAndProjectName(uid: Long, projectName: String): PTS_DB? {
        getDefaultInstance().use { realm ->
            return realm.where(PTS_DB::class.java)
                    .equalTo(PTS_DB.REALM_TASK_UID, uid)
                    .equalTo(PTS_DB.REALM_PROJECT_NAME, projectName)
                    .findFirst()
        }
    }

    fun getMilestonesList(): RealmResults<PTS_DB>? {
        getDefaultInstance().use { realm ->
            val milestonesList = realm.where(PTS_DB::class.java)
                    .equalTo(PTS_DB.REALM_IS_MILESTONE, true)
                    .findAll()
            return milestonesList ?: null
        }
    }

    fun getTaskByWorkingUsername(email: String): RealmResults<PTS_DB>? {
        getDefaultInstance().use { realm ->
            val result = realm.where(PTS_DB::class.java)
                    .beginGroup()
                    .contains(PTS_DB.REALM_TASK_WORKING_USERNAME, email)
                    .endGroup()
                    .findAll()
            return result ?: null
        }
    }

    fun deleteDB(_class: Class<out RealmModel>?) {
        if (_class == null) return
        val realm = getDefaultInstance()
        realm.executeTransaction { realm1 ->
            realm1.delete(_class)
        }
    }

    fun saveRealmObject(
            projectName: String?,
            taskName: String?,
            stepName: String?,
            outlineLevel: Int,
            taskDeadline: Date?,
            usersAssigned: String?,
            predecessorsUIDlist: List<Long>,
            timeBudget: Double?,
            pTSProgress: Double?,
            isRollup: Boolean,
            isMilestone: Boolean,
            uid: Long,
            id: Long,
            isActive: Boolean) {
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                val ptsDb = PTS_DB()
                ptsDb.projectName = projectName
                ptsDb.taskName = taskName
                ptsDb.stepName = stepName
                ptsDb.outlineLevel = outlineLevel
                ptsDb.taskDeadline = taskDeadline
                ptsDb.usersAssigned = usersAssigned
                ptsDb.setPredecessorsUIDlist(predecessorsUIDlist)
                ptsDb.timeBudget = timeBudget
                ptsDb.setPTSProgress(pTSProgress)
                ptsDb.isRollup = isRollup
                ptsDb.isMilestone = isMilestone
                ptsDb.uid = uid
                ptsDb.id = id
                ptsDb.isActive = isActive
                bgRealm.copyToRealmOrUpdate(ptsDb)
            }
        }
    }

    fun savePhoneEmailMeeting(id: Long, projectName: String, resName: String) {
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                val phoneCall = createEmptyObject(id + 1, projectName, PROJECT_PHONECALL, resName)
                val email = createEmptyObject(id + 2, projectName, PROJECT_EMAIL, resName)
                val meeting = createEmptyObject(id + 3, projectName, PROJECT_MEETING, resName)
                val travel = createEmptyObject(id + 4, projectName, PROJECT_TRAVEL, resName)
                bgRealm.copyToRealmOrUpdate(phoneCall)
                bgRealm.copyToRealmOrUpdate(email)
                bgRealm.copyToRealmOrUpdate(meeting)
                bgRealm.copyToRealmOrUpdate(travel)
            }
        }
    }

    private fun createEmptyObject(id: Long?, projectName: String, taskName: String, workingUsername: String): PTS_DB {
        val ptsDb = PTS_DB()
        ptsDb.projectName = projectName
        ptsDb.taskName = taskName
        val onlyEmail: String = Utils.getEmail(workingUsername)
        ptsDb.taskWorkingUsername = onlyEmail
        ptsDb.isRollup = true
        id?.let { ptsDb.id = it }
        if (taskName.contains(PROJECT_PHONECALL)) ptsDb.uid = PTS_DB.PTS_PHONECALL_UID
        if (taskName.contains(PROJECT_EMAIL)) ptsDb.uid = PTS_DB.PTS_EMAIL_UID
        if (taskName.contains(PROJECT_MEETING)) ptsDb.uid = PTS_DB.PTS_MEETING_UID
        if (taskName.contains(PROJECT_TRAVEL)) ptsDb.uid = PTS_DB.PTS_TRAVEL_UID
        return ptsDb
    }

    private fun createEmptyObject(id: Long?, uid: Long, projectName: String, taskName: String, workingUsername: String): PTS_DB {
        val ptsDb = PTS_DB()
        ptsDb.projectName = projectName
        ptsDb.taskName = taskName
        val onlyEmail: String = Utils.getEmail(workingUsername)
        ptsDb.taskWorkingUsername = onlyEmail
        ptsDb.isRollup = true
        id?.let { ptsDb.id = it }
        ptsDb.uid = uid
        return ptsDb
    }

    private fun createEmptyObjectForUserActivity(id: Long?, uid: Long, projectName: String, eventType: String, workingUsername: String,
                                                 eventDateTime: String, date: Date, taskName: String, stepName: String, durationWorked: Long)
            : UserActivity_DB {
        val ptsDb = UserActivity_DB()
        ptsDb.projectName = projectName
        ptsDb.eventName = eventType
        ptsDb.dateTime = date
        ptsDb.taskName = taskName
        ptsDb.stepName = stepName
        ptsDb.eventDuration = durationWorked
        val onlyEmail: String = Utils.getEmail(workingUsername)
        ptsDb.userName = onlyEmail
        ptsDb.taskUID = uid
        id?.let { ptsDb.id = it }
        ptsDb.eventDateTime = eventDateTime
        return ptsDb
    }

    fun saveAsRead(taskId: Long) {
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                getTaskFromDb(taskId)?.let { task ->
                    task.isRead = true
                    bgRealm.copyToRealmOrUpdate(task)
                }
            }
        }
    }

    fun getAdditionalTime(taskId: Long): Double? {
        var additionalTime: Double? = null
        val task = getTaskFromDb(taskId)
        task?.let {
            additionalTime = it.taskAdditionalTime
        }
        return additionalTime
    }

    fun setAdditionalTime(taskId: Long, time: Double?) {
        getDefaultInstance().use { realm ->
            realm.executeTransactionAsync { bgRealm ->
                getTaskFromDb(taskId)?.let { task ->
                    task.taskAdditionalTime = time
                    task.isNotificationShowen = false
                }
            }
        }
    }

    fun fetchLastUserActivity(): UserActivity_DB? {
        val lastID = getUniqueId(UserActivity_DB::class.java)
        getDefaultInstance().use { realm ->
            return realm
                    .where(UserActivity_DB::class.java)
                    .equalTo(UserActivity_DB.ID_FIELD, lastID - 1)
                    .findFirst()
        }
    }

    fun handleRealmTransactions(context: Context, userEmail: String?, data: List<DocumentSnapshot>) {
        for (documentSnapshot1 in data) {
            val cloudUserWorking = documentSnapshot1.get(FirestorePTS_DB.FB_USER_WORKING) as String?
            val cloudUserAssigned = documentSnapshot1.get(FirestorePTS_DB.FB_USERS_ASSIGNED) as String?
            if (cloudUserAssigned!!.contains(userEmail!!) ||
                    cloudUserWorking!!.contains(userEmail)) continue
            val uid = documentSnapshot1.get(FirestorePTS_DB.FB_UID) as Long
            val projectName = documentSnapshot1.get(FirestorePTS_DB.FB_PROJECT_NAME) as String?
            if (projectName.isNullOrEmpty()) return
            val ptsDB = getPTSbyUIDProjectNameUserNameComaEmail(uid,
                    projectName, Utils.getEmail(cloudUserWorking)) ?: continue

            if (realm.isInTransaction) realm.commitTransaction()
            realm.beginTransaction()
            ptsDB.taskFinishDateTime = documentSnapshot1.get(FirestorePTS_DB.FB_ACTUAL_FINISH) as String?
            ptsDB.taskStartDateTime = documentSnapshot1.get(FirestorePTS_DB.FB_ACTUAL_START) as String?
            ptsDB.taskAdditionalTime = try {
                documentSnapshot1.get(FirestorePTS_DB.FB_ADDITIONAL_TIME).toString().toDouble()
            } catch (e: NumberFormatException) {
                0.0
            }
            ptsDB.isPTScompleted = documentSnapshot1.get(FirestorePTS_DB.FB_IS_COMPLETED) as Boolean
            ptsDB.projectName = documentSnapshot1.get(FirestorePTS_DB.FB_PROJECT_NAME) as String?
            ptsDB.taskNote = documentSnapshot1.get(FirestorePTS_DB.FB_TASK_NOTE) as String?
            ptsDB.setPTSProgress(try {
                documentSnapshot1.get(FirestorePTS_DB.FB_TIME_PROGRESS).toString().toDouble()
            } catch (e: java.lang.NumberFormatException) {
                0.0
            })
            ptsDB.taskWorkingUsername = Utils.getEmail(cloudUserWorking)
            ptsDB.usersAssigned = Utils.getEmail(cloudUserAssigned)

            ptsDB.isRead = if (documentSnapshot1.get(FirestorePTS_DB.FB_IS_READ) != null) {
                documentSnapshot1.get(FirestorePTS_DB.FB_IS_READ) as Boolean
            } else {
                false
            }

            realm.copyToRealmOrUpdate(ptsDB)
            realm.commitTransaction()
        }

        setCurrentPossibleTasks(getNumPossibleStartTask(context))
        if (isPossibleTasksInc()) {
            sendNotification(context, currentPossibleTasks!!, (currentPossibleTasks
                    ?: 0) - (prevPossibleTasks ?: 0))
        }

    }

    companion object {
        private var INSTANCE: RealmHandler? = null
        private val singleThreadDispatcher = newSingleThreadContext("RealmThread")
        val job = Job()
        val ioScope = CoroutineScope(singleThreadDispatcher + job)

        fun getInstance(): RealmHandler {
            if (INSTANCE == null) {
                INSTANCE = RealmHandler()
            }
            return INSTANCE as RealmHandler
        }

        const val PROJECT_PHONECALL = "PHONECALL"
        const val PROJECT_EMAIL = "EMAIL"
        const val PROJECT_MEETING = "MEETING"
        const val PROJECT_TRAVEL = "TRAVEL"
    }
}
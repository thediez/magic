package com.timejet.bio.timejet

import android.content.Context
import com.example.timejet.repository.models.PTS_DB.*
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.repository.models.PTS_DB.*
import com.timejet.bio.timejet.utils.LocalUserInfo
import com.timejet.bio.timejet.utils.Utils.Companion.formatTime3digit
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.util.*

fun writeXLSXFile(appContext: Context) : String? {

    val realmHandler = RealmHandler.getInstance()

    val userEmail = LocalUserInfo.getUserEmail(appContext)

    val excelReportPath2File = appContext.filesDir.path + "/" + "Report_" + userEmail + ".xlsx"

    val excelReport = File(excelReportPath2File)

    val wb = XSSFWorkbook()
    var cell: XSSFCell

    var rowNum = 0

    val sheetName2 = "Report"//name of sheet
    val sheet2 = wb.createSheet(sheetName2)

    var row2: XSSFRow

    val columnNames2 = Arrays.asList(
            "ID", "UID", "Project Name", "Task Name",
            "Step Name", "Task Assigned", "Task Working", "Task Completed",
            "Time Budget", "Actual Work", "Task Deadline", "Task Note", "Actual Start", "Actual Finish",
            "Remaining Work", "Additional Time", "Is Active")
    var column2 = 0
    row2 = sheet2.createRow(rowNum++)
    for (listNum in columnNames2) {
        cell = row2.createCell(column2++)
        cell.setCellValue(listNum)
    }
    val ptsDbs = realmHandler.getAllTasks()
    val listOfPTS = ArrayList(ptsDbs)


    for (ptsDb in listOfPTS) {
        val projectName = ptsDb.projectName
        val uid = ptsDb.uid
        val id = ptsDb.id
        if(projectName.isEmpty() && ptsDb.taskName.isEmpty()){ continue }

        row2 = sheet2.createRow(rowNum++)

        // ID
        cell = row2.createCell(0)
        cell.setCellValue(id.toDouble())

        // UID
        cell = row2.createCell(1)
        cell.setCellValue(uid.toDouble())

        // Project Name
        cell = row2.createCell(2)
        cell.setCellValue(projectName)

        // Task Name
        cell = row2.createCell(3)
        cell.setCellValue(ptsDb.taskName)

        // Step Name
        cell = row2.createCell(4)
        cell.setCellValue(ptsDb.stepName)

        // Users Assigned
        cell = row2.createCell(5)
        cell.setCellValue(ptsDb.usersAssigned)

        // Task User Working
        cell = row2.createCell(6)
        cell.setCellValue(ptsDb.taskWorkingUsername)

        // Completed of Not
        cell = row2.createCell(7)
        if (ptsDb.isPTScompleted) cell.setCellValue(appContext.getString(R.string.completed))

        // Time Budged
        cell = row2.createCell(8)
        cell.setCellValue(ptsDb.timeBudget ?: 0.0)

        // Time Progress
        cell = row2.createCell(9)

        var dPTSprogress: Double = ptsDb.ptSprogress?: -1.0

        if (uid == PTS_PHONECALL_UID || uid == PTS_EMAIL_UID || uid == PTS_MEETING_UID || uid == PTS_TRAVEL_UID)
            cell.setCellValue(formatTime3digit(if(dPTSprogress != -1.0) dPTSprogress else 0.0).toDouble())

        if (!ptsDb.isRollup && uid != 0L && dPTSprogress != -1.0)
            cell.setCellValue(formatTime3digit(dPTSprogress).toDouble())

        // Task Deadline
        cell = row2.createCell(10)
        var taskDeadLine = if (ptsDb.taskDeadline == null) null else ptsDb.taskDeadline.toString()
        cell.setCellValue(taskDeadLine)

        // Task Note
        cell = row2.createCell(11)
        cell.setCellValue(ptsDb.taskNote)

        // actual start date
        cell = row2.createCell(12)
        cell.setCellValue(ptsDb.taskStartDateTime)

        // actual finish date
        cell = row2.createCell(13)
        cell.setCellValue(ptsDb.taskFinishDateTime)

        // remaing time
        cell = row2.createCell(14)
        cell.setCellValue(ptsDb.taskRemainingTime ?: 0.0)

        // additional time
        cell = row2.createCell(15)
        cell.setCellValue(ptsDb.taskAdditionalTime ?: 0.0)

        // is active
        cell = row2.createCell(16)
        cell.setCellValue(ptsDb.isActive)
    }

    val size = 5000
    try {
        for (i in 0..19) sheet2?.setColumnWidth(i, size)
    } catch (e: Exception) {
    }

    try {
        val fileOut = FileOutputStream(excelReportPath2File)
        wb.write(fileOut)
        fileOut.flush()
        fileOut.close()
    } catch (e: Exception) {
        return null
    }

    return excelReportPath2File
}
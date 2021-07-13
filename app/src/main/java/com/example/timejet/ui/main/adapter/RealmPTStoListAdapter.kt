package com.timejet.bio.timejet.ui.main.adapter

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import android.widget.TextView
import com.example.timejet.repository.models.PTS_DB
import com.timejet.bio.timejet.R
import com.timejet.bio.timejet.config.NEW_TASK_THRESHOLD
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.utils.Utils
import com.timejet.bio.timejet.utils.Utils.Companion.calculateTime
import io.realm.OrderedRealmCollection
import io.realm.RealmBaseAdapter
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class RealmPTStoListAdapter(realmResults: OrderedRealmCollection<PTS_DB>, val context: Context) :
    RealmBaseAdapter<PTS_DB>(realmResults), ListAdapter {
    private val LOG_TAG = "RealmPTStoListAdapter"

    init {
        Log.d(LOG_TAG, "realmResults.size: ${realmResults.size}")
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val convertView = LayoutInflater.from(this.context)
            .inflate(R.layout.row, parent, false)

        // Get PTS_DB Task Name
        if (adapterData != null) {
            val item = adapterData!![position]

            var s: String? = ""
            s = item.taskName
            val tv = convertView!!.findViewById<TextView>(R.id.tvTaskName)
            tv.text = s

            // Get PTS_DB step name
            s = item.stepName
            val tvStepName = convertView.findViewById<TextView>(R.id.tvStepName)
            tvStepName.text = s


            // Get PTS_DB Time Budget
            s = Utils.calculateTime(item.timeBudget)
            val timeBudget = context.getString(R.string.time_budget)
            val tvTimeBudget = convertView.findViewById<TextView>(R.id.textViewTimeBudget)
            tvTimeBudget.text = String.format("%s %s", timeBudget, s)
            if (s == null || s == "null") tvTimeBudget.text = ""


            // Duration, Time Progress
            s = item.ptSprogress.toString()
            val tvTimeProgress = convertView.findViewById<TextView>(R.id.tvTimeProgress)
            val duration2 = context.getString(R.string.duration_hrs)
            if (s!!.isEmpty()) s = "00:00"
            var shortStr = ""
            try {
                shortStr = calculateTime(java.lang.Double.parseDouble(s))
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }

            tvTimeProgress.text = String.format("%s %s", duration2, shortStr)


            // Assigned username
            val assignedUsername = item.usersAssigned
            val tvUsersAssigned2 = convertView.findViewById<TextView>(R.id.tvUsersAssigned)
            tvUsersAssigned2.text = assignedUsername

            if (item.isMilestone) {
                tvTimeBudget.text = "This is the Milestone"
                tvTimeProgress.text = ""
            }

            s = item.uid.toString()
            val tvTaskID = convertView.findViewById<TextView>(R.id.tvTaskID)
            tvTaskID.text = String.format("UID: %s", s)

            // Deadline
            s = item.taskDeadline.toString()
            val textViewDeadline = convertView.findViewById<TextView>(R.id.textViewDeadline)
            textViewDeadline.text = String.format("%s %s", context.getString(R.string.deadline), s)
            if (s == "null") textViewDeadline.text = ""

            // tvPrjName with underline
            s = item.projectName
            val tvPrjname = convertView.findViewById<TextView>(R.id.tvPrjName)
            tvPrjname.text = String.format("%s", s)
            tvPrjname.paintFlags = tvPrjname.paintFlags or Paint.UNDERLINE_TEXT_FLAG

            // Predecessors list UID
            val prjName = item.projectName
            val predecessorsUIDlist = item.predecessorsIDlist
            val parentsUIDlist_isCompletedYN = RealmHandler.getInstance().getAllSublist(predecessorsUIDlist, prjName)
            val textViewPredecessors = convertView.findViewById<TextView>(R.id.textViewPredecessors)
            textViewPredecessors.text = String.format(
                "%s %s",
                context.getString(R.string.predecessors_uid),
                "\n" + parentsUIDlist_isCompletedYN
            )
            if (parentsUIDlist_isCompletedYN.toString().isEmpty()) textViewPredecessors.text = ""

            var tvIsCompleted = convertView.findViewById<TextView>(R.id.tvIsCompleted)
            tvIsCompleted.text = ""
            var textIsCompleted = ""

            convertView.findViewById<TextView>(R.id.tvIsRead).let {
                when (item.isRead) {
                    true -> {
                        it.text = it.context.getString(R.string.readed)
                        it.typeface = Typeface.DEFAULT
                    }

                    false -> {
                        it.text = it.context.getString(R.string.notReaded)
                        it.typeface = Typeface.DEFAULT_BOLD
                    }
                }
            }

            if (item.taskStartDateTime.isNotEmpty()) {
                try {
                    val date: Date = SimpleDateFormat("dd.MM.yyyy HH:mm:ss Z")
                        .parse(item.taskStartDateTime)
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_MONTH, NEW_TASK_THRESHOLD)
                    val newTaskThreshold = cal.timeInMillis
                    cal.time = date
                    val taskStartTimeMils = cal.timeInMillis
                    if (taskStartTimeMils > newTaskThreshold) {
                    }
                } catch (ex: ParseException) {
                    Log.d(LOG_TAG, "taskStartDateTime parsing: ${ex.message}")
                }
            }

            convertView.setBackgroundColor(Color.LTGRAY)

            if (parentsUIDlist_isCompletedYN.toString().contains("[Y]") && !parentsUIDlist_isCompletedYN.toString().contains(
                    "[N]"
                )
            ) {
                convertView.setBackgroundColor(Color.YELLOW)

                textIsCompleted = context.resources.getString(R.string.not_completed)
            }

            if (assignedUsername != "" && predecessorsUIDlist.size == 0) {
                convertView.setBackgroundColor(Color.rgb(250, 250, 145))
                textIsCompleted = context.resources.getString(R.string.not_completed)
            }
            val duration = item.ptSprogress
            if (duration != null) {
                textIsCompleted = context.resources.getString(R.string.in_progress)
            }
            tvIsCompleted.text = textIsCompleted
            if (textIsCompleted.isEmpty())
                tvIsCompleted.background = null
            else
                tvIsCompleted.background = context.resources.getDrawable(R.drawable.rectangle)
            val isCmpltd = item.isPTScompleted
            if (isCmpltd) {
                convertView.setBackgroundColor(Color.rgb(150, 250, 150))

                tvIsCompleted = convertView.findViewById(R.id.tvIsCompleted)
                tvIsCompleted.setText(R.string.task_complete)

            }
            if (parentsUIDlist_isCompletedYN.toString().contains("[N]")) {
                convertView.setBackgroundColor(Color.rgb(240, 80, 85))
                tvIsCompleted.setText(R.string.not_completed)
                tvIsCompleted.background = context.resources.getDrawable(R.drawable.rectangle)
            }
        }
        return convertView!!
    }
}

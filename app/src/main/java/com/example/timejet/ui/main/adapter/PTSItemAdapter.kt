package com.timejet.bio.timejet.ui.main.adapter

import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.timejet.repository.models.PTS_DB
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import com.timejet.bio.timejet.R
import com.timejet.bio.timejet.config.NEW_TASK_THRESHOLD
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.ui.setVisibleOrGone
import com.timejet.bio.timejet.utils.Utils
import kotlinx.android.synthetic.main.row.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.lang.Double
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

class PTSItemAdapter(private val onItemClickListener: OnItemClickListener?) :
    RecyclerView.Adapter<PTSItemAdapter.PTSViewHolder>() {
    companion object {
        val LOG_TAG: String = this::class.java.simpleName
    }

    interface OnItemClickListener {
        fun onItemClickListener(item: PTS_DB?, position: Int)
    }

    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    private var ptsList: List<PTS_DB> = ArrayList()

    class PTSViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTaskName: TextView = itemView.tvTaskName
        val tvStepName: TextView = itemView.tvStepName
        val tvTimeBudget: TextView = itemView.textViewTimeBudget
        val tvTimeBudgetAdditionalTime: TextView = itemView.textViewTimeBudgetAdditionalTime
        val tvTimeProgress: TextView = itemView.tvTimeProgress
        val tvUsersAssigned2: TextView = itemView.tvUsersAssigned
        val tvIsComplete: TextView = itemView.tvIsCompleted
        val tvTaskId: TextView = itemView.tvTaskID
        val tvDeadLine: TextView = itemView.textViewDeadline
        val tvPredecessors: TextView = itemView.textViewPredecessors
        val tvProjectName: TextView = itemView.tvPrjName
        val tvIsRead: TextView = itemView.tvIsRead
        val card: CardView = itemView.card
        val imgNotReaded: ImageView = itemView.imageNotReadCard
        val imgReaded: ImageView = itemView.imageReadCard
        val imgCheckIcon: ImageView = itemView.imageCheckIcon
        val shadowImage: ImageView = itemView.shadowImage
        val tvIsNotRead: TextView = itemView.tvIsNotRead
        val circularProgressBar: CircularProgressBar = itemView.circularProgressBar
        val procent : TextView = itemView.procent
        val circle: ConstraintLayout = itemView.circle
    }

    fun updateDate(list: List<PTS_DB>) {
        ptsList = list;
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PTSViewHolder {
        val view: View = LayoutInflater.from(parent.context).inflate(R.layout.row, parent, false)
        view.layoutParams =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return PTSViewHolder(view)
    }

    override fun getItemCount(): Int = ptsList.size

    override fun onBindViewHolder(holder: PTSViewHolder, position: Int) {
        if (position != RecyclerView.NO_POSITION) {
            val item = ptsList[position]
            val context = holder.itemView.context
            var s: String? = ""

            holder.itemView.setOnClickListener {
                Log.d(LOG_TAG, "Click on position [$position] item id ${item.id}")
                onItemClickListener?.let {
                    it.onItemClickListener(ptsList[position], position)
                }
            }

            holder.tvTaskName.text = item.taskName
            holder.tvStepName.text = item.stepName
            holder.tvTimeBudget.text =
                if (item.timeBudget != null) Utils.calculateTime(item.timeBudget) else ""
            holder.tvTimeBudgetAdditionalTime.text = if (item.taskAdditionalTime != null) {
                "+" + Utils.calculateTime(item.taskAdditionalTime)
            } else ""
            s = if(item.ptSprogress == null){
                ""
            } else{
                Utils.calculateTime(item.ptSprogress)
            }

            if (s.isEmpty()) s = "00:00"
            var shortStr = ""
            try {
                shortStr = s
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }

            holder.tvTimeProgress.text = String.format("%s", shortStr)


            // Assigned username
            val assignedUsername = item.usersAssigned
            holder.tvUsersAssigned2.text = assignedUsername
            if (item.isMilestone) {
                holder.tvTimeBudget.text = "This is the Milestone"
                holder.tvTimeProgress.text = ""
                holder.tvUsersAssigned2.text = ""
            }

            s = item.uid.toString()
            holder.tvTaskId.text = String.format("UID: %s", s)

            // Deadline
            if(item.taskDeadline != null){
                s = item.taskDeadline.toString()
                holder.tvDeadLine.text = if (!s.isNullOrEmpty() && s != "null" ) String.format("%s", s) else ""
            } else {
                s = ""
                holder.tvDeadLine.text = if (!s.isNullOrEmpty() && s != "null" ) String.format("%s", s) else ""
        }

            // tvPrjName with underline
            s = item.projectName
            holder.tvProjectName.text = String.format("%s", s)
            holder.tvProjectName.paintFlags = holder.tvProjectName.paintFlags or Paint.UNDERLINE_TEXT_FLAG

            // Predecessors list UID
            val prjName = item.projectName
            val predecessorsUIDlist = item.predecessorsIDlist

            holder.tvIsComplete.text = ""
            holder.tvIsComplete.background = null

            RealmHandler.ioScope.launch {
                val parentsIsCompletedPair = RealmHandler.getInstance().isAllPredecessorsCompletedWithString(predecessorsUIDlist, prjName)
                val parentsIsCompleted = parentsIsCompletedPair.first
                val parentsUIDlist_isCompletedYN = parentsIsCompletedPair.second
                uiScope.launch {
                    holder.tvPredecessors.text = if (parentsUIDlist_isCompletedYN.isEmpty()) ""
                        else String.format("%s",parentsUIDlist_isCompletedYN.trim())
                    var textIsCompleted = ""

                    holder.tvIsRead.let {
                        when (item.isRead) {
                            true -> {
                                it.text = it.context.getString(R.string.readed)
                                val typeface = ResourcesCompat.getFont(context, R.font.barlow_extra_bold)
                                it.setTypeface(typeface);
                                val params = it.getLayoutParams() as ConstraintLayout.LayoutParams
                                params.horizontalBias = 0.5f
                                params.verticalBias = 0.5f
                                it.setLayoutParams(params)
                                holder.imgReaded.setVisibleOrGone(true)
                                holder.imgNotReaded.setVisibleOrGone(false)
                                holder.tvIsRead.setVisibleOrGone(true)
                                holder.tvIsNotRead.setVisibleOrGone(false)
                            }

                            false -> {
                                it.text = it.context.getString(R.string.notReaded)
                                holder.imgReaded.setVisibleOrGone(false)
                                holder.imgNotReaded.setVisibleOrGone(true)
                                holder.tvIsRead.setVisibleOrGone(false)
                                holder.tvIsNotRead.setVisibleOrGone(true)
                            }
                        }
                    }

                    // состояние inProgress если длительность не ноль
                    val duration = item.ptSprogress
                    if (duration != null) {
                        textIsCompleted = context.resources.getString(R.string.in_progress)
                    }

                    // Set 'NEW' visible is taskStartDateTime > then currentTime - 2 days
                    if (item.taskStartDateTime.isNotEmpty()) {
                        try {
                            val date: Date = SimpleDateFormat("dd.MM.yyyy HH:mm:ss Z")
                                .parse(item.taskStartDateTime)
                            val cal = Calendar.getInstance()
                            cal.add(Calendar.DAY_OF_MONTH, NEW_TASK_THRESHOLD)
                            val newTaskThreshold = cal.timeInMillis
                            cal.time = date
                            val taskStartTimeMils = cal.timeInMillis
                        } catch (ex: ParseException) {
                            Log.d(LOG_TAG, "taskStartDateTime parsing: ${ex.message}")
                        }
                    }

                    if (item.isPTScompleted) {
                        holder.card.setCardBackgroundColor(context.resources.getColor(R.color.green))
                        textIsCompleted = context.resources.getString(R.string.task_complete)
                        holder.tvIsComplete.setTextColor(context.resources.getColor(R.color.green))
                        holder.imgCheckIcon.setVisibleOrGone(true)
                        holder.shadowImage.setImageResource(R.drawable.shadow_green)
                        holder.circularProgressBar.apply {
                            progress = 100f
                            progressBarColor = context.resources.getColor(R.color.green)
                            backgroundProgressBarColor = context.resources.getColor(R.color.transparentGreen)
                            // Set Progress Max
                            progressMax = 100f
                            // Other
                            startAngle = 0f
                        }
                        holder.procent.text = "100%"
                        holder.procent.setBackgroundColor(context.resources.getColor(R.color.green))
                        val background = holder.circle.background as GradientDrawable
                        background.mutate()
                        background.setColor(context.resources.getColor(R.color.green))
                    } else if (item.isRollup) {
                        holder.card.setCardBackgroundColor(context.resources.getColor(R.color.likeOrange))
                        textIsCompleted = context.resources.getString(R.string.task_can_start)
                        holder.imgCheckIcon.setVisibleOrGone(false)
                        holder.tvIsComplete.setTextColor(context.resources.getColor(R.color.likeOrange))
                        holder.shadowImage.setImageResource(R.drawable.shadow_orange)
                        holder.circularProgressBar.apply {
                            progress = 0f
                            progressBarColor = context.resources.getColor(R.color.likeOrange)
                            backgroundProgressBarColor = context.resources.getColor(R.color.transparentLikeOrange)
                            // Set Progress Max
                            progressMax = 100f
                            // Other
                            startAngle = 0f
                        }
                        holder.procent.text = "0%"
                        holder.procent.setBackgroundColor(context.resources.getColor(R.color.likeOrange))
                        val background = holder.circle.background as GradientDrawable
                        background.mutate()
                        background.setColor(context.resources.getColor(R.color.likeOrange))
                    } else {
                        if (assignedUsername != "") {
                            if (predecessorsUIDlist.size == 0) {
                                holder.card.setCardBackgroundColor(context.resources.getColor(R.color.myYellow)) //Color.rgb(250, 250, 145))
                                holder.imgCheckIcon.setVisibleOrGone(false)
                                holder.shadowImage.setImageResource(R.drawable.shadow_gold)
                                textIsCompleted = if(item.taskStartDateTime !=""){
                                    context.resources.getString(R.string.task_in_progress)
                                } else{
                                    context.resources.getString(R.string.not_completed)
                                }
                                holder.tvIsComplete.setTextColor(context.resources.getColor(R.color.myYellow))
                                holder.circularProgressBar.apply {
                                    if(item.taskAdditionalTime == null){
                                        if(item.timeBudget == null || item.timeBudget == 0.0){
                                            progress = 0F
                                        } else {
                                            progress = (100 -((item.taskRemainingTime/(item.timeBudget))*100)).toFloat()
                                        }
                                    } else {
                                        if(item.timeBudget == null || item.timeBudget == 0.0){
                                            progress = 0F
                                        } else {
                                            progress = (100 -((item.taskRemainingTime/(item.timeBudget + item.taskAdditionalTime))*100)).toFloat()
                                        }
                                    }
                                    progressBarColor = context.resources.getColor(R.color.myYellow)
                                    backgroundProgressBarColor = context.resources.getColor(R.color.transparentMyYellow)
                                    // Set Progress Max
                                    progressMax = 100f
                                    // Other
                                    startAngle = 0f
                                }
                                if(item.taskAdditionalTime == null){
                                    if(item.timeBudget == null || item.timeBudget == 0.0){
                                        holder.procent.text = "0%"
                                    } else {
                                        holder.procent.text = ((100 -((item.taskRemainingTime/(item.timeBudget))*100)).roundToInt()).toString() + "%"
                                    }
                                } else {
                                    if(item.timeBudget == null || item.timeBudget == 0.0){
                                        holder.procent.text = "0%"
                                    } else {
                                        holder.procent.text = ((100 -((item.taskRemainingTime/(item.timeBudget + item.taskAdditionalTime))*100)).roundToInt()).toString() + "%"
                                    }
                                }

                                holder.procent.setBackgroundColor(context.resources.getColor(R.color.myYellow))
                                val background = holder.circle.background as GradientDrawable
                                background.mutate()
                                background.setColor(context.resources.getColor(R.color.myYellow))
                            } else {
                                if (parentsIsCompleted) {
                                    holder.card.setCardBackgroundColor(context.resources.getColor(R.color.myYellow))
                                    holder.shadowImage.setImageResource(R.drawable.shadow_gold)
                                    holder.imgCheckIcon.setVisibleOrGone(false)
                                    textIsCompleted = if(item.taskStartDateTime !=""){
                                        context.resources.getString(R.string.task_in_progress)
                                    } else{
                                        context.resources.getString(R.string.task_can_start)
                                    }
                                    holder.tvIsComplete.setTextColor(context.resources.getColor(R.color.myYellow))
                                    holder.circularProgressBar.apply {
                                        if(item.taskAdditionalTime == null){
                                            if(item.timeBudget == null || item.timeBudget == 0.0){
                                                progress = 0F
                                            } else {
                                                progress = (100 -((item.taskRemainingTime/(item.timeBudget))*100)).toFloat()
                                            }
                                        } else {
                                            if(item.timeBudget == null || item.timeBudget == 0.0){
                                                progress = 0F
                                            } else {
                                                progress = (100 -((item.taskRemainingTime/(item.timeBudget + item.taskAdditionalTime))*100)).toFloat()
                                            }
                                        }
//                                        progress = 0f
                                        progressBarColor = context.resources.getColor(R.color.myYellow)
                                        backgroundProgressBarColor = context.resources.getColor(R.color.transparentMyYellow)
                                        // Set Progress Max
                                        progressMax = 100f
                                        // Other
                                        startAngle = 0f
                                    }
                                    if(item.taskAdditionalTime == null){
                                        if(item.timeBudget == null || item.timeBudget == 0.0){
                                            holder.procent.text = "0%"
                                        } else {
                                            holder.procent.text = ((100 -((item.taskRemainingTime/(item.timeBudget))*100)).roundToInt()).toString() + "%"
                                        }
                                    } else {
                                        if(item.timeBudget == null || item.timeBudget == 0.0){
                                            holder.procent.text = "0%"
                                        } else {
                                            holder.procent.text = ((100 - ((item.taskRemainingTime / (item.timeBudget + item.taskAdditionalTime)) * 100)).roundToInt()).toString() + "%"
                                        }
                                    }
                                    holder.procent.setBackgroundColor(context.resources.getColor(R.color.myYellow))
                                    val background = holder.circle.background as GradientDrawable
                                    background.mutate()
                                    background.setColor(context.resources.getColor(R.color.myYellow))
                                } else {
                                    holder.card.setCardBackgroundColor(context.resources.getColor(R.color.red)) //Color.rgb(240, 80, 85))
                                    holder.shadowImage.setImageResource(R.drawable.shadow_red)
                                    holder.imgCheckIcon.setVisibleOrGone(false)
                                    textIsCompleted = context.resources.getString(R.string.not_completed)
                                    holder.tvIsComplete.setTextColor(context.resources.getColor(R.color.red))
                                    holder.circularProgressBar.apply {
                                        //                            progress = (100 -((viewModel.getRemainingTime()/(viewModel.getTimeBuget() + viewModel.getAddTime()))*100)).toFloat()
                                        progress = 0f
                                        progressBarColor = context.resources.getColor(R.color.red)
                                        backgroundProgressBarColor = context.resources.getColor(R.color.transparentRed)
                                        // Set Progress Max
                                        progressMax = 100f
                                        // Other
                                        startAngle = 0f
                                    }
                                    holder.procent.text = "0%"
                                    holder.procent.setBackgroundColor(context.resources.getColor(R.color.red))
                                    val background = holder.circle.background as GradientDrawable
                                    background.mutate()
                                    background.setColor(context.resources.getColor(R.color.red))
                                }
                            }
//                            holder.tvIsComplete.background = context.resources.getDrawable(R.drawable.rectangle)
                        } else {
                            if (predecessorsUIDlist.size == 0) {
                                holder.card.setCardBackgroundColor(context.resources.getColor(R.color.likeOrange))
                                holder.shadowImage.setImageResource(R.drawable.shadow_orange)
                                holder.imgCheckIcon.setVisibleOrGone(false)
                                textIsCompleted = context.resources.getString(R.string.task_can_start)
                                holder.tvIsComplete.setTextColor(context.resources.getColor(R.color.likeOrange))
                                holder.circularProgressBar.apply {
                                    //                            progress = (100 -((viewModel.getRemainingTime()/(viewModel.getTimeBuget() + viewModel.getAddTime()))*100)).toFloat()
                                    progress = 0f
                                    progressBarColor = context.resources.getColor(R.color.likeOrange)
                                    backgroundProgressBarColor = context.resources.getColor(R.color.transparentLikeOrange)
                                    // Set Progress Max
                                    progressMax = 100f
                                    // Other
                                    startAngle = 0f
                                }
                                holder.procent.text = "0%"
                                holder.procent.setBackgroundColor(context.resources.getColor(R.color.likeOrange))
                                val background = holder.circle.background as GradientDrawable
                                background.mutate()
                                background.setColor(context.resources.getColor(R.color.likeOrange))
                            } else {
                                if (parentsIsCompleted) {
                                    holder.card.setCardBackgroundColor(context.resources.getColor(R.color.myYellow))
                                    holder.shadowImage.setImageResource(R.drawable.shadow_gold)
                                    holder.imgCheckIcon.setVisibleOrGone(false)
                                    textIsCompleted = if(item.taskStartDateTime !=""){
                                        context.resources.getString(R.string.task_in_progress)
                                    } else{
                                        context.resources.getString(R.string.not_completed)
                                    }
                                    holder.tvIsComplete.setTextColor(context.resources.getColor(R.color.myYellow))
                                    holder.circularProgressBar.apply {
                                        if(item.taskAdditionalTime == null){
                                            if(item.timeBudget == null || item.timeBudget == 0.0){
                                                progress = 0F
                                            } else {
                                                progress = (100 -((item.taskRemainingTime/(item.timeBudget))*100)).toFloat()
                                            }
                                        } else {
                                            if(item.timeBudget == null || item.timeBudget == 0.0){
                                                progress = 0F
                                            } else {
                                                progress = (100 -((item.taskRemainingTime/(item.timeBudget + item.taskAdditionalTime))*100)).toFloat()
                                            }
                                        }
//                                        progress = 0f
                                        progressBarColor = context.resources.getColor(R.color.myYellow)
                                        backgroundProgressBarColor = context.resources.getColor(R.color.transparentMyYellow)
                                        // Set Progress Max
                                        progressMax = 100f
                                        // Other
                                        startAngle = 0f
                                    }
                                    if(item.taskAdditionalTime == null){
                                        if(item.timeBudget == null || item.timeBudget == 0.0){
                                            holder.procent.text = "0%"
                                        } else {
                                            holder.procent.text = ((100 -((item.taskRemainingTime/(item.timeBudget))*100)).roundToInt()).toString() + "%"
                                        }
                                    } else {
                                        if(item.timeBudget == null || item.timeBudget == 0.0){
                                            holder.procent.text = "0%"
                                        } else {
                                            holder.procent.text = ((100 -((item.taskRemainingTime/(item.timeBudget + item.taskAdditionalTime))*100)).roundToInt()).toString() + "%"
                                        }
                                    }
                                    holder.procent.setBackgroundColor(context.resources.getColor(R.color.myYellow))
                                    val background = holder.circle.background as GradientDrawable
                                    background.mutate()
                                    background.setColor(context.resources.getColor(R.color.myYellow))
                                } else {
                                    holder.card.setCardBackgroundColor(context.resources.getColor(R.color.red)) //Color.rgb(240, 80, 85))
                                    holder.shadowImage.setImageResource(R.drawable.shadow_red)
                                    holder.imgCheckIcon.setVisibleOrGone(false)
                                    textIsCompleted = context.resources.getString(R.string.not_completed)
                                    holder.tvIsComplete.setTextColor(context.resources.getColor(R.color.red))
                                    holder.circularProgressBar.apply {
                                        //                            progress = (100 -((viewModel.getRemainingTime()/(viewModel.getTimeBuget() + viewModel.getAddTime()))*100)).toFloat()
                                        progress = 0f
                                        progressBarColor = context.resources.getColor(R.color.red)
                                        backgroundProgressBarColor = context.resources.getColor(R.color.transparentRed)
                                        // Set Progress Max
                                        progressMax = 100f
                                        // Other
                                        startAngle = 0f
                                    }
                                    holder.procent.text = "0%"
                                    holder.procent.setBackgroundColor(context.resources.getColor(R.color.red))
                                    val background = holder.circle.background as GradientDrawable
                                    background.mutate()
                                    background.setColor(context.resources.getColor(R.color.red))
                                }
                            }
                        }
                    }
                    holder.tvIsComplete.text = textIsCompleted
                }
            }
        }
    }

}
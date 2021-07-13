import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.timejet.repository.models.UserActivity_DB
import kotlinx.android.synthetic.main.statistic_row.view.*
import java.util.concurrent.TimeUnit

class RVAdapter(list: List<UserActivity_DB>) : RecyclerView.Adapter<RVAdapter.ViewHolder>() {
    private val list: List<UserActivity_DB>

    init {
        this.list = list
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal var taskNameStatistic: TextView
        internal var stepNameStatistic: TextView
        internal var textHour: TextView
        internal var textMinute: TextView
        internal var cv: CardView

        init {
            taskNameStatistic = itemView.statisticTaskName as TextView
            stepNameStatistic = itemView.statisticStepName as TextView
            textHour = itemView.textHour as TextView
            textMinute = itemView.textMinute as TextView
            cv = itemView.cardTest as CardView
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.timejet.bio.timejet.R.layout.statistic_row, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val userActivity = list.get(position)
        holder.taskNameStatistic.text = userActivity.projectName
        if(userActivity.taskUID < 0L){
            holder.stepNameStatistic.text = userActivity.eventName
        } else {
            holder.stepNameStatistic.text = userActivity.stepName
        }
        if (userActivity.eventDuration != null) {

            val minutes = TimeUnit.MILLISECONDS.toMinutes(userActivity.eventDuration)
            if (minutes == 0L) {
                holder.textMinute.text = "01"
                holder.textHour.text = "00"
            } else {
                if (minutes >= 60L) {
                    val hours = TimeUnit.MINUTES.toHours(minutes)
                    val minutesAfter = minutes - (hours * 60L)
                    if (hours >= 10L) {
                        holder.textHour.text = hours.toString()
                    } else {
                        holder.textHour.text = "0" + hours.toString()
                    }
                    if (minutesAfter >= 10L) {
                        holder.textMinute.text = minutesAfter.toString()
                    } else {
                        holder.textMinute.text = "0" + minutesAfter.toString()
                    }
                } else {
                    holder.textHour.text = "00"
                    if (minutes < 10L) {
                        holder.textMinute.text = "0" + minutes.toString()
                    } else {
                        holder.textMinute.text = minutes.toString()
                    }
                }
            }
        }
    }

}
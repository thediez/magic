package com.timejet.bio.timejet.ui.statistic

import RVAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.timejet.repository.models.UserActivity_DB
import com.google.android.material.button.MaterialButton
import com.timejet.bio.timejet.R
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.ui.MainActivity
import com.timejet.bio.timejet.utils.Utils
import kotlinx.android.synthetic.main.statistic_dialog.*
import kotlinx.coroutines.*

class StatisticFragment : Fragment() {
    private val LOG_TAG = this::class.java.simpleName
    private lateinit var binding: com.timejet.bio.timejet.databinding.StatisticDialogBinding

    lateinit var viewModel: StatisticViewModel
    lateinit var realmHandler: RealmHandler

    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.statistic_dialog, container, false)
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        (activity as MainActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        btnDaily.isChecked = true
        binding.btnDaily.text = "DAILY"
        RealmHandler.ioScope.launch {
            realmHandler = RealmHandler.getInstance()
            var itemList : List<UserActivity_DB>
            withContext(Dispatchers.IO){
                itemList = realmHandler.getUserActivityByDay(true);
            }
            var workedTimeString = calculateTime(itemList)
            uiScope.launch {
                var cardAdapter = RVAdapter(itemList);
                binding.daylyWorkedTime.text = calculateTime(itemList)
                binding.rvStatCard.layoutManager = LinearLayoutManager(context)
                binding.rvStatCard.adapter = cardAdapter
                binding.rvStatCard.setItemViewCacheSize(32)
            }
        }
        materialButtonToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (group.checkedButtonId == -1) group.check(checkedId)

            val listenerButton: MaterialButton = group.findViewById(checkedId)
            val checkedButton: MaterialButton? = group.findViewById(group.checkedButtonId)
            var daily = true

            if (checkedButton != null) {
                if(checkedButton.text.toString().toLowerCase() == "daily"){
                    daily = true
                    binding.btnDaily.text = "DAILY"
                    binding.btnMonthly.text = "Monthly"
                    binding.daylyWorked.text = "Daily working time"
                } else {
                    daily = false
                    binding.btnMonthly.text = "MONTHLY"
                    binding.btnDaily.text = "Daily"
                    binding.daylyWorked.text = "Monthly working time"
                }
            }
            RealmHandler.ioScope.async {
                realmHandler = RealmHandler.getInstance()
                val itemList = realmHandler.getUserActivityByDay(daily);
                var workedTimeString = calculateTime(itemList)
                uiScope.launch {
                    var cardAdapter = RVAdapter(itemList);
                    binding.daylyWorkedTime.text = calculateTime(itemList)
                    binding.rvStatCard.layoutManager = LinearLayoutManager(context)
                    binding.rvStatCard.adapter = cardAdapter
                    binding.rvStatCard.setItemViewCacheSize(32)
                }
            }
        }
        binding.rvStatCard.addItemDecoration(
                DividerItemDecoration(
                        context,
                        RecyclerView.VERTICAL
                ).apply {
                    this.setDrawable(context?.getDrawable(R.drawable.item_decorator_statistic)!!)
                })
    }

    fun calculateTime(list: List<UserActivity_DB>): String {
        var totalMilliseconds = 0L
        for (item in list) {
            totalMilliseconds += item.eventDuration
        }

        return Utils.calculateTime(totalMilliseconds)
    }
}
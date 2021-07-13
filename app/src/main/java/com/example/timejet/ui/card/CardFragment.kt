package com.timejet.bio.timejet.ui.card

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.View.INVISIBLE
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.example.timejet.repository.models.PTS_DB
import com.example.timejet.repository.models.PTS_DB.*
import com.google.android.material.snackbar.Snackbar
import com.timejet.bio.timejet.R
import com.timejet.bio.timejet.repository.EventType
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.repository.StateRepository
import com.timejet.bio.timejet.repository.models.PTS_DB
import com.timejet.bio.timejet.repository.models.PTS_DB.*
import com.timejet.bio.timejet.ui.MainActivity
import com.timejet.bio.timejet.ui.main.AutoCompleteAdapter
import com.timejet.bio.timejet.ui.main.createStyledText
import com.timejet.bio.timejet.ui.main.isPossibleTasksInc
import com.timejet.bio.timejet.ui.setVisibleOrGone
import com.timejet.bio.timejet.utils.MskedEditTextUtils.MaskedEditText
import com.timejet.bio.timejet.utils.Utils
import kotlinx.android.synthetic.main.fragment_card.*
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class CardFragment : Fragment(), MainActivity.OnDrawerStateChangeCallback {
    private val LOG_TAG = this::class.java.simpleName
    private lateinit var binding: com.timejet.bio.timejet.databinding.FragmentCardBinding
    private lateinit var mbinding: com.timejet.bio.timejet.databinding.PopupWindowLayoutBinding
    lateinit var viewModel: CardViewModel
    lateinit var realmHandler: RealmHandler
    var pw: PopupWindow? = null
    var popUpWasOpened = false
    var endTaskDialog: Dialog? = null

    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_card, container, false)
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        (activity as MainActivity).refreshBurgerIcon()
        (activity as MainActivity).onDrawerStateChangeCallback = this
        if (arguments != null) {
            CardFragmentArgs.fromBundle(requireArguments()).apply {
                viewModel = ViewModelProvider(this@CardFragment,
                    CardViewModelFactory(
                        activity?.application!!,
                        taskId,
                        eventType,
                        uid,
                        projectName,
                        userAssigned,
                        pred
                    )).get(CardViewModel::class.java)
            }
        } else {
            viewModel = ViewModelProvider(this,
                CardViewModelFactory(activity?.application!!, -1, "", -1, "", "", ""))
                .get(CardViewModel::class.java)
        }
        realmHandler = RealmHandler.getInstance()
        binding.viewModel = viewModel
        binding.callback = this

        binding.executePendingBindings()

        initBasicViews()

        val circularProgressBar = binding.circularProgressBar

        viewModel.timeProgress().observe(viewLifecycleOwner, Observer { progress ->
            updatePieChart()
        })

        updatePieChart()

        if(StateRepository.getInstance().getEvent() == EventType.PHONECALL_EVENT ||
                StateRepository.getInstance().getEvent() == EventType.EMAIL_EVENT ||
                StateRepository.getInstance().getEvent() == EventType.MEETING_EVENT ||
                StateRepository.getInstance().getEvent() == EventType.TRAVEL_EVENT){
            viewModel.setPopupWindowState(true)
        }

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackHandle()
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    private fun updatePieChart() {
        circularProgressBar.apply {
            var timeRemained: Double = viewModel.getTaskRemainingTime()!!
            viewModel.timeProgress().value?.let {
                val hours = it.split(":")[0].toDouble()
                val minutes = it.split(":")[1].toDouble()
                val timePassed = ((hours * 3600000) + (minutes * 60000)) / 3600000
                timeRemained = (viewModel.getTimeBuget()!! + viewModel.getAddTime()!!) - timePassed
            }

            if(viewModel.getTimeBuget() != 0.0){
                binding.procent.text = (100 -((timeRemained!! /(viewModel.getTimeBuget()!! + viewModel.getAddTime()!!))*100)).roundToInt().toString() + "%"
            } else {
                binding.procent.text = "0%"
            }
            progress = (100 -((timeRemained /(viewModel.getTimeBuget()!! + viewModel.getAddTime()!!))*100)).toFloat()
            progressMax = 100f
            startAngle = 0f
            invalidate()
        }

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackHandle()
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

    }

    private fun initBasicViews() {
        with(viewModel) {
            firestoreSyncResult().observe(viewLifecycleOwner, Observer {
                it?.getContentIfNotHandled().let { result ->
                    result?.let { it1 ->
                        if (!it1) {
                            Snackbar.make(binding.root, "Cloud sync FAIL", Snackbar.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            })

            snackbarMsg().observe(viewLifecycleOwner, Observer {
                it?.getContentIfNotHandled().let { result ->
                    result?.let { it1 ->
                        if (it1.isEmpty())
                            Snackbar.make(
                                binding.root,
                                "Unknown Error",
                                Snackbar.LENGTH_LONG
                            ).show()
                        else
                            Snackbar.make(binding.root, it1, Snackbar.LENGTH_LONG).show()
                    }
                }
            })

            getSummaryStringTaskStart().observe(viewLifecycleOwner, Observer { pair ->
                if (isPossibleTasksInc()) {
                    binding.tvTaskYouCanStart.text =
                        createStyledText(pair?.first ?: 0, pair?.second ?: 0)
                } else {
                    binding.tvTaskYouCanStart.text = (pair?.first ?: 0).toString()
                }

            })

            getSummaryProjects().observe(viewLifecycleOwner, Observer {
                binding.textViewTotalProjects.text = it.toString()
            })

            getSummaryAllYourTask().observe(viewLifecycleOwner, Observer {
                binding.textViewAllTask.text = it.toString()
            })

            getSummaryFinishedTask().observe(viewLifecycleOwner, Observer {
                binding.textViewAllFinishedTasks.text = it.toString()
            })

            isPhoneButtonAnimate().observe(viewLifecycleOwner, Observer {
                it.let {
                    if(it.peekContent()) {
                        binding.phoneButton.setBackgroundResource(R.drawable.phone_button_presed)
                    } else {
                        binding.phoneButton.setBackgroundResource(R.drawable.phone_button)
                    }
                }
            })

            isEmailButtonAnimate().observe(viewLifecycleOwner, Observer {
                it.let {
                    if(it.peekContent()) {
                        binding.mailButton.setBackgroundResource(R.drawable.mail_button_presed)
                    } else {
                        binding.mailButton.setBackgroundResource(R.drawable.mail_button)
                    }
                }
            })

            isMeetingButtonAnimate().observe(viewLifecycleOwner, Observer {
                it.let {
                    if(it.peekContent()) {
                        binding.meetingButton.setBackgroundResource(R.drawable.meeting_button_pressed)
                    } else {
                        binding.meetingButton.setBackgroundResource(R.drawable.meeting_button)
                    }
                }
            })

            isTravellingButtonAnimate().observe(viewLifecycleOwner, Observer {
                it.let {
                    if(it.peekContent()) {
                        binding.travelButton.setBackgroundResource(R.drawable.travel_button_pressed)
                    } else {
                        binding.travelButton.setBackgroundResource(R.drawable.travel_button)
                    }
                }
            })

            isSyncAnimate().observe(viewLifecycleOwner, Observer {
                it.let {
                    if (it) {
                        binding.syncbutton.setVisibleOrGone(false)
                        binding.animationSync.setVisibleOrGone(true)
                        binding.animationSync.playAnimation()
                    } else {
                        binding.syncbutton.setVisibleOrGone(true)
                        binding.animationSync.setVisibleOrGone(false)
                        binding.animationSync.pauseAnimation()
                    }
                }
            })

            viewModel.timeBudget().observe(viewLifecycleOwner, Observer {
                it.let {
                    binding.timeBudget.text = it
                }
            })

            viewModel.globalMinutes().observe(viewLifecycleOwner, Observer {
                it?.let {
                    binding.textMinute.text = it
                }
            })

            viewModel.globalHours().observe(viewLifecycleOwner, Observer {
                it?.let {
                    binding.textHour.text = it
                }
            })

            isPopUpWindowClosed().observe(viewLifecycleOwner, Observer {
                it.let{
                    if (it == EventType.PHONECALL_EVENT) {
                        pw?.dismiss()
                        binding.phoneButton.setBackgroundResource(R.drawable.phone_button)
                    }
                    if(it == EventType.EMAIL_EVENT){
                        pw?.dismiss()
                        binding.mailButton.setBackgroundResource(R.drawable.mail_button)
                    }
                    if(it == EventType.MEETING_EVENT){
                        pw?.dismiss()
                        binding.meetingButton.setBackgroundResource(R.drawable.meeting_button)
                    }
                    if(it == EventType.TRAVEL_EVENT){
                        pw?.dismiss()
                        binding.travelButton.setBackgroundResource(R.drawable.travel_button)
                    }
                }
            })

            isShowSpinnerDialog().observe(viewLifecycleOwner, Observer {
                it.let {
                    if (it != EventType.NONE) {  //
                        var eventTypeForThread = it
                        RealmHandler.ioScope.launch {
                            val allProjects = realmHandler.getAllProjectNamesFromDB()
                            uiScope.launch {
                                if (allProjects == null) {
                                    Toast.makeText(
                                            context,
                                            "Error, do download and parse first",
                                            Toast.LENGTH_LONG
                                    )
                                            .show()
                                } else {
                                    val allProjectsList = ArrayList(allProjects)
                                    val adb = AlertDialog.Builder(context)
                                    val v: View = LayoutInflater.from(context).inflate(R.layout.communications_dialog, null)
                                    val rippleViewClose = v.findViewById<View>(R.id.buttonCancel) as TextView
                                    val chrono = v.findViewById<View>(R.id.chronometerCommunications) as Chronometer
                                    chrono.setBase(SystemClock.elapsedRealtime())
                                    chrono.format = "00:%s"
                                    chrono.start()
                                    chrono.setOnChronometerTickListener({ cArg ->
                                        val elapsedMillis = SystemClock.elapsedRealtime() - cArg.base
                                        if (elapsedMillis > 3600000L) {
                                            cArg.format = "0%s"
                                        }
                                        else {
                                            cArg.format = "00:%s"
                                        }
                                    })
                                    val listView =
                                            v.findViewById<View>(R.id.list) as ListView
                                    val searchBox =
                                            v.findViewById<View>(R.id.searchBox) as EditText
                                    val adapter = context?.let {
                                        AutoCompleteAdapter(
                                                it, // Context
                                                R.layout.items_view, // Layout
                                                0,
                                                allProjectsList// Array
                                        )
                                    }
                                    listView.adapter = adapter
                                    adb.setView(v)
                                    var alertDialog = adb.create()

                                    listView.onItemClickListener =
                                            AdapterView.OnItemClickListener { adapterView, view, i, l ->
                                                val t =
                                                        view.findViewById<View>(R.id.text1) as TextView
                                                searchBox.setText(t.text, TextView.BufferType.EDITABLE)
                                                searchBox.setSelection(t.text.length);
                                                // Get the selected item text from ListView
                                                pw?.dismiss()
                                                val selectedItem = t.text.toString()
                                                val convertView = LayoutInflater.from(context)
                                                        .inflate(
                                                                R.layout.popup_window_layout,
                                                                binding.root as ViewGroup,
                                                                false
                                                        )
                                                pw = PopupWindow(
                                                        convertView,
                                                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                                                        RelativeLayout.LayoutParams.WRAP_CONTENT
                                                );
                                                mbinding = DataBindingUtil.inflate(
                                                        LayoutInflater.from(context),
                                                        R.layout.popup_window_layout,
                                                        binding.root as ViewGroup,
                                                        false)
                                                pw!!.setContentView(mbinding.root)

                                                val location = IntArray(2)
                                                var y = 0;
                                                var x = 0;
                                                val fontSize: Float = resources.getDimension(R.dimen._52sdp)
                                                when (eventTypeForThread) {
                                                    EventType.PHONECALL_EVENT -> {
                                                        binding.phoneAnchor.getLocationOnScreen(location)
                                                        y = location[1]
                                                        binding.phoneButton.getLocationOnScreen(location)
                                                        x = location[0]
                                                    }
                                                    EventType.EMAIL_EVENT -> {
                                                        binding.mailAnchor.getLocationOnScreen(location)
                                                        y = location[1]
                                                        binding.mailButton.getLocationOnScreen(location)
                                                        x = location[0]
                                                    }
                                                    EventType.MEETING_EVENT -> {
                                                        binding.meetingAnchor.getLocationOnScreen(location)
                                                        y = location[1]
                                                        binding.meetingButton.getLocationOnScreen(location)
                                                        x = location[0]
                                                    }
                                                    EventType.TRAVEL_EVENT -> {
                                                        binding.travellingAnchor.getLocationOnScreen(location)
                                                        y = location[1]
                                                        binding.travelButton.getLocationOnScreen(location)
                                                        x = location[0]
                                                    }
                                                    else -> -1
                                                }
                                                val curPTSId = when (eventTypeForThread) {
                                                    EventType.PHONECALL_EVENT -> {
                                                        PTS_PHONECALL_UID
                                                    }
                                                    EventType.EMAIL_EVENT -> {
                                                        PTS_EMAIL_UID
                                                    }
                                                    EventType.MEETING_EVENT -> {
                                                        PTS_MEETING_UID
                                                    }
                                                    EventType.TRAVEL_EVENT -> {
                                                        PTS_TRAVEL_UID
                                                    }
                                                    else -> -1
                                                }
                                                viewModel.onSpinnerDialogSelected(selectedItem, curPTSId, eventTypeForThread, requireContext())

                                                viewModel.phoneTime().observe(viewLifecycleOwner, Observer {
                                                    it?.let {
                                                        if(StateRepository.getInstance().currentPTS!!.event == EventType.PHONECALL_EVENT){
                                                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                                                            pw?.contentView?.invalidate()
                                                        }
                                                    }
                                                })
                                                viewModel.emailTime().observe(viewLifecycleOwner, Observer {
                                                    it?.let {
                                                        if(StateRepository.getInstance().currentPTS!!!!.event == EventType.EMAIL_EVENT) {
                                                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                                                            pw?.contentView?.invalidate()
                                                        }
                                                    }
                                                })
                                                viewModel.meetingTime().observe(viewLifecycleOwner, Observer {
                                                    it?.let {
                                                        if(StateRepository.getInstance().currentPTS!!.event == EventType.MEETING_EVENT) {
                                                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                                                            pw?.contentView?.invalidate()
                                                        }
                                                    }
                                                })
                                                viewModel.travelTime().observe(viewLifecycleOwner, Observer {
                                                    it?.let {
                                                        if(StateRepository.getInstance().currentPTS!!.event == EventType.TRAVEL_EVENT) {
                                                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                                                            pw?.contentView?.invalidate()
                                                        }
                                                    }
                                                })
                                                val buttonSet = pw?.contentView?.findViewById<Button>(R.id.edit)
                                                buttonSet?.setOnClickListener{
                                                    showAlertDialogEditPhoneEmailMeetingProject { viewModel.updateRemainingWorkAdditionalTime() }
                                                }
                                                pw?.contentView?.findViewById<TextView>(R.id.tvProject)?.setText(selectedItem)
                                                pw!!.showAtLocation(
                                                        getView(), // Location to display popup window
                                                        Gravity.NO_GRAVITY, // Exact position of layout to display popup
                                                        x, // X offset
                                                        y - fontSize.roundToInt()) // Y offset

                                                alertDialog.dismiss()
                                            }

                                    searchBox.addTextChangedListener(object : TextWatcher {
                                        override fun beforeTextChanged(
                                                charSequence: CharSequence,
                                                i: Int,
                                                i1: Int,
                                                i2: Int
                                        ) {
                                        }

                                        override fun onTextChanged(
                                                charSequence: CharSequence,
                                                i: Int,
                                                i1: Int,
                                                i2: Int
                                        ) {
                                        }

                                        override fun afterTextChanged(editable: Editable) {
                                            if (adapter != null) {
                                                adapter.filter.filter(searchBox.text.toString())
                                            }
                                        }
                                    })
                                    rippleViewClose.setOnClickListener {
                                        when (eventTypeForThread) {
                                            EventType.PHONECALL_EVENT -> {
                                                binding.phoneButton.setBackgroundResource(R.drawable.phone_button)
                                            }
                                            EventType.EMAIL_EVENT -> {
                                                binding.mailButton.setBackgroundResource(R.drawable.mail_button)
                                            }
                                            EventType.MEETING_EVENT -> {
                                                binding.meetingButton.setBackgroundResource(R.drawable.meeting_button)
                                            }
                                            EventType.TRAVEL_EVENT -> {
                                                binding.travelButton.setBackgroundResource(R.drawable.travel_button)
                                            }
                                        }
                                        viewModel.onSpinnerDialogShowed()
                                        alertDialog.dismiss() }
                                    alertDialog.setCancelable(true)
                                    alertDialog.setCanceledOnTouchOutside(false)
                                    alertDialog.show()

                                }
                            }
                        }
                    }
                }
            })

            isPopUpWindowOpend().observe(viewLifecycleOwner, Observer {
                it?.let {
                    if (it) {
                        StateRepository.getInstance().currentPTS?.event?.let {
                            StateRepository.getInstance().currentPTS?.projectName?.let { it1 ->
                                if(it == EventType.MEETING_EVENT || it == EventType.EMAIL_EVENT || it==EventType.PHONECALL_EVENT || it== EventType.TRAVEL_EVENT){
                                    createPopUpWindow(it, it1)
                                }
                            }
                        }
                    }
                }
            })

            isStartAnimate().observe(viewLifecycleOwner, Observer {
                if (!it.peekContent()) {
                    binding.animationTime.pauseAnimation()
                } else {
                    if (binding.animationTime.progress > 0) {
                        binding.animationTime.resumeAnimation();
                    } else {
                        binding.animationTime.playAnimation()
                    }
                    if(pw != null){
                        pw!!.dismiss()
                    }
                    binding.imageWork.visibility = View.VISIBLE
                    binding.imagePauza.visibility = View.INVISIBLE
                    binding.imageStop.visibility = View.INVISIBLE
                }
            })

            isPauseAnimate().observe(viewLifecycleOwner, Observer {
                it.getContentIfNotHandled()?.let {
                } ?: run {
                    it.peekContent().let {
                        if (it) {
                            binding.imageWork.visibility = View.INVISIBLE
                            binding.imagePauza.visibility = View.VISIBLE
                            binding.imageStop.visibility = View.INVISIBLE
                        }
                    }
                }

            })

            isEndAnimate().observe(viewLifecycleOwner, Observer {
                it.getContentIfNotHandled()?.let {
                } ?: run {
                    it.peekContent().let {
                        if (it) {
                            binding.imageWork.visibility = View.INVISIBLE
                            binding.imagePauza.visibility = View.INVISIBLE
                            binding.imageStop.visibility = View.VISIBLE
                        }
                    }
                }
            })

            isOverBudget().observe(viewLifecycleOwner, Observer {
                if (it) {
                    alertDialogOverBudgetAddTaskNote(viewModel.getCurrentPTSDB())
                    setOverBugetFalse()
                }
            })

            isConfirmEnd().observe(viewLifecycleOwner, Observer {
                if (it) {
                    val dialog = Dialog(requireContext())
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                    dialog.setContentView(R.layout.dialog_confirm_end_task)
                    val buttonSet = dialog.findViewById<Button>(R.id.buttonSet)
                    val buttonCancel = dialog.findViewById<Button>(R.id.buttonCancel)
                    endTaskDialog = dialog
                    buttonSet.setOnClickListener {
                        viewModel.endConfirmed()
                        refreshFragment()
                        dialog.dismiss()
                    }

                    buttonCancel.setOnClickListener { dialog.dismiss() }
                    dialog.show()
                    isConfirmEndPostFalse()
                }
            })
        }
//        binding.tvFirebaseProjectName.text = FirebaseFirestore.getInstance().app.options.projectId
    }

    fun refreshFragment(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fragmentManager?.beginTransaction()?.detach(binding.callback as Fragment)?.commitNow();
            fragmentManager?.beginTransaction()?.attach(binding.callback as Fragment)?.commitNow();
            endTaskDialog?.dismiss()
        } else {
            fragmentManager?.beginTransaction()?.detach(binding.callback as Fragment)?.attach(binding.callback as Fragment)?.commit();
            endTaskDialog?.dismiss()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)
    }


    private fun alertDialogEditTimeProgress(): Boolean {
        showAlertDialogEditTimeProgress {
            viewModel.updateRemainingWorkAdditionalTime()
        }
        return true
    }

    private fun alertDialogEditPhoneEmailMeetingProject(): Boolean {
        showAlertDialogEditPhoneEmailMeetingProject { viewModel.updateRemainingWorkAdditionalTime() }
        return true
    }

    fun editTime(){
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_edit_progress)
        val buttonSet = dialog.findViewById<Button>(R.id.buttonSet)
        val buttonCancel = dialog.findViewById<Button>(R.id.buttonCancel)

        val editTextTime = dialog.findViewById<MaskedEditText>(R.id.editTextTime)

        val time = viewModel.timeProgress().value
        val toPost:String
        toPost = if (time.isNullOrEmpty()){
            "0000:00"
        } else {
            if (time.substringBefore(':').length == 3){
                "0" + time
            } else {
                if (time.substringBefore(':').length == 2){
                    "00" + time
                } else {
                    time
                }
            }
        }

        editTextTime.setText(toPost)

        buttonSet.setOnClickListener {
            val timeStr = editTextTime.text.toString()
            if (timeStr.isNullOrEmpty()) {
            } else {
                try {
                    if(timeStr.contains(':')){
                        var hours = timeStr.substringBefore(':')
                        var minutes = timeStr.substringAfter(':')
                        if(hours.length>4){
                            hours = hours.substring(0, 4)
                        }
                        if(minutes.length>2){
                            minutes = minutes.substring(0, 2)
                        }
                        if(minutes.isEmpty()){
                            minutes = "00"
                        }
                        if(minutes.length==1){
                            minutes += '0'
                        }
                        val milis = hours.toLong() * 3600000 + minutes.toLong() * 60000
                        val timeToDoubleVar = milis/3600000.0
                        StateRepository.getInstance().timeStartToNull()
                        viewModel.setProgress(timeToDoubleVar)
                    } else {
                        var hours = timeStr
                        if(hours.length>4){
                            hours = hours.substring(0, 4)
                        }
                        val milis = hours.toLong() * 3600000
                        val timeToDoubleVar = milis/3600000.0
                        StateRepository.getInstance().timeStartToNull()
                        viewModel.setProgress(timeToDoubleVar)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.d(LOG_TAG, "Exception: ${e.message}")
                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
            dialog.dismiss()
        }

        buttonCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun additionalTimeClick() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_additional_time)
        val buttonSet = dialog.findViewById<Button>(R.id.buttonSet)
        val buttonCancel = dialog.findViewById<Button>(R.id.buttonCancel)

        val editTextAdditionalTime = dialog.findViewById<MaskedEditText>(R.id.editTextAdditionalTime)

        val prevTime = viewModel.getAdditionalTimeForCurrentTask() ?: 0.0
        val toPost:String
        if(Utils.calculateTime(prevTime).substringBefore(':').length == 3){
            toPost = "0" + Utils.calculateTime(prevTime)
        } else {
            if(Utils.calculateTime(prevTime).substringBefore(':').length == 2){
                toPost = "00" + Utils.calculateTime(prevTime)
            } else {
                toPost = Utils.calculateTime(prevTime)
            }
        }

        editTextAdditionalTime.setText(toPost)

        buttonSet.setOnClickListener {
            val timeStr = editTextAdditionalTime.text.toString()
            Log.d(LOG_TAG, "additinalTimeClick: timeStr: $timeStr")
            if (timeStr.isNullOrEmpty()) {
                viewModel.setAdditionalTime(null)
                Log.d(LOG_TAG, "additinalTimeClick: timeStr: set to NULL")
            } else {
                try {
                    if(timeStr.contains(':')){
                        var hours = timeStr.substringBefore(':')
                        var minutes = timeStr.substringAfter(':')
                        if(hours.length>4){
                            hours = hours.substring(0, 4)
                        }
                        if(minutes.length>2){
                            minutes = minutes.substring(0, 2)
                        }
                        if(minutes.isEmpty()){
                            minutes = "00"
                        }
                        if(minutes.length==1){
                            minutes += '0'
                        }
                        val milis = hours.toLong() * 3600000 + minutes.toLong() * 60000
                        val timeToDoubleVar = milis/3600000.0
                        viewModel.setAdditionalTime(timeToDoubleVar)
                    } else {
                        var hours = timeStr
                        if(hours.length>4){
                            hours = hours.substring(0, 4)
                        }
                        val milis = hours.toLong() * 3600000
                        val timeToDoubleVar = milis/3600000.0
                        viewModel.setAdditionalTime(timeToDoubleVar)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.d(LOG_TAG, "Exception: ${e.message}")
                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
            dialog.dismiss()
        }

        buttonCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // клик на кнопку ДОБАВИТЬ ЗАМЕТКУ
    fun imageViewTaskNoteClick() {
        alertDialogOverBudgetAddTaskNote(viewModel.getCurrentPTSDB())
    }

    fun imageViewPTSisDoneClick() {
//        binding.imageViewPTSisDone.visibility = INVISIBLE
//        viewModel.rollbackCompleted()
        binding.imageViewPTSisDone?.postDelayed({
            binding.imageViewPTSisDone.visibility = INVISIBLE
            viewModel.rollbackCompleted()
        }, 100)
    }

    fun onButtonChooseStep() {
        onBackHandle()
    }

    fun onButtonStatistic() {
        view?.let {
            pw?.dismiss()
            viewModel.buttonPauseTaskPressed()
            val navController = Navigation.findNavController(it)
            navController.navigate(CardFragmentDirections.actionCardFragmentToStatisticFragment())
        }
    }

    override fun onStop() {
        Log.d(LOG_TAG, "onStop()")
        super.onStop()
    }

    fun onBackHandle() {
        if (isVisible) {
            when (StateRepository.getInstance().getEvent()) {
                EventType.START_EVENT -> {
                    viewModel.buttonPauseTaskPressed()
                    StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
                    StateRepository.getInstance().setEvent(EventType.NONE)
                }
                EventType.PHONECALL_EVENT, EventType.EMAIL_EVENT, EventType.MEETING_EVENT, EventType.TRAVEL_EVENT -> {
                }
                else -> {
                    StateRepository.getInstance().setEvent(EventType.NONE)
                }
            }
            viewModel.jobsStop()
            Navigation.findNavController(requireView()).popBackStack()
            pw?.dismiss()
        }
    }

    private fun alertDialogOverBudgetAddTaskNote(currentPTSDB: PTS_DB?) {
        if (currentPTSDB != null) {
            val dialog = Dialog(requireContext())
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_comment)
            val buttonSet = dialog.findViewById<Button>(R.id.buttonSet)
            val buttonCancel = dialog.findViewById<Button>(R.id.buttonCancel)

            val noteText = dialog.findViewById<EditText>(R.id.noteText)

            noteText.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

            buttonSet.setOnClickListener {
                val newTaskNote = noteText.text.toString()
                if (!newTaskNote.isEmpty()) {
                    viewModel.setNote(newTaskNote)
                }
                dialog.dismiss()
            }

            buttonCancel.setOnClickListener { dialog.dismiss() }
            dialog.show()
        } else {
            Toast.makeText(activity, "Please select task!", Toast.LENGTH_LONG).show()
        }

    }

    private fun showAlertDialogEditPhoneEmailMeetingProject(onPositiveButtonClick: () -> Unit) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_additional_time_communications)
        val buttonSet = dialog.findViewById<Button>(R.id.buttonSet)
        val buttonCancel = dialog.findViewById<Button>(R.id.buttonCancel)

        val editTextCommunicationTime = dialog.findViewById<MaskedEditText>(R.id.editTextAdditionalTime)

        var toPost = "0000:00"
        when (StateRepository.getInstance().getEvent()) {
            EventType.PHONECALL_EVENT -> {
                if (viewModel.phoneTime() != null || viewModel.phoneTime().value != ""){
                    toPost = if(viewModel.phoneTime().value?.substringBefore(':')?.length!! == 2){
                        "00" + viewModel.phoneTime().value!!.substringBefore(':') + ":" + viewModel.phoneTime().value!!.substringAfter(':').substringBefore(':')
                    } else {
                        if(viewModel.phoneTime().value?.substringBefore(':')?.length!! == 3){
                            "0" + viewModel.phoneTime().value!!.substringBefore(':') + ":" + viewModel.phoneTime().value!!.substringAfter(':').substringBefore(':')
                        } else {
                            viewModel.phoneTime().value!!.substringBefore(':') + ":" + viewModel.phoneTime().value!!.substringAfter(':').substringBefore(':')
                        }
                    }
                } else {
                    toPost = "0000:00"
                }
            }
            EventType.EMAIL_EVENT -> {
                if (viewModel.emailTime() != null || viewModel.emailTime().value != ""){
                    toPost = if(viewModel.emailTime().value?.substringBefore(':')?.length!! == 2){
                        "00" + viewModel.emailTime().value!!.substringBefore(':') + ":" + viewModel.emailTime().value!!.substringAfter(':').substringBefore(':')
                    } else {
                        if(viewModel.emailTime().value?.substringBefore(':')?.length!! == 3){
                            "0" + viewModel.emailTime().value!!.substringBefore(':') + ":" + viewModel.emailTime().value!!.substringAfter(':').substringBefore(':')
                        } else {
                            viewModel.emailTime().value!!.substringBefore(':') + ":" + viewModel.emailTime().value!!.substringAfter(':').substringBefore(':')
                        }
                    }
                } else {
                    toPost = "0000:00"
                }
            }
            EventType.MEETING_EVENT -> {
                if (viewModel.meetingTime() != null || viewModel.meetingTime().value != ""){
                    toPost = if(viewModel.meetingTime().value?.substringBefore(':')?.length!! == 2){
                        "00" + viewModel.meetingTime().value!!.substringBefore(':') + ":" + viewModel.meetingTime().value!!.substringAfter(':').substringBefore(':')
                    } else {
                        if(viewModel.meetingTime().value?.substringBefore(':')?.length!! == 3){
                            "0" + viewModel.meetingTime().value!!.substringBefore(':') + ":" + viewModel.meetingTime().value!!.substringAfter(':').substringBefore(':')
                        } else {
                            viewModel.meetingTime().value!!.substringBefore(':') + ":" + viewModel.meetingTime().value!!.substringAfter(':').substringBefore(':')
                        }
                    }
                } else {
                    toPost = "0000:00"
                }
            }
            EventType.TRAVEL_EVENT -> {
                if (viewModel.travelTime() != null || viewModel.travelTime().value != ""){
                    toPost = if(viewModel.travelTime().value?.substringBefore(':')?.length!! == 2){
                        "00" + viewModel.travelTime().value!!.substringBefore(':') + ":" + viewModel.travelTime().value!!.substringAfter(':').substringBefore(':')
                    } else {
                        if(viewModel.travelTime().value?.substringBefore(':')?.length!! == 3){
                            "0" + viewModel.travelTime().value!!.substringBefore(':') + ":" + viewModel.travelTime().value!!.substringAfter(':').substringBefore(':')
                        } else {
                            viewModel.travelTime().value!!.substringBefore(':') + ":" + viewModel.travelTime().value!!.substringAfter(':').substringBefore(':')
                        }
                    }
                } else {
                    toPost = "0000:00"
                }
            }
        }

        editTextCommunicationTime.setText(toPost)

        buttonSet.setOnClickListener {
            val timeStr = editTextCommunicationTime.text.toString()
            if (timeStr.isNullOrEmpty()) {
            } else {
                try {
                    if(timeStr.contains(':')){
                        var hours = timeStr.substringBefore(':')
                        var minutes = timeStr.substringAfter(':')
                        if(hours.length>4){
                            hours = hours.substring(0, 4)
                        }
                        if(minutes.length>2){
                            minutes = minutes.substring(0, 2)
                        }
                        if(minutes.isEmpty()){
                            minutes = "00"
                        }
                        if(minutes.length==1){
                            minutes += '0'
                        }
                        val milis = hours.toLong() * 3600000 + minutes.toLong() * 60000
                        val timeToDoubleVar = milis/3600000.0
                        viewModel.updatePhoneCallEmailMeetingDurationNotLoop(StateRepository.getInstance().getEvent(), timeToDoubleVar)
                    } else {
                        var hours = timeStr
                        if(hours.length>4){
                            hours = hours.substring(0, 4)
                        }
                        val milis = hours.toLong() * 3600000
                        val timeToDoubleVar = milis/3600000.0
                        viewModel.updatePhoneCallEmailMeetingDurationNotLoop(StateRepository.getInstance().getEvent(), timeToDoubleVar)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.d(LOG_TAG, "Exception: ${e.message}")
                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
            dialog.dismiss()
        }

        buttonCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showAlertDialogEditTimeProgress(onPositiveButtonClick: () -> Unit) {
        var formattedDuration: String? = null
        RealmHandler.ioScope.let {
            try {
                formattedDuration = Utils.calculateTime(viewModel.getProgress())
            } catch (e: NumberFormatException) {
            }
            uiScope.launch {

                val edittext = EditText(activity)
                edittext.setTextColor(Color.BLACK)
                edittext.setText(formattedDuration)
                edittext.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                AlertDialog.Builder(activity)
                    .setTitle("Edit Time Progress")
                    .setIcon(R.drawable.ic_edit_black_24dp)
                    .setView(edittext)
                    .setPositiveButton("OK") { _, _ ->
                        try {
                            val newTimeProgress = edittext.text.toString().toDouble()
                            RealmHandler.ioScope.launch {
                                viewModel.setProgress(newTimeProgress)
                            }
                        } catch (e: Exception) {
                            Log.d(LOG_TAG, "Edit time progress: Parse error: ${e.message}")
                            Toast.makeText(
                                context,
                                "Edit time progress: parse error: ${e.message}",
                                Toast.LENGTH_LONG
                            )
                                .show()
                        }
                        onPositiveButtonClick()
                    }
                    .setNegativeButton("Cancel") { _, _ -> }
                    .show()
            }
        }
    }

    private fun createPopUpWindow(eventTypeForThread: EventType, projectName: String) {
        RealmHandler.ioScope.launch {
            uiScope.launch {
                delay(50)
                pw?.dismiss()
                val selectedItem = projectName
                val convertView = LayoutInflater.from(context)
                        .inflate(
                                R.layout.popup_window_layout,
                                binding.root as ViewGroup,
                                false
                        )
                pw = PopupWindow(
                        convertView,
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                );
                mbinding = DataBindingUtil.inflate(
                        LayoutInflater.from(context),
                        R.layout.popup_window_layout,
                        binding.root as ViewGroup,
                        false)
                pw!!.setContentView(mbinding.root)

                val location = IntArray(2)
                var y = 0;
                var x = 0;
                val fontSize: Float = resources.getDimension(R.dimen._52sdp)
                when (eventTypeForThread) {
                    EventType.PHONECALL_EVENT -> {
                        view?.findViewById<TextView>(R.id.phone_anchor)?.getLocationOnScreen(location)
                        y = location[1]
                        binding.phoneButton.getLocationOnScreen(location)
                        x = location[0]
                    }
                    EventType.EMAIL_EVENT -> {
                        view?.findViewById<TextView>(R.id.mailAnchor)?.getLocationOnScreen(location)
                        y = location[1]
                        binding.mailButton.getLocationOnScreen(location)
                        x = location[0]
                    }
                    EventType.MEETING_EVENT -> {
                        view?.findViewById<TextView>(R.id.meetingAnchor)?.getLocationOnScreen(location)
                        y = location[1]
                        binding.meetingButton.getLocationOnScreen(location)
                        x = location[0]
                    }
                    EventType.TRAVEL_EVENT -> {
                        view?.findViewById<TextView>(R.id.travellingAnchor)?.getLocationOnScreen(location)
                        y = location[1]
                        binding.travelButton.getLocationOnScreen(location)
                        x = location[0]
                    }
                    else -> -1
                }
                val curPTSId = when (eventTypeForThread) {
                    EventType.PHONECALL_EVENT -> {
                        PTS_PHONECALL_UID
                    }
                    EventType.EMAIL_EVENT -> {
                        PTS_EMAIL_UID
                    }
                    EventType.MEETING_EVENT -> {
                        PTS_MEETING_UID
                    }
                    EventType.TRAVEL_EVENT -> {
                        PTS_TRAVEL_UID
                    }
                    else -> -1
                }
                viewModel.onSpinnerDialogSelected(selectedItem, curPTSId, eventTypeForThread, requireContext())

                viewModel.phoneTime().observe(viewLifecycleOwner, {
                    it?.let {
                        if(StateRepository.getInstance().currentPTS!!.event == EventType.PHONECALL_EVENT){
                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                            pw?.contentView?.invalidate()
                        }
                    }
                })
                viewModel.emailTime().observe(viewLifecycleOwner, {
                    it?.let {
                        if(StateRepository.getInstance().currentPTS!!.event == EventType.EMAIL_EVENT) {
                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                            pw?.contentView?.invalidate()
                        }
                    }
                })
                viewModel.meetingTime().observe(viewLifecycleOwner, {
                    it?.let {
                        if(StateRepository.getInstance().currentPTS!!.event == EventType.MEETING_EVENT) {
                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                            pw?.contentView?.invalidate()
                        }
                    }
                })
                viewModel.travelTime().observe(viewLifecycleOwner, {
                    it?.let {
                        if(StateRepository.getInstance().currentPTS!!.event == EventType.TRAVEL_EVENT) {
                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                            pw?.contentView?.invalidate()
                        }
                    }
                })

                pw?.contentView?.findViewById<TextView>(R.id.tvProject)?.setText(selectedItem)
                val buttonSet = pw?.contentView?.findViewById<Button>(R.id.edit)
                buttonSet?.setOnClickListener{
                    showAlertDialogEditPhoneEmailMeetingProject { viewModel.updateRemainingWorkAdditionalTime() }
                }
                pw!!.showAtLocation(
                        getView(), // Location to display popup window
                        Gravity.NO_GRAVITY, // Exact position of layout to display popup
                        x, // X offset
                        y - fontSize.roundToInt()) // Y offset
            }
        }
    }



    override fun onDrawerOpened() {
        pw?.let {
            pw?.dismiss()
            viewModel.buttonPauseTaskPressed()
        }
    }

    override fun onDrawerClosed() {
//        if (popUpWasOpened) {
//            popUpWasOpened = false
//            viewModel.setPopupWindowState(true)
//        }
    }

}
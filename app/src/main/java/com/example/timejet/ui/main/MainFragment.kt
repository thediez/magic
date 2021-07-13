package com.timejet.bio.timejet.ui.main


import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import com.google.android.material.snackbar.Snackbar
import com.timejet.bio.timejet.R
import com.timejet.bio.timejet.R.drawable.*
import com.timejet.bio.timejet.databinding.FragmentMainBinding
import com.timejet.bio.timejet.repository.EventType
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.repository.StateRepository
import com.timejet.bio.timejet.repository.databases.localDB.showToast
import com.timejet.bio.timejet.repository.models.PTS_DB
import com.timejet.bio.timejet.repository.models.PTS_DB.*
import com.timejet.bio.timejet.ui.MainActivity
import com.timejet.bio.timejet.ui.main.adapter.PTSItemAdapter
import com.timejet.bio.timejet.ui.setVisibleOrGone
import com.timejet.bio.timejet.utils.LocalUserInfo
import com.timejet.bio.timejet.utils.MskedEditTextUtils.MaskedEditText
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class MainFragment : Fragment(), MainActivity.OnDrawerStateChangeCallback {
    private val LOG_TAG = this::class.java.simpleName
    lateinit var binding: FragmentMainBinding
    lateinit var viewModel: MainViewModel
    var popUpPPositionLocation = IntArray(2)
    var flag = false

    private var ptsItemAdapter = PTSItemAdapter(object : PTSItemAdapter.OnItemClickListener {
        override fun onItemClickListener(item: PTS_DB?, position: Int) {
            Log.d(LOG_TAG, "onItemClickListener: start")
            if (viewModel.isSyncAnimate().value == true) {
                Toast.makeText(context?.applicationContext, R.string.sync_is_running, Toast.LENGTH_LONG).show()
                return
            }
            RealmHandler.ioScope.launch {
                val notYourTask = viewModel.isNotYourTask(position)
                val notAllPredecessors = viewModel.isNotAllPredecessorsAreFinished(position)
                uiScope.launch {
                    when {
                        notYourTask -> showToast(context?.resources!!.getString(R.string.itsNotYourTask), context!!)
                        notAllPredecessors -> showToast(context?.resources!!.getString(R.string.notAllPredecessorsAreFinished), context!!)
                        else -> {
                            try {
                                pw?.dismiss()
                                StateRepository.getInstance().currentPTS?.let { event ->
                                    val navController = Navigation.findNavController(binding.root)
                                    navController.let {
                                        if (navController.currentDestination?.id == R.id.mainFragment) {
                                            var pred = ""
                                            if (item != null) {
                                                if (item.predecessorsIDlist != null) {
                                                    item.predecessorsIDlist.forEachIndexed { index, s ->
                                                        pred += if (index == 0) {
                                                            s.toString()
                                                        } else {
                                                            "$s, "
                                                        }
                                                    }
                                                }
                                            }
                                            navController
                                                    .navigate(
                                                            MainFragmentDirections
                                                                    .actionMainFragmentToCardFragment(item?.id!!,
                                                                            event.event.name,
                                                                            event.uid,
                                                                            event.projectName,
                                                                            item.usersAssigned,
                                                                            pred
                                                                    )
                                                    )
                                        }
                                    }
                                }
                                        ?: Navigation.findNavController(binding.root).let { navController ->
                                            if (navController.currentDestination?.id == R.id.mainFragment) {
                                                var pred = ""
                                                if (item != null) {
                                                    if (item.predecessorsIDlist != null) {
                                                        item.predecessorsIDlist.forEachIndexed { index, s ->
                                                            pred += if (index == 0) {
                                                                s.toString()
                                                            } else {
                                                                "$s, "
                                                            }
                                                        }
                                                    }
                                                }
                                                navController
                                                        .navigate(
                                                                MainFragmentDirections
                                                                        .actionMainFragmentToCardFragment(item?.id!!,
                                                                                EventType.NONE.name,
                                                                                item.uid,
                                                                                item.projectName,
                                                                                item.usersAssigned,
                                                                                pred
                                                                        )
                                                        )
                                            }
                                        }
                            } catch (e: Exception) {
                                Log.d(LOG_TAG, "Navigation: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    })
    lateinit var realmHandler: RealmHandler

    var pw: PopupWindow? = null
    private lateinit var mbinding: com.timejet.bio.timejet.databinding.PopupWindowLayoutBinding

    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_main, container, false)
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onPause() {
        super.onPause()
        pw?.dismiss()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        (activity as MainActivity).onDrawerStateChangeCallback = this
        realmHandler = RealmHandler.getInstance()
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        binding.mainViewModel = viewModel

        (activity as MainActivity).refreshBurgerIcon()

        arguments?.let { it ->
            val args = MainFragmentArgs.fromBundle(it)
            if (args.uid > 0 && args.projectName.isNotEmpty() && StateRepository.getInstance().getEvent() != EventType.NONE) {
                RealmHandler.ioScope.launch {
                    val item = realmHandler.getPTSbyUIDProjectNameUserNameComaEmail(args.uid, args.projectName, LocalUserInfo.getUserEmail(requireContext()))
                    item?.let {
                        var pred = ""
                        if (item.predecessorsIDlist != null) {
                            item.predecessorsIDlist.forEachIndexed { index, s ->
                                pred += if (index == 0) {
                                    s.toString()
                                } else {
                                    "$s, "
                                }
                            }
                        }
                        Navigation.findNavController(binding.root)
                                .navigate(MainFragmentDirections.actionMainFragmentToCardFragment(it.id, StateRepository.getInstance().getEvent().name, it.id, it.projectName, it.usersAssigned, pred))
                        arguments = null
                    }
                }
            }
        }
        val background: Drawable = cbOrderDeadline.background
        if (background is GradientDrawable) {
            background.setColor(Color.parseColor("#667483"))
        }

        val list = ArrayList<String>()
        list.add(0, "Select progress")   //  Initial dummy entry
        list.add("Ready to start")
        list.add("In Progress")
        list.add("Complete")
        list.add("Locked")

        val imageList = ArrayList<Int>()
        imageList.add(ic_inprogress)
        imageList.add(ic_inprogress)
        imageList.add(ic_complete)
        imageList.add(ic_notcomplete)

        intArrayOf(card_dot, ic_inprogress, ic_complete, ic_notcomplete)
        val spinnerCustomAdapter = context?.let { SpinnerCustomAdapter(it, 0, 0, list) }
        progressSpinner.adapter = spinnerCustomAdapter

        RealmHandler.ioScope.launch {

            // Set an on item selected listener for spinner object
            progressSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p0: AdapterView<*>?) {
                    progressSpinner.setSelection(0)
                    viewModel.setProgress("")
                    viewModel.setOrderByDeadline(flag)
                    viewModel.getPtsItems()
                }

                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    // Display the selected item text on text view
                    if (position == 0) {
                        progressSpinner.setSelection(0)
                        viewModel.setProgress("")
                        viewModel.setOrderByDeadline(flag)
                        viewModel.getPtsItems()
                    } else {
                        progressSpinner.setSelection(position)
                        viewModel.setProgress(list[position])
                        viewModel.setOrderByDeadline(flag)
                        viewModel.getPtsItems()
                    }
                }
            }
            cbOrderDeadline.setOnClickListener {
                if (!viewModel.getOrderByDeadline()) {
                    val background: Drawable = cbOrderDeadline.background
                    if (background is GradientDrawable) {
                        background.setColor(Color.parseColor("#50B7D8"))
                    }
                    flag = true
                    viewModel.setOrderByDeadline(true)
                    viewModel.getPtsItems()
                } else {
                    val background: Drawable = cbOrderDeadline.background
                    if (background is GradientDrawable) {
                        background.setColor(Color.parseColor("#667483"))
                    }
                    flag = false
                    viewModel.setOrderByDeadline(false)
                    viewModel.getPtsItems()
                }
            }
            val allProjects: Array<out Any>
            val allUsers: Array<out Any>
            val allTasks: Array<out Any>
            if (showTasksForAllUsers.isPressed) {
                allProjects = realmHandler.getAllProjectNamesFromDBMain()?.toArray() as Array<out Any>
                allUsers = realmHandler.getAllUsersAssignedFromDB().toArray() as Array<out Any>
                allTasks = realmHandler.getAllTasksFromDB()?.toArray() as Array<out Any>
            } else {
                allProjects = realmHandler.getAllProjectNamesFromDBByUser().toArray() as Array<out Any>
                allUsers = realmHandler.getAllUsersAssignedFromDB().toArray() as Array<out Any>
                allTasks = realmHandler.getAllTasksFromDBByUser().toArray() as Array<out Any>
            }
            if (!allProjects.isNullOrEmpty()) {
                uiScope.launch(Dispatchers.Main) {
                    if (ibtn_addProject != null) {
                        ibtn_addProject.visibility = View.INVISIBLE
                    }
                    if (textAddNewProject != null) {
                        textAddNewProject.visibility = View.INVISIBLE
                    }
                }
                val autocompleteList = ArrayList<String>()
                for (i in allProjects.indices) {
                    autocompleteList.add(allProjects[i].toString())
                    Log.d(LOG_TAG, allProjects[i].toString())
                }
                addAdapter(autocompleteList, searchByProject, "project")
                autocompleteList.clear()
                for (i in allUsers.indices) {
                    autocompleteList.add(allUsers[i].toString())
                    Log.d(LOG_TAG, allUsers[i].toString())
                }
                addAdapter(autocompleteList, searchByUser, "user")
                autocompleteList.clear()
                for (i in allTasks.indices) {
                    autocompleteList.add(allTasks[i].toString())
                    Log.d(LOG_TAG, allTasks[i].toString())
                }
                addAdapter(autocompleteList, searchByTask, "task")
                autocompleteList.clear()
            } else {
                uiScope.launch(Dispatchers.Main) {
                    if (ibtn_addProject != null) {
                        ibtn_addProject.visibility = View.VISIBLE
                    }
                    if (textAddNewProject != null) {
                        textAddNewProject.visibility = View.VISIBLE
                    }
                }
            }
        }

        binding.callback = this

        binding.executePendingBindings()

        initBasicViews()

        viewModel.isNotNavigated = true

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackHandle()
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addAdapter(list: ArrayList<String>, autoCompleteTextView: AutoCompleteTextView, textViewSwitch: String) {
        uiScope.launch {
            // Initialize a new array adapter object
            val adapter = context?.let {
                AutoCompleteAdapter(
                        it, // Context
                        R.layout.items_view_autocomplete, // Layout
                        0,
                        list// Array
                )
            }
            val clearIcon = baseline_clear_black
            val activeIconSearch = icon_search_active
            val inactiveIconSearch = icon_search
            // Set the AutoCompleteTextView adapter
            autoCompleteTextView.setAdapter(adapter)
            if (autoCompleteTextView.text.toString() != "") {
                val clearIcon = baseline_clear_black
                val icon = icon_search_active
                autoCompleteTextView.setCompoundDrawablesWithIntrinsicBounds(icon, 0, clearIcon, 0)
            }
            // Auto complete threshold
            // The minimum number of characters to type to show the drop down
            autoCompleteTextView.threshold = 1
            autoCompleteTextView.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                    val clearIcon = if (p0?.isNotEmpty() == true) clearIcon else 0
                    val icon = if (p0?.isNotEmpty() == true) activeIconSearch else inactiveIconSearch
                    autoCompleteTextView.setCompoundDrawablesWithIntrinsicBounds(icon, 0, clearIcon, 0)
                    if (p0 != null) {
                        for (i in p0.length - 1 downTo 0) {
                            if (p0[i] == '\n') {
                                p0.delete(i, i + 1)
                                return
                            }
                        }
                    }
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }
            })
            autoCompleteTextView.setOnTouchListener(View.OnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    if (event.rawX >= (autoCompleteTextView.right - autoCompleteTextView.compoundPaddingRight)) {
                        autoCompleteTextView.setText("")
                        autoCompleteTextView.isFocusableInTouchMode = false
                        autoCompleteTextView.isFocusable = false
                        autoCompleteTextView.isFocusableInTouchMode = true
                        autoCompleteTextView.isFocusable = true
                        autoCompleteTextView.setCompoundDrawablesWithIntrinsicBounds(inactiveIconSearch, 0, 0, 0)
                        return@OnTouchListener true
                    }
                }
                return@OnTouchListener false
            })

            autoCompleteTextView.onFocusChangeListener = View.OnFocusChangeListener { _, b ->
                if (!b && autoCompleteTextView.text.toString() == "") {
                    autoCompleteTextView.setCompoundDrawablesWithIntrinsicBounds(inactiveIconSearch, 0, 0, 0)
                } else {
                    if (b && autoCompleteTextView.text.toString() != "") {
                        autoCompleteTextView.setCompoundDrawablesWithIntrinsicBounds(activeIconSearch, 0, clearIcon, 0)
                    } else {
                        if (!b && autoCompleteTextView.text.toString() != "") {
                            autoCompleteTextView.setCompoundDrawablesWithIntrinsicBounds(activeIconSearch, 0, clearIcon, 0)
                        } else {
                            if (b && autoCompleteTextView.text.toString() == "") {
                                autoCompleteTextView.setCompoundDrawablesWithIntrinsicBounds(activeIconSearch, 0, 0, 0)
                            }
                        }
                    }
                }
            }
            // Set an item click listener for ListView
            autoCompleteTextView.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
                // Get the selected item text from ListView
                val selectedItem = parent.getItemAtPosition(position) as String
                when (textViewSwitch) {
                    "project" -> viewModel.setProjectSearch(selectedItem)
                    "user" -> viewModel.setUserSearch(selectedItem)
                    "task" -> viewModel.setTaskNameSearch(selectedItem)
                }
                viewModel.getPtsItems()
                hideKeyboard()
            }
            autoCompleteTextView.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    if (!list.contains(p0.toString())) {
                        when (textViewSwitch) {
                            "project" -> viewModel.setProjectSearch("")
                            "user" -> viewModel.setUserSearch("")
                            "task" -> viewModel.setTaskNameSearch("")
                        }
                        viewModel.getPtsItems()
                    }
                }
            })
        }
    }

    private fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        Log.e(LOG_TAG, "ON RESUME Thread: Thread: ${Thread.currentThread().name}")
        viewModel.getPtsItems()
        viewModel.restoreView()
    }

    private fun initBasicViews() {
        this@MainFragment.binding.rvListView.layoutManager = LinearLayoutManager(context)
        this@MainFragment.binding.rvListView.adapter = ptsItemAdapter
        this@MainFragment.binding.rvListView.addItemDecoration(DividerItemDecoration(context, VERTICAL).apply {
            this.setDrawable(context?.getDrawable(item_decorator)!!)
        })
        with(viewModel) {
            firestoreSyncResult().observe(viewLifecycleOwner, {
                it?.getContentIfNotHandled().let { result ->
                    result?.let { it1 ->
                        if (!it1) {
                            Snackbar.make(binding.root, "Cloud sync FAIL", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            })

            viewModel.globalMinutes().observe(viewLifecycleOwner, {
                it?.let {
                    this@MainFragment.binding.textMinute.text = it
                }
            })

            viewModel.globalHours().observe(viewLifecycleOwner, {
                it?.let {
                    this@MainFragment.binding.textHour.text = it
                }
            })

            snackbarMsg().observe(viewLifecycleOwner, {
                it?.getContentIfNotHandled()?.let { result ->
                    Snackbar.make(binding.root, result, Snackbar.LENGTH_LONG).show()
                }
            })

            ptsItems.observe(viewLifecycleOwner, {
                ptsItemAdapter.updateDate(it)
//                Log.d(LOG_TAG, "ptsItems update data: ${it.size}")
            })

            isPopUpWindowClosed().observe(viewLifecycleOwner, {
                it.let {
                    if (it == EventType.PHONECALL_EVENT) {
                        pw?.dismiss()
                        binding.phoneButton.setBackgroundResource(phone_button)
                    }
                    if (it == EventType.EMAIL_EVENT) {
                        pw?.dismiss()
                        binding.mailButton.setBackgroundResource(mail_button)
                    }
                    if (it == EventType.MEETING_EVENT) {
                        pw?.dismiss()
                        binding.meetingButton.setBackgroundResource(meeting_button)
                    }
                    if (it == EventType.TRAVEL_EVENT) {
                        pw?.dismiss()
                        binding.travelButton.setBackgroundResource(travel_button)
                    }
                }
            })

            isPopUpWindowOpend().observe(viewLifecycleOwner, {
                it?.let {
                    if (it) {
                        StateRepository.getInstance().currentPTS?.event?.let {
                            StateRepository.getInstance().currentPTS?.projectName?.let { it1 ->
                                if (it == EventType.PHONECALL_EVENT || it == EventType.EMAIL_EVENT ||
                                        it == EventType.MEETING_EVENT || it == EventType.TRAVEL_EVENT) {
                                    createPopUpWindow(it, it1)
                                }
                            }
                        }
                    }
                }
            })

            isPhoneButtonAnimate().observe(viewLifecycleOwner, {
                it.let {
                    if (it.peekContent()) {
                        binding.phoneButton.setBackgroundResource(phone_button_presed)
                    } else {
                        binding.phoneButton.setBackgroundResource(phone_button)
                    }
                }
            })

            isEmailButtonAnimate().observe(viewLifecycleOwner, {
                it.let {
                    if (it.peekContent()) {
                        binding.mailButton.setBackgroundResource(mail_button_presed)
                    } else {
                        binding.mailButton.setBackgroundResource(mail_button)
                    }
                }
            })

            isMeetingButtonAnimate().observe(viewLifecycleOwner, {
                it.let {
                    if (it.peekContent()) {
                        binding.meetingButton.setBackgroundResource(meeting_button_pressed)
                    } else {
                        binding.meetingButton.setBackgroundResource(meeting_button)
                    }
                }
            })

            isTravelButtonAnimate().observe(viewLifecycleOwner, {
                it.let {
                    if (it.peekContent()) {
                        binding.travelButton.setBackgroundResource(travel_button_pressed)
                    } else {
                        binding.travelButton.setBackgroundResource(travel_button)
                    }
                }
            })

            isSyncAnimate().observe(viewLifecycleOwner, {
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

            isAllUsers().observe(viewLifecycleOwner, {
                it.let {
                    if (it) {
                        RealmHandler.ioScope.launch {
                            val allProjects = realmHandler.getAllProjectNamesFromDB()?.toArray()
                            val allUsers = realmHandler.getAllUsersAssignedFromDB().toArray()
                            val allTasks = realmHandler.getAllTasksFromDB()?.toArray()
                            if (allProjects != null) {
                                val list = arrayListOf<String>()
                                for (i in allProjects.indices) {
                                    list.add(allProjects[i].toString())
                                    Log.d(LOG_TAG, allProjects[i].toString())
                                }
                                addAdapter(list, searchByProject, "project")
                            }
                            if (allUsers.isNotEmpty()) {
                                val list = arrayListOf<String>()
                                for (i in allUsers.indices) {
                                    list.add(allUsers[i].toString())
                                    Log.d(LOG_TAG, allUsers[i].toString())
                                }
                                addAdapter(list, searchByUser, "user")
                            }
                            if (allTasks != null) {
                                val list = arrayListOf<String>()
                                for (i in allTasks.indices) {
                                    list.add(allTasks[i].toString())
                                    Log.d(LOG_TAG, allTasks[i].toString())
                                }
                                addAdapter(list, searchByTask, "task")
                            }
                        }
                        searchByUser.setVisibleOrGone(true)
                        rv_listView.adapter?.notifyDataSetChanged()
                        rv_listView.requestLayout()
                    } else {
                        RealmHandler.ioScope.launch {
                            val allProjects = realmHandler.getAllProjectNamesFromDBByUser().toArray()
                            val allUsers = realmHandler.getAllUsersAssignedFromDB().toArray()
                            val allTasks = realmHandler.getAllTasksFromDBByUser().toArray()
                            if (allProjects.isNotEmpty()) {
                                val list = arrayListOf<String>()
                                for (i in allProjects.indices) {
                                    list.add(allProjects[i].toString())
                                    Log.d(LOG_TAG, allProjects[i].toString())
                                }
                                addAdapter(list, searchByProject, "project")
                            }
                            if (allUsers.isNotEmpty()) {
                                val list = arrayListOf<String>()
                                for (i in allUsers.indices) {
                                    list.add(allUsers[i].toString())
                                    Log.d(LOG_TAG, allUsers[i].toString())
                                }
                                addAdapter(list, searchByUser, "user")
                            }
                            if (allTasks.isNotEmpty()) {
                                val list = arrayListOf<String>()
                                for (i in allTasks.indices) {
                                    list.add(allTasks[i].toString())
                                    Log.d(LOG_TAG, allTasks[i].toString())
                                }
                                addAdapter(list, searchByTask, "task")
                            }
                        }
                        searchByUser.setVisibleOrGone(false)
                        rv_listView.requestLayout()
                    }
                }
            })

            isKeyboardIsShowen().observe(viewLifecycleOwner, Observer {
                it.let {
                    val location = IntArray(2)
                    var y = 0;
                    var x = 0;
                    val fontSize: Float = this@MainFragment.resources.getDimension(R.dimen._52sdp)
                    when (StateRepository.getInstance().getEvent()) {
                        EventType.PHONECALL_EVENT -> {
                            this@MainFragment.binding.phoneAnchor.getLocationOnScreen(location)
                            y = location[1]
                            this@MainFragment.binding.phoneButton.getLocationOnScreen(location)
                            x = location[0]
                        }
                        EventType.EMAIL_EVENT -> {
                            this@MainFragment.binding.mailAnchor.getLocationOnScreen(location)
                            y = location[1]
                            this@MainFragment.binding.mailButton.getLocationOnScreen(location)
                            x = location[0]
                        }
                        EventType.MEETING_EVENT -> {
                            this@MainFragment.binding.meetingAnchor.getLocationOnScreen(location)
                            y = location[1]
                            this@MainFragment.binding.meetingButton.getLocationOnScreen(location)
                            x = location[0]
                        }
                        EventType.TRAVEL_EVENT -> {
                            this@MainFragment.binding.travellingAnchor.getLocationOnScreen(location)
                            y = location[1]
                            this@MainFragment.binding.travelButton.getLocationOnScreen(location)
                            x = location[0]
                        }
                        else -> -1
                    }
                    if (popUpPPositionLocation[0] == x && popUpPPositionLocation[1] == y - fontSize.roundToInt()) {
                    } else {
                        pw?.update(x, y - fontSize.roundToInt(), -1, -1)
                        popUpPPositionLocation.set(0, x)
                        popUpPPositionLocation.set(1, y - fontSize.roundToInt())
                    }
                }
            })

            isShowSpinnerDialog().observe(viewLifecycleOwner, { it ->
                it.let { it1 ->
                    if (it1 != EventType.NONE) {
                        val eventTypeForThread = it1
                        RealmHandler.ioScope.launch {
                            val allProjects = realmHandler.getAllProjectNamesFromDB()
                            uiScope.launch {
                                if (allProjects == null) {
                                    Toast.makeText(
                                            context,
                                            "Error, do download and parse first",
                                            Toast.LENGTH_LONG
                                    ).show()
                                    ibtn_addProject.visibility = View.VISIBLE
                                    textAddNewProject.visibility = View.VISIBLE
                                } else {
                                    ibtn_addProject.visibility = View.INVISIBLE
                                    textAddNewProject.visibility = View.INVISIBLE
                                    val allProjectsList = ArrayList(allProjects)

                                    val adb = AlertDialog.Builder(context)
                                    val v: View = LayoutInflater.from(context).inflate(R.layout.communications_dialog, null)
                                    val rippleViewClose = v.findViewById<View>(R.id.buttonCancel) as TextView
                                    val chrono = v.findViewById<View>(R.id.chronometerCommunications) as Chronometer
                                    chrono.setBase(SystemClock.elapsedRealtime())
                                    chrono.format = "00:%s"
                                    chrono.start()
                                    chrono.setOnChronometerTickListener { cArg ->
                                        val elapsedMillis = SystemClock.elapsedRealtime() - cArg.base
                                        if (elapsedMillis > 3600000L) {
                                            cArg.format = "0%s"
                                        } else {
                                            cArg.format = "00:%s"
                                        }
                                    }
                                    val listView =
                                            v.findViewById<View>(R.id.list) as ListView
                                    val searchBox =
                                            v.findViewById<View>(R.id.searchBox) as EditText
                                    val adapter = context?.let { it ->
                                        AutoCompleteAdapter(
                                                it, // Context
                                                R.layout.items_view, // Layout
                                                0,
                                                allProjectsList// Array
                                        )
                                    }
                                    listView.adapter = adapter
                                    adb.setView(v)
                                    val alertDialog = adb.create()

                                    listView.onItemClickListener =
                                            AdapterView.OnItemClickListener { _, view, _, _ ->
                                                val t =
                                                        view.findViewById<View>(R.id.text1) as TextView
                                                searchBox.setText(t.text, TextView.BufferType.EDITABLE)
                                                searchBox.setSelection(t.text.length)
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
                                                pw!!.contentView = mbinding.root

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

                                                viewModel.phoneTime().observe(viewLifecycleOwner, {
                                                    it?.let {
                                                        if (StateRepository.getInstance().currentPTS!!.event == EventType.PHONECALL_EVENT) {
                                                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.text = it
                                                            pw?.contentView?.invalidate()
                                                        }
                                                    }
                                                })
                                                viewModel.emailTime().observe(viewLifecycleOwner, {
                                                    it?.let {
                                                        if (StateRepository.getInstance().currentPTS!!.event == EventType.EMAIL_EVENT) {
                                                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.text = it
                                                            pw?.contentView?.invalidate()
                                                        }
                                                    }
                                                })
                                                viewModel.meetingTime().observe(viewLifecycleOwner, {
                                                    it?.let {
                                                        if (StateRepository.getInstance().currentPTS!!.event == EventType.MEETING_EVENT) {
                                                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.text = it
                                                            pw?.contentView?.invalidate()
                                                        }
                                                    }
                                                })
                                                viewModel.travelTime().observe(viewLifecycleOwner, {
                                                    it?.let {
                                                        if (StateRepository.getInstance().currentPTS!!.event == EventType.TRAVEL_EVENT) {
                                                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.text = it
                                                            pw?.contentView?.invalidate()
                                                        }
                                                    }
                                                })

                                                val buttonSet = pw?.contentView?.findViewById<Button>(R.id.edit)
                                                buttonSet?.setOnClickListener {
                                                    showAlertDialogEditPhoneEmailMeetingProject {}
                                                }

                                                pw?.contentView?.findViewById<TextView>(R.id.tvProject)?.text = selectedItem
                                                pw!!.showAtLocation(
                                                        getView(), // Location to display popup window
                                                        Gravity.NO_GRAVITY, // Exact position of layout to display popup
                                                        x, // X offset
                                                        y - fontSize.roundToInt()) // Y offset
                                                popUpPPositionLocation.set(0, x)
                                                popUpPPositionLocation.set(1, y - fontSize.roundToInt())
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
                                            adapter?.filter?.filter(searchBox.text.toString())
                                        }
                                    })
                                    rippleViewClose.setOnClickListener {
                                        when (eventTypeForThread) {
                                            EventType.PHONECALL_EVENT -> {
                                                binding.phoneButton.setBackgroundResource(phone_button)
                                            }
                                            EventType.EMAIL_EVENT -> {
                                                binding.mailButton.setBackgroundResource(mail_button)
                                            }
                                            EventType.MEETING_EVENT -> {
                                                binding.meetingButton.setBackgroundResource(meeting_button)
                                            }
                                            EventType.TRAVEL_EVENT -> {
                                                binding.travelButton.setBackgroundResource(travel_button)
                                            }
                                        }
                                        alertDialog.dismiss()
                                        onSpinnerDialogShowed()
                                    }
                                    alertDialog.setCancelable(true)
                                    alertDialog.setCanceledOnTouchOutside(false)
                                    alertDialog.show()

                                }
                            }
                        }
                    }
                }
            })

            errors().observe(viewLifecycleOwner, {
                it?.let { event ->
                    event.getContentIfNotHandled()?.let { message ->
                        Toast.makeText(this@MainFragment.context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            })

            getZeroFilesImport().observe(viewLifecycleOwner, {
                if (it) {
                    Toast.makeText(context, "Zero files parse", Toast.LENGTH_LONG).show()
                }
            })

            parsedFilesNumber().observe(viewLifecycleOwner, {
                it?.getContentIfNotHandled().let { it1 ->
                    if (it1 != null) {
                        if (it1 > 0)
                            Snackbar.make(binding.root, "Get/Parse OK, $it1 .mpp", Snackbar.LENGTH_LONG).show()
                    }
                }
            })

            isLoading().observe(viewLifecycleOwner, {
                binding.loadingAnimation.enableMergePathsForKitKatAndAbove(true)
                if (it) {
                    binding.loadingAnimation.setVisibleOrGone(true)
                    binding.loadingAnimation.playAnimation()
                } else {
                    binding.loadingAnimation.setVisibleOrGone(false)
                    binding.loadingAnimation.pauseAnimation()
                }
            })

            refreshMainFragment().observe(viewLifecycleOwner, {
                if (it) {
                    viewModel.refreshMainFragment.value = false
                    Navigation.findNavController(binding.root).navigate(MainFragmentDirections.actionMainFragmentToMainFragment())
                }
            })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)
    }

    fun onBackHandle() {
        if (isVisible) {
            when (StateRepository.getInstance().getEvent()) {
                EventType.PHONECALL_EVENT, EventType.EMAIL_EVENT, EventType.MEETING_EVENT, EventType.TRAVEL_EVENT -> {
                    StateRepository.getInstance().setEvent(EventType.PAUSE_EVENT)
                }
                else -> {
                }
            }
            requireActivity().finish()
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
                if (viewModel.phoneTime() != null || viewModel.phoneTime().value != "") {
                    toPost = if (viewModel.phoneTime().value?.substringBefore(':')?.length!! == 2) {
                        "00" + viewModel.phoneTime().value!!.substringBefore(':') + ":" + viewModel.phoneTime().value!!.substringAfter(':').substringBefore(':')
                    } else {
                        if (viewModel.phoneTime().value?.substringBefore(':')?.length!! == 3) {
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
                toPost = if (viewModel.emailTime() != null || viewModel.emailTime().value != "") {
                    if (viewModel.emailTime().value?.substringBefore(':')?.length!! == 2) {
                        "00" + viewModel.emailTime().value!!.substringBefore(':') + ":" + viewModel.emailTime().value!!.substringAfter(':').substringBefore(':')
                    } else {
                        if (viewModel.emailTime().value?.substringBefore(':')?.length!! == 3) {
                            "0" + viewModel.emailTime().value!!.substringBefore(':') + ":" + viewModel.emailTime().value!!.substringAfter(':').substringBefore(':')
                        } else {
                            viewModel.emailTime().value!!.substringBefore(':') + ":" + viewModel.emailTime().value!!.substringAfter(':').substringBefore(':')
                        }
                    }
                } else {
                    "0000:00"
                }
            }
            EventType.MEETING_EVENT -> {
                toPost = if (viewModel.meetingTime() != null || viewModel.meetingTime().value != "") {
                    if (viewModel.meetingTime().value?.substringBefore(':')?.length!! == 2) {
                        "00" + viewModel.meetingTime().value!!.substringBefore(':') + ":" + viewModel.meetingTime().value!!.substringAfter(':').substringBefore(':')
                    } else {
                        if (viewModel.meetingTime().value?.substringBefore(':')?.length!! == 3) {
                            "0" + viewModel.meetingTime().value!!.substringBefore(':') + ":" + viewModel.meetingTime().value!!.substringAfter(':').substringBefore(':')
                        } else {
                            viewModel.meetingTime().value!!.substringBefore(':') + ":" + viewModel.meetingTime().value!!.substringAfter(':').substringBefore(':')
                        }
                    }
                } else {
                    "0000:00"
                }
            }
            EventType.TRAVEL_EVENT -> {
                toPost = if (viewModel.travelTime() != null || viewModel.travelTime().value != "") {
                    if (viewModel.travelTime().value?.substringBefore(':')?.length!! == 2) {
                        "00" + viewModel.travelTime().value!!.substringBefore(':') + ":" + viewModel.travelTime().value!!.substringAfter(':').substringBefore(':')
                    } else {
                        if (viewModel.travelTime().value?.substringBefore(':')?.length!! == 3) {
                            "0" + viewModel.travelTime().value!!.substringBefore(':') + ":" + viewModel.travelTime().value!!.substringAfter(':').substringBefore(':')
                        } else {
                            viewModel.travelTime().value!!.substringBefore(':') + ":" + viewModel.travelTime().value!!.substringAfter(':').substringBefore(':')
                        }
                    }
                } else {
                    "0000:00"
                }
            }
        }

        editTextCommunicationTime.setText(toPost)

        buttonSet.setOnClickListener {
            val timeStr = editTextCommunicationTime.text.toString()
            if (timeStr.isNotEmpty()) {
                try {
                    if (timeStr.contains(':')) {
                        var hours = timeStr.substringBefore(':')
                        var minutes = timeStr.substringAfter(':')
                        if (hours.length > 4) {
                            hours = hours.substring(0, 4)
                        }
                        if (minutes.length > 2) {
                            minutes = minutes.substring(0, 2)
                        }
                        if (minutes.isEmpty()) {
                            minutes = "00"
                        }
                        if (minutes.length == 1) {
                            minutes += '0'
                        }
                        val milis = hours.toLong() * 3600000 + minutes.toLong() * 60000
                        val timeToDoubleVar = milis / 3600000.0
                        viewModel.updatePhoneCallEmailMeetingDurationNotLoop(StateRepository.getInstance().getEvent(), timeToDoubleVar)
                    } else {
                        var hours = timeStr
                        if (hours.length > 4) {
                            hours = hours.substring(0, 4)
                        }
                        val milis = hours.toLong() * 3600000
                        val timeToDoubleVar = milis / 3600000.0
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

    fun onButtonStatistic() {
        view?.let {
            pw?.dismiss()
            viewModel.buttonPauseTaskPressed()
            val navController = Navigation.findNavController(it)
            navController.navigate(MainFragmentDirections.actionMainFragmentToStatisticFragment())
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
                pw!!.contentView = mbinding.root

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    pw!!.isAttachedInDecor = true
                }

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

                viewModel.phoneTime().observe(viewLifecycleOwner, {
                    it?.let {
                        if (StateRepository.getInstance().currentPTS!!.event == EventType.PHONECALL_EVENT) {
                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                            pw?.contentView?.invalidate()
                        }
                    }
                })
                viewModel.emailTime().observe(viewLifecycleOwner, {
                    it?.let {
                        if (StateRepository.getInstance().currentPTS!!.event == EventType.EMAIL_EVENT) {
                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                            pw?.contentView?.invalidate()
                        }
                    }
                })
                viewModel.meetingTime().observe(viewLifecycleOwner, {
                    it?.let {
                        if (StateRepository.getInstance().currentPTS!!.event == EventType.MEETING_EVENT) {
                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                            pw?.contentView?.invalidate()
                        }
                    }
                })
                viewModel.travelTime().observe(viewLifecycleOwner, {
                    it?.let {
                        if (StateRepository.getInstance().currentPTS!!.event == EventType.TRAVEL_EVENT) {
                            pw?.contentView?.findViewById<TextView>(R.id.textViewProjectTime)?.setText(it)
                            pw?.contentView?.invalidate()
                        }
                    }
                })
                val buttonSet = pw?.contentView?.findViewById<Button>(R.id.edit)
                buttonSet?.setOnClickListener {
                    showAlertDialogEditPhoneEmailMeetingProject {}
                }
                pw?.contentView?.findViewById<TextView>(R.id.tvProject)?.setText(selectedItem)
                pw!!.showAtLocation(
                        getView(), // Location to display popup window
                        Gravity.NO_GRAVITY, // Exact position of layout to display popup
                        x, // X offset
                        y - fontSize.roundToInt()) // Y offset
                popUpPPositionLocation.set(0, x)
                popUpPPositionLocation.set(1, y - fontSize.roundToInt())
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
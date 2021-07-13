package com.timejet.bio.timejet.ui

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.timejet.bio.timejet.BuildConfig
import com.timejet.bio.timejet.Mail
import com.timejet.bio.timejet.R
import com.timejet.bio.timejet.databinding.ActivityBinding
import com.timejet.bio.timejet.repository.RealmHandler
import com.timejet.bio.timejet.repository.StateRepository
import com.timejet.bio.timejet.ui.card.CardFragment
import com.timejet.bio.timejet.ui.card.CardFragmentDirections
import com.timejet.bio.timejet.ui.main.MainFragment
import com.timejet.bio.timejet.ui.main.MainFragmentDirections
import com.timejet.bio.timejet.ui.main.deleteAll
import com.timejet.bio.timejet.ui.statistic.StatisticFragment
import com.timejet.bio.timejet.ui.statistic.StatisticFragmentDirections
import com.timejet.bio.timejet.utils.*
import kotlinx.android.synthetic.main.activity.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener{

    private val LOG_TAG = this::class.java.simpleName

    lateinit var toggle: ActionBarDrawerToggle
    lateinit var toolbar:Toolbar
    lateinit var drawer:DrawerLayout
    lateinit var navigationView: View
    lateinit var viewModel:MainActivityViewModel
    private lateinit var alarmManager: AlarmManager
    private lateinit var pendingIntent: PendingIntent
    var onDrawerStateChangeCallback: OnDrawerStateChangeCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deleteAll = 0

        val factory = SavedStateViewModelFactory(application, this,null)
        viewModel = ViewModelProvider(this,factory).get(MainActivityViewModel::class.java)

        val binding: ActivityBinding = DataBindingUtil.setContentView(this, R.layout.activity)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        setSupportActionBar(binding.toolbar)

        drawer = binding.drawer
        toolbar = binding.toolbar
        toolbar.setBackgroundColor(Color.parseColor("#ECF0F4"))

        navigationView = binding.navigationView

        toggle = ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayShowTitleEnabled(false)


        toolbar.setNavigationOnClickListener {

            val navController = Navigation.findNavController(this,R.id.nav_host_fragment)
            if (StatisticFragment::class.java.simpleName.equals(navController.currentDestination?.label)) {
                // If current fragment is StatisticFragment
                // we return to prev. fragment, remove back button
                // in toolbar and show burger icon instead
                navController.popBackStack()
                refreshBurgerIcon()
            } else {
                if (drawer.isDrawerOpen(navigationView)) {
                    drawer.closeDrawer(navigationView)
                } else {
                    drawer.openDrawer(navigationView)
                }
            }
        }

        drawer.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (!drawer.isDrawerOpen(navigationView)) {
                    onDrawerStateChangeCallback?.onDrawerOpened()
                }
            }

            override fun onDrawerClosed(drawerView: View) {
                    onDrawerStateChangeCallback?.onDrawerClosed()
            }

            override fun onDrawerOpened(drawerView: View) {
            }

        })

        navigationView.navigationView.setNavigationItemSelectedListener(this)

        val navController = Navigation.findNavController(this,R.id.nav_host_fragment)


        navController.addOnDestinationChangedListener { _, destination, arguments ->
            when(destination.id) {
                R.id.loginFragment -> {
                    toolbar.visibility = View.GONE
                    drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    Log.d(LOG_TAG, "navController: ${destination.label}")
                }

                R.id.mainFragment -> {
                    toolbar.visibility = View.VISIBLE
                    drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    hideKeyboard()
                    Log.d(LOG_TAG, "navController: ${destination.label}")
                    fillUserText()
                    viewModel.afterRestart().value?.let {
                    } ?: Log.d(LOG_TAG, "Destination changed: value is NULL")
                }

                R.id.cardFragment -> {
                    hideKeyboard()
                    toolbar.visibility = View.VISIBLE
                    Log.d(LOG_TAG, "navController: ${destination.label}: args: ${arguments.toString()}")
                }

                R.id.statisticFragment -> {
                    hideKeyboard()
                    toolbar.visibility = View.VISIBLE
                    Log.d(LOG_TAG, "navController: ${destination.label}: args: ${arguments.toString()}")
                }
            }
        }

        viewModel.navigatoToLogin.observe(this, {
            if(it) {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        })

        viewModel.refreshMainFragment.observe(this, {
            if(it) {
                viewModel.refreshMainFragment.value = false

                when (navController.currentDestination?.label) {
                    MainFragment::class.java.simpleName -> navController.navigate(MainFragmentDirections.actionMainFragmentToMainFragment())
                    StatisticFragment::class.java.simpleName -> navController.navigate(StatisticFragmentDirections.actionStatisticFragmentToMainFragment())
                }
            }
        })

        viewModel.parsedFilesNumber.observe(this, {
            it?.getContentIfNotHandled().let {it1 ->
                if (it1 != null) {
                    if (it1 > 0)
                        Snackbar.make(this.window!!.decorView, "Get/Parse OK, $it1 .mpp", Snackbar.LENGTH_LONG).show()
                }
            }
        })

        viewModel.isLoading().observe(this, {
            binding.loadingAnimation.enableMergePathsForKitKatAndAbove(true)
            if(it) {
                binding.loadingAnimation.setVisibleOrGone(true)
                binding.loadingAnimation.playAnimation()
            } else {
                binding.loadingAnimation.setVisibleOrGone(false)
                binding.loadingAnimation.pauseAnimation()
            }
        })

        viewModel.getZeroFilesImport().observe(this, {
            if(it) {
                Toast.makeText(applicationContext, "Zero files parse", Toast.LENGTH_LONG).show()
            }
        })

        viewModel.firestoreSyncResult().observe(this, {
            it?.getContentIfNotHandled().let { result ->
                result?.let { it1 ->
                    if (!it1) {
                        Snackbar.make(binding.root, "Cloud sync FAIL", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        })

        alarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingIntent = Intent(this, MyBroadCastReceiver::class.java).let { intent ->
            PendingIntent.getBroadcast(this, 0, intent, 0)
        }
        alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 60 * 1000,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                pendingIntent
        )
    }

    fun refreshBurgerIcon() {
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        toggle.isDrawerIndicatorEnabled = true
        toggle.syncState()
    }

    override fun onResume() {
        super.onResume()
        val navController = Navigation.findNavController(this,R.id.nav_host_fragment)
        if (MainFragment::class.java.simpleName == navController.currentDestination?.label) {
             navController.navigate(MainFragmentDirections.actionMainFragmentToMainFragment())
        }
    }

    override fun onNavigationItemSelected(p0: MenuItem): Boolean {
        Log.d(LOG_TAG, "MenuItemSelected: ${p0.itemId}")
        when(p0.itemId) {
            R.id.nav_download_parse_tasks -> viewModel.getAllProjectsMPPClick()
            R.id.get_statistic -> showStatistic(this)
            R.id.nav_delete_all -> deleteAllDialog()
            R.id.nav_sync -> viewModel.onButtonSyncClick()
            R.id.nav_send_feedback -> showAlertDialogSendEmail(this)
            R.id.nav_logout -> viewModel.logout()
        }
        p0.isChecked = false
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    fun deleteAllDialog() {
        AlertDialog.Builder(this).setTitle(getString(R.string.delete_all))
            .setMessage(R.string.sure_q)
            .setPositiveButton(getString(R.string.yes)
            ) { _, _ ->
                RealmHandler.ioScope.launch {
                    RealmHandler.getInstance().deleteAllRealmDB()

                    withContext(Dispatchers.IO) {
                        Utils.deleteAllLocalFiles(applicationContext)
                    }

                    StateRepository.getInstance().currentPTS = null
                    deleteAll = 1

                    withContext(Dispatchers.Main) {
                        viewModel.logout()
                    }
                }
            }
            .setNegativeButton(getString(R.string.no)) { _, _ -> }
            .create()
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        fillUserText()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun fillUserText() {
        drawer.findViewById<TextView>(R.id.TVuserName)?.text = LocalUserInfo.getUserName(applicationContext)
        drawer.findViewById<TextView>(R.id.TVemail)?.text = LocalUserInfo.getUserEmail(applicationContext)
        drawer.findViewById<TextView>(R.id.TVdomainName)?.text = LocalUserInfo.getUserDomain(applicationContext)
        drawer.findViewById<TextView>(R.id.TVgroupName)?.text = LocalUserInfo.getUserGroup(applicationContext)
        drawer.findViewById<TextView>(R.id.tvVersion)?.text = "Version: " + BuildConfig.VERSION_NAME
    }

    fun hideKeyboard() {
        val view = currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent):Boolean {
        if (currentFocus != null)
        {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        }
        return super.dispatchTouchEvent(ev)
    }

    fun showStatistic(activity: Context){
        val navController = Navigation.findNavController(this,R.id.nav_host_fragment)
        if (CardFragment::class.java.simpleName == navController.currentDestination?.label){
            navController.navigate(CardFragmentDirections.actionCardFragmentToStatisticFragment())
        } else {
            navController.navigate(MainFragmentDirections.actionMainFragmentToStatisticFragment())
        }
    }

    fun showAlertDialogSendEmail(activity: Context) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_write_message)
        val buttonSet = dialog.findViewById<Button>(R.id.buttonSet)
        val buttonCancel = dialog.findViewById<Button>(R.id.buttonCancel)

        val editTextToSend = dialog.findViewById<EditText>(R.id.editTextToSend)

        editTextToSend.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        buttonSet.setOnClickListener {
            val messageText = editTextToSend.text.toString()
            val recipients = arrayOf("timejet2020@gmail.com")
            val email = SendEmailAsyncTask()
//            email.activity = this
            val euser = "timejet.test@gmail.com"
            val epassword = "Qwerty12#"
            val subject = "[TimeJet] Feedback from " + LocalUserInfo.getUserEmail(activity)!!
            email.m = Mail(euser, epassword).apply {
                _from = euser
                body = messageText
                _to = recipients
                _subject = subject
            }
            email.execute(activity)
            dialog.dismiss()
        }
        buttonCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    interface OnDrawerStateChangeCallback {
        fun onDrawerOpened()
        fun onDrawerClosed()
    }
}
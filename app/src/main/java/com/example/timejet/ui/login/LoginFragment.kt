package com.timejet.bio.timejet.ui.login

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.FirebaseApp
import com.timejet.bio.timejet.R
import com.timejet.bio.timejet.repository.StateRepository
import com.timejet.bio.timejet.repository.databases.onlineDB.FirebaseOnlineDB
import com.timejet.bio.timejet.ui.main.ERROR_GET_UID
import com.timejet.bio.timejet.utils.Utils
import java.io.File
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import javax.net.ssl.SSLContext

class LoginFragment : Fragment() {

    private val LOG_TAG = this::class.java.simpleName

    lateinit var binding:com.timejet.bio.timejet.databinding.FragmentLoginBinding
    lateinit var viewModel:LoginViewModel

    private val email get() = binding.editTextEmail.text.trim().toString()
    private val password get() = binding.editTextPassword.text.toString()
    private val configFilesUrl get() = binding.editTextConfigFilesUrl.text.toString()
    private val userGroup get() = binding.editTextGroupName.text.toString()
    private val inputsAreValid get() = email.isNotEmpty() && password.isNotEmpty() && configFilesUrl.isNotEmpty() && userGroup.isNotEmpty()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_login, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName: String = requireContext().packageName
            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager?
            val isIgnoredBatteryOptimState = pm!!.isIgnoringBatteryOptimizations(packageName)
            if (!isIgnoredBatteryOptimState) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
        viewModel = ViewModelProviders.of(this).get(LoginViewModel::class.java)
        binding.viewModel = viewModel
        binding.callback = this
        binding.executePendingBindings()
        hideKeyboard()
        initializeViews()

        try {
            // Google Play will install latest OpenSSL
            ProviderInstaller.installIfNeeded(context)
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, null, null)
            sslContext.createSSLEngine()
        } catch (e: GooglePlayServicesRepairableException) {
            e.printStackTrace();
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace();
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace();
        } catch (e: KeyManagementException) {
            e.printStackTrace();
        }
    }

    private fun initializeViews() = with(viewModel) {

        isLoading.observe(viewLifecycleOwner, Observer {
            showProgressBar.postValue(!it)
        })

        loginAttemptResult.observe(viewLifecycleOwner, Observer {
            isAnimationPlaying.postValue(!it)
        })

        firebaseApp.observe(viewLifecycleOwner, Observer<FirebaseApp> { firebaseApp ->
        if (firebaseApp != null)
                navigateToMain()
        })
        userEmail.observe(viewLifecycleOwner, Observer<String> { email ->
            binding.editTextEmail.setText(email.toLowerCase())
        })
        userGroup.observe(viewLifecycleOwner, Observer<String> { group ->
            binding.editTextGroupName.setText(group)
        })
        configFilesUrl.observe(viewLifecycleOwner, Observer<String> { configFilesUrl ->
            binding.editTextConfigFilesUrl.setText(configFilesUrl)
        })
        loginAttemptResult.observe(viewLifecycleOwner, Observer { success ->
            if (success!!) loginSuccess()
        })
        configFetchFail.observe(viewLifecycleOwner, Observer<Pair<String, String>> { pair ->
            if (pair?.first != null)
                if (pair.first == "get_config_fail")
                    showToast("Get config FAIL\n${pair.second}")
        })
        configFetchSuccess.observe(viewLifecycleOwner, Observer<Pair<String, File?>> { pair ->
            if (pair?.first != null)
                if (pair.first == "get_config_ok") {
                    processConfigFetch(pair)
                }
        })
    }

    private fun loginSuccess() {
        navigateToMain()
    }

    private fun navigateToMain() {
        //StateRepository.getInstance().restore()
        val currentPTS = StateRepository.getInstance().currentPTS

        if(currentPTS != null && currentPTS.uid != ERROR_GET_UID && currentPTS.projectName.isNotEmpty()) {
            Log.d(LOG_TAG,"navigateToMain with params: UID: $currentPTS.uid, projectName: $currentPTS.projectName")
            val action = LoginFragmentDirections.actionLoginFragmentToMainFragment()
            action.uid = currentPTS.uid
            action.projectName = currentPTS.projectName
            Navigation.findNavController(requireView())
                .navigate(action)
        } else {
            Log.d(LOG_TAG,"navigateToMain: No UID or projectName")
            Navigation.findNavController(requireView())
                .navigate(R.id.action_loginFragment_to_mainFragment)
        }

    }

    private fun showToast(err: String) {
        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
    }

    fun Fragment.hideKeyboard() {
        view?.let { activity?.hideKeyboard(it) }
    }

    fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun onLoginClick() {
        if (inputsAreValid) {
            Utils.saveLoginData(email, configFilesUrl, userGroup, requireContext())
            try {
                viewModel.attemptLogin(email, password)
            } catch (e: Exception) {
                showToast("Error Init Firebase, Exit")
            }
        } else {
            showToast("One field is empty")
        }
    }

    private fun processConfigFetch(pair: Pair<String, File?>) {
        showToast("Get config OK")
        Utils.deleteLocalFile(requireContext(), "google-services.json")
        //Utils.deleteLocalFile(context, "config.zip")
        try {
            Utils.unzip(pair.second as File, activity?.applicationContext?.filesDir!!)
            val firebaseOnlineDB = FirebaseOnlineDB(requireContext())
            val dropboxToken =
                firebaseOnlineDB.loadFromAsset(File("${activity?.applicationContext?.filesDir!!.absolutePath}/dropboxToken.txt"))
            if (dropboxToken.isNotEmpty())
                Utils.saveTokenDropbox(requireContext(), dropboxToken)
            firebaseOnlineDB.attemptToInitCloudIsSuccess(binding.editTextEmail.text.toString(), binding.editTextPassword.text.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            viewModel.isLoading.value = false
            showToast("Error,\nNo Dropbox Token")
        }
    }
}
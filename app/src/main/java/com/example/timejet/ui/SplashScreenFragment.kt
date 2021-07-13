package com.timejet.bio.timejet.ui

import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.timejet.bio.timejet.R
import kotlinx.android.synthetic.main.splash_screen.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class SplashScreenFragment : Fragment(), CoroutineScope {

//    private lateinit var binding: com.timejet.bio.timejet.databinding.SplashScreenBinding
//    var mp = MediaPlayer.create(context, R.raw.clack)

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + Job()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.splash_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        var mp = MediaPlayer.create(context, R.raw.badabum)
        mp.start()
        launch {
            animationPreLoader.playAnimation()
            delay(3500)
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_splashScreen_to_loginFragment)
            }
        }
}
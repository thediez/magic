package com.timejet.bio.timejet.ui

import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.widget.ImageView

fun animateButton(context: Context?, imageView: ImageView, animRes: Int) {
    context?.let {
        imageView.setImageDrawable(context.getDrawable(animRes))
        val animation = imageView.drawable as AnimationDrawable
        animation.start()
    }
}
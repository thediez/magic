package com.timejet.bio.timejet.ui

import android.graphics.Typeface
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.TextView
import androidx.databinding.BindingAdapter

@BindingAdapter("visibleGone")
fun View.setVisibleOrGone(show: Boolean) {
    visibility = if (show) VISIBLE else GONE
}

@BindingAdapter("isRead")
fun setTypeface(v: TextView, isRead: Boolean?) {
    if (isRead!!)
        v.typeface = Typeface.DEFAULT
    else
        v.typeface = Typeface.DEFAULT_BOLD
}
package com.timejet.bio.timejet.ui.main

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import com.timejet.bio.timejet.R

class SpinnerCustomAdapter(context: Context,
                           @LayoutRes private val layoutResource: Int,
                           @IdRes private val textViewResourceId: Int = 0,
                           private val values: List<String>) : ArrayAdapter<String>(context, layoutResource, values) {


    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(convertView, parent, position, false)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(convertView, parent, position, true)
    }

    private fun createViewFromResource(convertView: View?, parent: ViewGroup, position: Int, isDropDown: Boolean): View {
        val context = parent.context
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_custom, parent, false)
        val label = view.findViewById(R.id.textView) as TextView
        val icon: ImageView = view.findViewById(R.id.imageView) as ImageView
        val triangle: ImageView = view.findViewById(R.id.triangle) as ImageView
        when(position){
            0 -> {
                label.setTextColor(context.resources.getColor(R.color.white))
                label.text = values[position]
                icon.setVisibility(View.GONE)
            }
            1 -> {
                label.text = values[position]
                label.setTextColor(context.resources.getColor(R.color.myYellow))
                icon.setImageResource(R.drawable.ic_inprogress)
            }
            2 -> {
                label.text = values[position]
                label.setTextColor(context.resources.getColor(R.color.myYellow))
                icon.setImageResource(R.drawable.ic_inprogress)
            }
            3 -> {
                label.text = values[position]
                label.setTextColor(context.resources.getColor(R.color.green))
                icon.setImageResource(R.drawable.ic_complete)
            }
            4 -> {
                label.text = values[position]
                label.setTextColor(context.resources.getColor(R.color.red))
                icon.setImageResource(R.drawable.ic_notcomplete)
            }
        }

        if (isDropDown) {
            if(position == 0 ){
                icon.setVisibility(View.GONE)
            }
            else{
                triangle.setVisibility(View.GONE)
                icon.setVisibility(View.VISIBLE)
            }
        } else {
            icon.setVisibility(View.GONE)
        }

        return view
    }
}
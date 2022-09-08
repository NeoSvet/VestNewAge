package ru.neosvet.vestnewage.view.basic

import android.view.View
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.getItemView(position: Int): View =
    findViewHolderForAdapterPosition(position)!!.itemView

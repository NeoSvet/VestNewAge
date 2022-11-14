package ru.neosvet.vestnewage.view.basic

import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation

class ResizeAnim(
    private val mView: View, private val hor: Boolean, private val iSize: Int
) : Animation() {
    var iStart = if (hor) mView.width else mView.height

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
        val size = iStart + ((iSize - iStart) * interpolatedTime).toInt()
        if (hor) mView.layoutParams.width = size else mView.layoutParams.height = size
        mView.requestLayout()
    }

    override fun willChangeBounds(): Boolean {
        return true
    }
}
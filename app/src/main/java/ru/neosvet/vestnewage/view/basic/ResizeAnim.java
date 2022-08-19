package ru.neosvet.vestnewage.view.basic;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

public class ResizeAnim extends Animation {
    private int iStart;
    private final int iSize;
    private final View mView;
    private final boolean hor;

    public ResizeAnim(View view, boolean hor, int size) {
        mView = view;
        iSize = size;
        this.hor = hor;
        if (hor)
            iStart = view.getWidth();
        else
            iStart = view.getHeight();
    }

    public void setStart(int i) {
        iStart = i;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        int size = iStart + (int) ((iSize - iStart) * interpolatedTime);
        if (hor)
            mView.getLayoutParams().width = size;
        else
            mView.getLayoutParams().height = size;
        mView.requestLayout();
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
    }

    @Override
    public boolean willChangeBounds() {
        return true;
    }
}

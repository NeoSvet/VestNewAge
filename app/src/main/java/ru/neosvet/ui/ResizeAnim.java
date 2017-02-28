package ru.neosvet.ui;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

public class ResizeAnim extends Animation {
    private int iStart, iSize;
    private View mView;
    private boolean bSquare;

    public ResizeAnim(View view, boolean square, int size) {
        mView = view;
        iSize = size;
        bSquare = square;
        if (square || size == 10)
            iStart = view.getHeight();
        else
            iStart = 10;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        int size = iStart + (int) ((iSize - iStart) * interpolatedTime);
        if (bSquare)
            mView.getLayoutParams().width = size;
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

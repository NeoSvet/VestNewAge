package ru.neosvet.ui;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.vestnewage.R;

public class Tip {
    private View object;
    private boolean show = false;
    private Animation anShow, anHide;
    private Timer timer;

    final Handler hHide = new Handler(message -> {
        if (show)
            object.startAnimation(anHide);
        return false;
    });

    public Tip(Context context, final View object) {
        this.object = object;
        anShow = AnimationUtils.loadAnimation(context, R.anim.show);
        anHide = AnimationUtils.loadAnimation(context, R.anim.hide);
        anHide.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                object.setVisibility(View.GONE);
                show = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    public void show() {
        if (show)
            return;
        show = true;
        object.setVisibility(View.VISIBLE);
        object.startAnimation(anShow);
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                hHide.sendEmptyMessage(0);
            }
        }, 2500);
    }

    public void hide() {
        if (!show)
            return;
        timer.cancel();
        object.clearAnimation();
        object.setVisibility(View.GONE);
        show = false;
    }

    public void hideAnimated() {
        if (!show)
            return;
        timer.cancel();
        object.clearAnimation();
        object.startAnimation(anHide);
    }

    public boolean isShow() {
        return show;
    }
}

package ru.neosvet.ui;

import android.content.Context;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.R;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.helpers.DateHelper;

public class StatusButton {
    private Context context;
    private Animation anRotate;
    private View panel;
    private TextView tv;
    private ImageView iv;
    private Animation anMin, anMax;
    private boolean crash = false, stop = true, time = false, visible;

    public StatusButton(Context context, View p) {
        this.context = context;
        this.panel = p;
        tv = (TextView) panel.findViewById(R.id.tvStatus);
        iv = (ImageView) panel.findViewById(R.id.ivStatus);

        anRotate = AnimationUtils.loadAnimation(context, R.anim.rotate);
        anRotate.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (!stop) //panel.getVisibility() == View.VISIBLE
                    iv.startAnimation(anRotate);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        if (context instanceof MainActivity) {
            anMin = AnimationUtils.loadAnimation(context, R.anim.minimize);
            anMin.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    panel.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            anMax = AnimationUtils.loadAnimation(context, R.anim.maximize);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void setLoad(boolean start) {
        stop = !start;
        if (start) {
            crash = false;
            time = false;
            panel.setVisibility(View.VISIBLE);
            visible = true;
            iv.startAnimation(anRotate);
        } else {
            panel.setVisibility(View.GONE);
            visible = false;
            iv.clearAnimation();
        }
    }

    public void setCrash(boolean crash) {
        this.crash = crash;
        if (crash) {
            stop = true;
            time = false;
            tv.setText(context.getResources().getString(R.string.crash));
            panel.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.shape_red));
            iv.setImageResource(R.drawable.close);
            iv.clearAnimation();
        } else {
            restoreText();
            panel.setVisibility(View.GONE);
            visible = false;
            panel.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.shape_norm));
            iv.setImageResource(R.drawable.refresh);
        }
    }

    public boolean checkTime(long time) {
        if (!stop || crash)
            return true;
        if (DateHelper.initNow(context).getTimeInSeconds() - time > DateHelper.DAY_IN_SEC) {
            this.time = true;
            panel.setVisibility(View.VISIBLE);
            visible = true;
            tv.setText(context.getResources().getString(R.string.refresh) + "?");
            return true;
        } else {
            this.time = false;
            visible = false;
            panel.setVisibility(View.GONE);
            restoreText();
        }
        return false;
    }

    public boolean isCrash() {
        return crash;
    }

    public void setText(String s) {
        tv.setText(s);
    }

    public void restoreText() {
        tv.setText(context.getResources().getString(R.string.load));
    }

    public void setClick(View.OnClickListener event) {
        panel.setOnClickListener(event);
    }

    public boolean onClick() {
        if (isCrash()) {
            Lib.showToast(context, context.getResources().getString(R.string.about_crash));
            setLoad(false);
            setCrash(false);
            return true;
        }
        return false;
    }

    public boolean isTime() {
        return time;
    }

    public boolean isStop() {
        return stop;
    }

    public boolean startMin() {
        if (visible)
            panel.startAnimation(anMin);
        return visible;
    }

    public boolean startMax() {
        if (visible) {
            panel.setVisibility(View.VISIBLE);
            panel.startAnimation(anMax);
        }
        return visible;
    }
}

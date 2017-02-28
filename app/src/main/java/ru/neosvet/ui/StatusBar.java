package ru.neosvet.ui;

import android.content.Context;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import ru.neosvet.blagayavest.R;
import ru.neosvet.utils.Lib;

/**
 * Created by NeoSvet on 25.12.2016.
 */

public class StatusBar {
    private Context cntxt;
    private Animation anStatus;
    private View panel;
    private TextView tv;
    private ImageView iv;
    private boolean crash = false, stop = false;

    public StatusBar(Context context, View p) {
        cntxt = context;
        this.panel = p;
        tv = (TextView) panel.findViewById(R.id.tvStatus);
        iv = (ImageView) panel.findViewById(R.id.ivStatus);

        anStatus = AnimationUtils.loadAnimation(context, R.anim.rotate);
        anStatus.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (!stop) //panel.getVisibility() == View.VISIBLE
                    iv.startAnimation(anStatus);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    public void setLoad(boolean boolStart) {
        stop = !boolStart;
        if (boolStart) {
            panel.setVisibility(View.VISIBLE);
            iv.startAnimation(anStatus);
        } else {
            panel.setVisibility(View.GONE);
            iv.clearAnimation();
        }
    }

    public void setCrash(boolean crash) {
        stop = true;
        this.crash = crash;
        if (crash) {
            tv.setText(cntxt.getResources().getString(R.string.crash));
            panel.setBackgroundDrawable(cntxt.getResources().getDrawable(R.drawable.shape_red));
            iv.setImageResource(R.drawable.close);
            iv.clearAnimation();
        } else {
            restoreText();
            panel.setBackgroundDrawable(cntxt.getResources().getDrawable(R.drawable.shape_norm));
            iv.setImageResource(R.drawable.refresh);
        }
    }

    public boolean isCrash() {
        return crash;
    }

    public void setText(String s) {
        tv.setText(s);
    }

    public void restoreText() {
        tv.setText(cntxt.getResources().getString(R.string.load));
    }

    public void setClick(View.OnClickListener event) {
        panel.setOnClickListener(event);
    }

    public boolean onClick() {
        if (isCrash()) {
            Lib.showToast(cntxt, cntxt.getResources().getString(R.string.about_crash));
            setLoad(false);
            setCrash(false);
            return true;
        }
        return false;
    }
}

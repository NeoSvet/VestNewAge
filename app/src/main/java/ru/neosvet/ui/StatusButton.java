package ru.neosvet.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;

public class StatusButton {
    private Context context;
    private Animation anRotate;
    private View panel;
    private TextView tv;
    private ImageView iv;
    private Animation anMin, anMax;
    private String error = null;
    private boolean stop = true, time = false, visible;

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
            error = null;
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

    public void setError(String error) {
        this.error = error;
        if (error != null) {
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
        if (!stop || isCrash())
            return true;
        if (DateHelper.initNow(context).getTimeInSeconds() - time > DateHelper.DAY_IN_SEC * 7) {
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

    private boolean isCrash() {
        return error != null;
    }

    public void setText(String s) {
        tv.setText(s);
    }

    private void restoreText() {
        tv.setText(context.getResources().getString(R.string.load));
    }

    public void setClick(View.OnClickListener event) {
        panel.setOnClickListener(event);
    }

    public boolean onClick() {
        if (isCrash()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.NeoDialog)
                    .setTitle(context.getResources().getString(R.string.error))
                    .setMessage(error)
                    .setPositiveButton(context.getResources().getString(android.R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {

                                }
                            });
            builder.create().show();
            setLoad(false);
            setError(null);
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

package ru.neosvet.ui;

import android.content.Context;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;

public class StatusButton {
    private final Context context;
    private final Animation anRotate;
    private Animation anMin;
    private Animation anMax;
    private final Animation anHide;
    private final View panel;
    private final TextView tv;
    private final ImageView iv;
    private final ProgressBar progBar;
    private ResizeAnim resizeMax = null;
    private String error = null;
    private boolean stop = true, time = false, visible, prog = false;

    public StatusButton(Context context, View p) {
        this.context = context;
        this.panel = p;
        tv = (TextView) panel.findViewById(R.id.tvStatus);
        iv = (ImageView) panel.findViewById(R.id.ivStatus);
        progBar = (ProgressBar) panel.findViewById(R.id.progStatus);

        anRotate = AnimationUtils.loadAnimation(context, R.anim.rotate);
        anRotate.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (!stop && visible)
                    iv.startAnimation(anRotate);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        anHide = AnimationUtils.loadAnimation(context, R.anim.hide);
        anHide.setAnimationListener(new Animation.AnimationListener() {
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
        setError(null);
        stop = !start;
        if (start) {
            loadText();
            time = false;
            panel.setVisibility(View.VISIBLE);
            visible = true;
            iv.startAnimation(anRotate);
        } else {
            if (prog) {
                prog = false;
                progBar.setProgress(0);
                progBar.setVisibility(View.GONE);
            }
            visible = false;
            iv.clearAnimation();
            tv.setText(context.getString(R.string.done));
            panel.startAnimation(anHide);
        }
    }

    public void setError(String error) {
        this.error = error;
        stop = true;
        if (error != null) {
            time = false;
            tv.setText(context.getString(R.string.crash));
            panel.setBackgroundResource(R.drawable.shape_red);
            iv.setImageResource(R.drawable.close);
            iv.clearAnimation();
            visible = true;
            panel.setVisibility(View.VISIBLE);
        } else {
            panel.setVisibility(View.GONE);
            visible = false;
            panel.setBackgroundResource(R.drawable.shape_norm);
            iv.setImageResource(R.drawable.refresh);
        }
    }

    public boolean checkTime(long time) {
        if (!stop || isCrash())
            return true;
        if (DateHelper.initNow(context).getTimeInSeconds() - time > DateHelper.DAY_IN_SEC * 7) {
            this.time = true;
            initResizeMax();
            visible = true;
            tv.setText(context.getString(R.string.refresh) + "?");
            return true;
        } else {
            this.time = false;
            visible = false;
            panel.setVisibility(View.GONE);
        }
        return false;
    }

    private void initResizeMax() {
        panel.setVisibility(View.VISIBLE);
        if (resizeMax == null) {
            resizeMax = new ResizeAnim(panel, true, (int) (210f * context.getResources().getDisplayMetrics().density));
            resizeMax.setStart(50);
            resizeMax.setDuration(800);
        }
        panel.clearAnimation();
        panel.startAnimation(resizeMax);
    }

    public boolean isCrash() {
        return error != null;
    }

    public void startText() {
        tv.setText(context.getString(R.string.start));
    }

    public void loadText() {
        tv.setText(context.getString(R.string.load));
    }

    public void setClick(View.OnClickListener event) {
        panel.setOnClickListener(event);
    }

    public boolean onClick() {
        if (isCrash()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.NeoDialog)
                    .setTitle(context.getString(R.string.error))
                    .setMessage(error)
                    .setPositiveButton(context.getString(R.string.send),
                            (dialog, id) -> sendError())
                    .setNegativeButton(context.getString(android.R.string.cancel),
                            (dialog, id) -> dialog.dismiss())
                    .setOnDismissListener(dialog -> ErrorUtils.clear());
            builder.create().show();
            ProgressHelper.setBusy(false);
            setLoad(false);
            setError(null);
            return true;
        }
        return false;
    }

    private void sendError() {
        Lib lib = new Lib(context);
        lib.openInApps(Const.mailto + ErrorUtils.getInformation(context), null);
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

    public void setProgress(int p) {
        if (!prog) {
            progBar.setVisibility(View.VISIBLE);
            prog = true;
        }
        progBar.setProgress(p);
    }
}

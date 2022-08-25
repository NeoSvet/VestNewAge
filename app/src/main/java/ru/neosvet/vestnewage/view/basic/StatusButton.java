package ru.neosvet.vestnewage.view.basic;

import android.content.Context;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.utils.Const;
import ru.neosvet.vestnewage.utils.ErrorUtils;
import ru.neosvet.vestnewage.utils.Lib;

public class StatusButton {
    private Context context;
    private Animation anRotate;
    private Animation anHide;
    private View panel;
    private TextView tv;
    private ImageView iv;
    private ProgressBar progBar;
    private String error = null;
    private boolean stop = true, visible, prog = false;

    public void init(Context context, View p) {
        this.context = context;
        this.panel = p;
        tv = panel.findViewById(R.id.tvStatus);
        iv = panel.findViewById(R.id.ivStatus);
        progBar = panel.findViewById(R.id.progStatus);

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
    }

    public boolean isVisible() {
        return visible;
    }

    public void setLoad(boolean start) {
        setError(null);
        stop = !start;
        if (prog) {
            prog = false;
            progBar.setProgress(0);
            progBar.setVisibility(View.GONE);
        }
        clearAnimation();
        if (start) {
            loadText();
            visible = true;
            iv.startAnimation(anRotate);
        } else {
            visible = false;
            tv.setText(context.getString(R.string.done));
            panel.startAnimation(anHide);
        }
    }

    public void setError(Throwable error) {
        stop = true;
        clearAnimation();
        if (error != null) {
            ErrorUtils.setError(error);
            this.error = ErrorUtils.getMessage();
            if (prog) progBar.setVisibility(View.GONE);
            tv.setText(context.getString(R.string.crash));
            panel.setBackgroundResource(R.drawable.shape_red);
            iv.setImageResource(R.drawable.ic_close);
            visible = true;
        } else {
            ErrorUtils.clear();
            this.error = null;
            panel.setVisibility(View.GONE);
            visible = false;
            panel.setBackgroundResource(R.drawable.shape_norm);
            iv.setImageResource(R.drawable.ic_refresh);
        }
    }

    public boolean isCrash() {
        return error != null;
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
                            (dialog, id) -> ErrorUtils.clear())
                    .setOnDismissListener(dialog -> ErrorUtils.clear());
            builder.create().show();
            setLoad(false);
            setError(null);
            return true;
        }
        return false;
    }

    private void sendError() {
        Lib.openInApps(Const.mailto + ErrorUtils.getInformation(), null);
        ErrorUtils.clear();
    }

    public void setProgress(int p) {
        if (!prog) {
            clearAnimation();
            progBar.setVisibility(View.VISIBLE);
            prog = true;
        }
        progBar.setProgress(p);
    }

    private void clearAnimation() {
        iv.clearAnimation();
        anHide.cancel();
        anRotate.cancel();
        panel.setVisibility(View.VISIBLE);
    }
}

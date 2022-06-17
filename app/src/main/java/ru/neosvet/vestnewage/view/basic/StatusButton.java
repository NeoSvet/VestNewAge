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
        if (start) {
            loadText();
            panel.setVisibility(View.VISIBLE);
            visible = true;
            iv.startAnimation(anRotate);
        } else {
            visible = false;
            iv.clearAnimation();
            tv.setText(context.getString(R.string.done));
            panel.startAnimation(anHide);
        }
    }

    public void setError(String error) {
        stop = true;
        if (error != null) {
            if (prog) progBar.setVisibility(View.GONE);
            error = parseError(error);
            tv.setText(context.getString(R.string.crash));
            panel.setBackgroundResource(R.drawable.shape_red);
            iv.setImageResource(R.drawable.ic_close);
            iv.clearAnimation();
            visible = true;
            panel.setVisibility(View.VISIBLE);
        } else {
            panel.setVisibility(View.GONE);
            visible = false;
            panel.setBackgroundResource(R.drawable.shape_norm);
            iv.setImageResource(R.drawable.ic_refresh);
        }
        this.error = error;
    }

    private String parseError(String error) {
        if (error.contains("failed to connect")) { //SocketTimeoutException
            int i = error.indexOf("connect") + 11;
            String site = error.substring(i, error.indexOf("/", i));
            i = error.indexOf("after") + 6;
            String sec = error.substring(i, i + 2);
            return String.format(context.getString(R.string.format_timeout), site, sec);
        }
        return error;
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
            progBar.setVisibility(View.VISIBLE);
            prog = true;
        }
        progBar.setProgress(p);
    }
}

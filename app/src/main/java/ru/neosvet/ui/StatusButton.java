package ru.neosvet.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;

public class StatusButton {
    private Context context;
    private Animation anRotate, anMin, anMax, anHide;
    private View panel;
    private TextView tv;
    private ImageView iv;
    private ProgressBar progBar;
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
                if (!stop) //panel.getVisibility() == View.VISIBLE
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
            tv.setText(context.getResources().getString(R.string.done));
            panel.startAnimation(anHide);
        }
    }

    public void setError(String error) {
        this.error = error;
        stop = true;
        if (error != null) {
            time = false;
            tv.setText(context.getResources().getString(R.string.crash));
            panel.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.shape_red));
            iv.setImageResource(R.drawable.close);
            iv.clearAnimation();
        } else {
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
            initResizeMax();
            visible = true;
            tv.setText(context.getResources().getString(R.string.refresh) + "?");
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

    private boolean isCrash() {
        return error != null;
    }

    public void startText() {
        tv.setText(context.getResources().getString(R.string.start));
    }

    public void loadText() {
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
                    .setPositiveButton(context.getResources().getString(R.string.send),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    sendError();
                                }
                            })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            Lib.setError(null);
                        }
                    });
            builder.create().show();
            ProgressHelper.setBusy(false);
            setLoad(false);
            setError(null);
            return true;
        }
        return false;
    }

    private void sendError() {
        StringBuilder des = new StringBuilder();
        try {
            des.append(context.getResources().getString(R.string.app_version));
            des.append(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
            des.append(" (");
            des.append(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
            des.append(")\n");
            des.append(context.getResources().getString(R.string.system_version));
            des.append(Build.VERSION.RELEASE);
            des.append(" (");
            des.append(Build.VERSION.SDK_INT);
            des.append(")");
        } catch (Exception e) {
            e.printStackTrace();
        }
        Lib lib = new Lib(context);
        lib.openInApps("mailto:neosvet333@gmail.com?subject=Приложение «Весть Нового Века»&body="
                + lib.getErrorDes() + "\n"
                + context.getResources().getString(R.string.srv_info)
                + des.toString(), null);
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

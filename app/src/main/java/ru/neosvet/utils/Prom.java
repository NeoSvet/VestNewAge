package ru.neosvet.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.R;

public class Prom {
    private Context context;
    private TextView tvPromTime = null;
    private Handler hTime = null;
    private Lib lib;

    public Prom(Context context) {
        this.context = context;
        if (context instanceof Activity) {
            Date today = getMoscowDate();
            int i = today.getDate();
            if (i == 4 || i == 17 || i == 26 || i == 30) {
            if (today.getHours() > 11)
                return;
                Activity act = (Activity) context;
                tvPromTime = (TextView) act.findViewById(R.id.tvPromTime);
                tvPromTime.setVisibility(View.VISIBLE);
                lib = new Lib(context);
                setViews();
                setPromTime();
            }
        } else
            lib = new Lib(context);
    }

    private void setViews() {
        if (context instanceof MainActivity) {
            final Animation anMin = AnimationUtils.loadAnimation(context, R.anim.minimize);
            anMin.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    tvPromTime.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            final Animation anMax = AnimationUtils.loadAnimation(context, R.anim.maximize);
            tvPromTime.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    tvPromTime.startAnimation(anMin);
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            hTime.sendEmptyMessage(1);
                        }
                    }, 2000);
                }
            });
            hTime = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message message) {
                    if (message.what == 0)
                        setPromTime();
                    else {
                        tvPromTime.setVisibility(View.VISIBLE);
                        tvPromTime.startAnimation(anMax);
                    }
                    return false;
                }
            });
        } else {
            hTime = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message message) {
                    setPromTime();
                    return false;
                }
            });
        }
    }

    public boolean isProm() {
        return tvPromTime != null;
    }

    private Date getMoscowDate() {
        Date d = new Date();
        // Moscow getTimezoneOffset == -180
        if (d.getTimezoneOffset() != -180) {
            d.setMinutes(d.getMinutes() +
                    d.getTimezoneOffset() + 180);
        }
        return d;
    }

    private void setPromTime() {
        String t = getPromText();
        if (t.contains("-")) {
            Animation an = AnimationUtils.loadAnimation(context, R.anim.hide);
            an.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    tvPromTime.setVisibility(View.GONE);
                    tvPromTime = null;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            tvPromTime.startAnimation(an);
            return;
        }
        tvPromTime.setText(t);
        if (t.equals(context.getResources().getString(R.string.prom))) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    hTime.sendEmptyMessage(0);
                }
            }, 3000);
            tvPromTime.startAnimation(AnimationUtils.loadAnimation(context, R.anim.blink));
        }
    }

    public String getPromText() {
        Date d = getMoscowDate();
        long now = d.getTime();
        d.setHours(11); // prom time
        d.setMinutes(0);
        d.setSeconds(0);
        String t = lib.getDiffDate(d.getTime(), now);
        if (t.contains("-"))
            return t;
        t = context.getResources().getString(R.string.to_prom)
                + " " + t.substring(0, t.length() - 6);
        d = new Date();
        for (int i = 0; i < context.getResources().getStringArray(R.array.time).length; i++) {
            if (t.contains(context.getResources().getStringArray(R.array.time)[i])) {
                if (i < 3) {
                    if (i == 0) {
                        if (!t.contains("1"))
                            return context.getResources().getString(R.string.prom);
                        else
                            t = t.replace(context.getResources().getStringArray(R.array.time)[i]
                                    , context.getResources().getString(R.string.seconde));
                    }
                    if (hTime != null)
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                hTime.sendEmptyMessage(0);
                            }
                        }, 1000);
                    break;
                } else if (i < 6) {
                    if (i == 3)
                        t = t.replace(context.getResources().getStringArray(R.array.time)[i]
                                , context.getResources().getString(R.string.minute));
                    d.setMinutes(d.getMinutes() + 1);
                    d.setSeconds(1);
                } else {
                    d.setHours(d.getHours() + 1);
                    d.setMinutes(0);
                    d.setSeconds(1);
                }
                if (hTime != null)
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            hTime.sendEmptyMessage(0);
                        }
                    }, d);
                break;
            }
        }
        return t;
    }

}

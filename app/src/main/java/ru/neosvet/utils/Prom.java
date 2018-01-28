package ru.neosvet.utils;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.activity.SlashActivity;
import ru.neosvet.vestnewage.fragment.SettingsFragment;
import ru.neosvet.vestnewage.receiver.PromReceiver;

public class Prom {
    public static final int notif_id = 333, hour_prom = 11;
    private static final String CORRECT = "correct", TIMECHECK = "timecheck";
    private Context context;
    private TextView tvPromTime = null;
    private Handler hTime = null;
    private Timer timer = null;
    private Lib lib;
    private SharedPreferences pref;

    public Prom(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(this.getClass().getSimpleName(), context.MODE_PRIVATE);
        lib = new Lib(context);
        if (context instanceof SlashActivity)
            return;
        if (context instanceof Activity) {
            Date today = getMoscowDate();
            if (today.getHours() >= hour_prom) // today prom was been
                return;
            Activity act = (Activity) context;
            tvPromTime = (TextView) act.findViewById(R.id.tvPromTime);
            tvPromTime.setVisibility(View.VISIBLE);
            setViews();
        }
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
        }
    }

    public void resume() {
        if (isProm()) {
            setPromTime();
        }
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
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
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
            timer = new Timer();
            timer.schedule(new TimerTask() {
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
        d.setHours(hour_prom); // prom time
        d.setMinutes(0);
        d.setSeconds(pref.getInt(CORRECT, 0));
        String t = lib.getDiffDate(d.getTime(), now);
        if (t.contains("-"))
            return t;
        t = context.getResources().getString(R.string.to_prom) + " " + t;
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
                    if (hTime != null) {
                        timer = new Timer();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                hTime.sendEmptyMessage(0);
                            }
                        }, 1000);
                    }
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
                if (hTime != null) {
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            hTime.sendEmptyMessage(0);
                        }
                    }, d);
                }
                break;
            }
        }
        return t;
    }

    public void synchronTime() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long timecheck = pref.getLong(TIMECHECK, 0);
                    if (System.currentTimeMillis() - timecheck < 86400000)
                        return;
                    Date d = getMoscowDate();
                    long now = d.getTime();
                    d.setHours(hour_prom); // prom time
                    d.setMinutes(0);
                    d.setSeconds(0);
                    int timeleft1 = (int) ((d.getTime() - now) / 1000);
                    if (timeleft1 < 0)
                        return;
                    InputStream in = new BufferedInputStream(lib.getStream(Const.SITE));
                    BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.contains("timeleft")) {
                            line = br.readLine();
                            break;
                        }
                    }
                    br.close();
                    if (line != null) {
                        int timeleft2 = Integer.parseInt(line.substring(line.indexOf("=") + 1, line.indexOf(";")));
                        int correct = timeleft2 - timeleft1;
                        if (correct != pref.getInt(CORRECT, 0))
                            PromReceiver.setReceiver(context,
                                    pref.getInt(SettingsFragment.TIME, -1), false);
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putInt(CORRECT, correct);
                        if (Math.abs(correct) > 60) {
                            showNotifDiffDate(d, correct);
                        } else {
                            editor.putLong(TIMECHECK, System.currentTimeMillis());
                        }
                        editor.apply();
                    }
                } catch (Exception e) {
                }
            }
        }).start();
    }

    private void showNotifDiffDate(Date d, int correct) {
        PendingIntent piSettings = PendingIntent.getActivity(context, 0,
                new Intent(Settings.ACTION_DATE_SETTINGS), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Date d1 = (Date) d.clone();
        d1.setSeconds(correct);
        StringBuilder title = new StringBuilder(context.getResources().getString(R.string.your_clock));
        if (correct > 0) {
            title.append(context.getResources().getString(R.string.time_more));
            title.append(lib.getDiffDate(d1.getTime(), d.getTime()));
        } else {
            title.append(context.getResources().getString(R.string.time_less));
            title.append(lib.getDiffDate(d.getTime(), d1.getTime()));
        }
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.star)
                .setContentTitle(title.toString())
                .setContentText(context.getResources().getString(R.string.correct_time))
                .setTicker(context.getResources().getString(R.string.correct_time))
                .setWhen(System.currentTimeMillis())
                .setFullScreenIntent(piEmpty, false)
                .setContentIntent(piSettings)
                .setAutoCancel(true);
        nm.notify(notif_id, mBuilder.build());
    }

}

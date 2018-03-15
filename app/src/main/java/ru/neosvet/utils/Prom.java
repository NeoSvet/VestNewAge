package ru.neosvet.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
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
    private static final String TIMEPROM = "timeprom";
    private Context context;
    private TextView tvPromTime = null;
    private Handler hTime = null;
    private Timer timer = null;
    private Lib lib;
    private SharedPreferences pref;

    public Prom(Context context, View textView) {
        this.context = context;
        pref = context.getSharedPreferences(this.getClass().getSimpleName(), context.MODE_PRIVATE);
        lib = new Lib(context);
        if (textView != null) {
//            long t = getPromDate().getTime() - System.currentTimeMillis();
//            if (t > 39600000) // today prom was been (>11 hours)
//                return;
            tvPromTime = (TextView) textView;
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

    public void hide() {
        stop();
        tvPromTime.setVisibility(View.GONE);
    }

    public void show() {
        if (isProm()) {
            setPromTime();
            tvPromTime.setVisibility(View.VISIBLE);
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

    public Date getPromDate() {
        long timeprom = pref.getLong(TIMEPROM, 0);
        long now;
        if (timeprom > 0) {
            now = System.currentTimeMillis();
            while (timeprom < now)
                timeprom += 86400000; //+24 hours
        } else {
            Date d = getMoscowDate();
            now = d.getTime();
            d.setHours(hour_prom); // prom time
            d.setMinutes(0);
            d.setSeconds(0);
            timeprom = d.getTime();
            if (timeprom < now)
                timeprom += 86400000; //+24 hours
            timeprom = System.currentTimeMillis() + timeprom - now;
        }
        return new Date(timeprom);
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
        String t = lib.getDiffDate(getPromDate().getTime(), System.currentTimeMillis());
        if (t.contains("-"))
            return t;
        t = context.getResources().getString(R.string.to_prom) + " " + t;
        Date d = new Date();
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

    public void synchronTime(final Handler action) {
        if (action == null) {
            if (pref.getLong(TIMEPROM, 0) > 0)
                return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
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
                        long timeprom = System.currentTimeMillis();
                        timeprom -= timeprom % 1000;
                        int timeleft = Integer.parseInt(line.substring(line.indexOf("=") + 1, line.indexOf(";")));
                        timeprom += timeleft * 1000;
                        if (timeprom != pref.getLong(TIMEPROM, 0)) {
                            int t = pref.getInt(SettingsFragment.TIME, -1);
                            if (t > -1)
                                PromReceiver.setReceiver(context, t);
                            SharedPreferences.Editor editor = pref.edit();
                            editor.putLong(TIMEPROM, timeprom);
                            editor.apply();
                        }
                        if (action != null)
                            action.sendEmptyMessage(timeleft);
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (action != null)
                    action.sendEmptyMessage(-1);
            }
        }).start();
    }

    public void showNotif() {
        SharedPreferences pref = context.getSharedPreferences(SettingsFragment.PROM, context.MODE_PRIVATE);
        final int p = pref.getInt(SettingsFragment.TIME, -1);
        if (p == -1)
            return;
        boolean boolSound = pref.getBoolean(SettingsFragment.SOUND, false);
        boolean boolVibr = pref.getBoolean(SettingsFragment.VIBR, true);
        Intent intent = new Intent(context, SlashActivity.class);
        intent.setData(Uri.parse(Const.SITE + "Posyl-na-Edinenie.html"));
        PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piProm = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String msg = getPromText();
        if (msg.contains("-")) {
            msg = context.getResources().getString(R.string.prom);
        }
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.star)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(context.getResources().getString(R.string.prom_for_soul_unite))
                .setContentText(msg)
                .setTicker(msg)
                .setWhen(System.currentTimeMillis() + 3000)
                .setFullScreenIntent(piEmpty, true)
                .setContentIntent(piProm)
                .setLights(Color.GREEN, 1000, 1000)
                .setAutoCancel(true);
        if (boolSound)
            mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        if (boolVibr)
            mBuilder.setVibrate(new long[]{500, 1500});
        nm.notify(notif_id, mBuilder.build());
        PromReceiver.setReceiver(context, p);
    }
}

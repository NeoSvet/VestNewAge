package ru.neosvet.vestnewage.utils;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Timer;
import java.util.TimerTask;

import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.data.DateUnit;
import ru.neosvet.vestnewage.network.NeoClient;
import ru.neosvet.vestnewage.view.activity.MainActivity;
import ru.neosvet.vestnewage.view.dialog.SetNotifDialog;

public class PromUtils {
    public static final String TAG = "Prom";
    private static final byte SET_PROM_TEXT = 0, START_ANIM = 1;
    private TextView tvPromTime = null;
    private Handler hTime = null;
    private Timer timer = null;
    private final SharedPreferences pref;
    private final int FLAGS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ?
            PendingIntent.FLAG_UPDATE_CURRENT :
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;

    public PromUtils(@Nullable View textView) {
        pref = App.context.getSharedPreferences(PromUtils.TAG, Context.MODE_PRIVATE);
        if (textView != null) {
            tvPromTime = (TextView) textView;
            tvPromTime.setVisibility(View.VISIBLE);
            setViews();
        }
    }

    public void stop() {
        if (timer != null)
            timer.cancel();
    }

    public void resume() {
        if (isProm())
            setPromTime();
    }

    public void hide() {
        stop();
        if (tvPromTime != null)
            tvPromTime.setVisibility(View.GONE);
    }

    public void show() {
        if (isProm()) {
            setPromTime();
            if (tvPromTime != null)
                tvPromTime.setVisibility(View.VISIBLE);
        }
    }

    private void setViews() {
        if (tvPromTime.getId() == R.id.tvPromTimeFloat) {
            final Animation anMin = AnimationUtils.loadAnimation(App.context, R.anim.minimize);
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
            final Animation anMax = AnimationUtils.loadAnimation(App.context, R.anim.maximize);
            tvPromTime.setOnClickListener(view -> {
                tvPromTime.startAnimation(anMin);
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        hTime.sendEmptyMessage(START_ANIM);
                    }
                }, 2 * DateUnit.SEC_IN_MILLS);
            });
            hTime = new Handler(message -> {
                if (message.what == SET_PROM_TEXT) {
                    setPromTime();
                } else { //START_ANIM
                    tvPromTime.setVisibility(View.VISIBLE);
                    tvPromTime.startAnimation(anMax);
                }
                return false;
            });
        } else { //R.id.tvPromTimeInMenu
            hTime = new Handler(message -> {
                setPromTime();
                return false;
            });
        }
    }

    public boolean isProm() {
        return tvPromTime != null;
    }

    private DateUnit getPromDate(boolean next) {
        int timeDiff = pref.getInt(Const.TIMEDIFF, 0);
        DateUnit prom = DateUnit.initNow();
        int hour = 8;
        if (next) {
            while (prom.getHours() > hour)
                hour += 8;
            if (hour == 24) {
                prom.changeDay(1);
                prom.setHours(0);
            } else
                prom.setHours(hour);
            hour = 8;
        }
        while (prom.getHours() >= hour)
            hour += 8;
        if (hour == 24) {
            prom.changeDay(1);
            prom.setHours(0);
        } else
            prom.setHours(hour);
        prom.setMinutes(0);
        prom.setSeconds(0);
        prom.changeSeconds(-timeDiff);
        return prom;
    }

    private void setPromTime() {
        String t = getPromText();
        if (t == null) { //t.contains("-")
            tvPromTime.setText(App.context.getString(R.string.prom));
            Animation an = AnimationUtils.loadAnimation(App.context, R.anim.hide);
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
            an.setDuration(DateUnit.SEC_IN_MILLS);
            tvPromTime.startAnimation(an);
            return;
        }
        tvPromTime.setText(t);
        if (tvPromTime.getId() == R.id.tvPromTimeFloat &&
                t.contains(App.context.getResources().getStringArray(R.array.time)[6])) {
            t = t.substring(App.context.getString(R.string.to_prom).length() + 1);
            int h = 0;
            if (t.contains(","))
                t = t.substring(0, t.indexOf(","));
            else if (t.contains("."))
                t = t.substring(0, t.indexOf("."));
            else if (t.contains(" "))
                t = t.substring(0, t.indexOf(" "));
            else
                h = 1;
            if (h == 0)
                h = Integer.parseInt(t);

            if (h > 2) {
                tvPromTime.setVisibility(View.GONE);
                return;
            }
            tvPromTime.setVisibility(View.VISIBLE);
        }
        if (t.equals(App.context.getString(R.string.prom))) {
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    hTime.sendEmptyMessage(SET_PROM_TEXT);
                }
            }, 3 * DateUnit.SEC_IN_MILLS);
            tvPromTime.startAnimation(AnimationUtils.loadAnimation(App.context, R.anim.blink));
        }
    }

    private String getPromText() {
        DateUnit prom = getPromDate(false);
        String t = prom.getDiffDate(System.currentTimeMillis());
        if (t.contains("-") || // prom was been
                t.equals(App.context.getResources().getStringArray(R.array.time)[0])) //second
            return null;
        t = App.context.getString(R.string.to_prom) + " " + t;
        int delay;
        if (t.contains(App.context.getString(R.string.sec)))
            delay = DateUnit.SEC_IN_MILLS; // 1 sec
        else if (t.contains(App.context.getString(R.string.min))) {
            t = t.replace(App.context.getResources().getStringArray(R.array.time)[3],
                    App.context.getString(R.string.minute));
            delay = 6 * DateUnit.SEC_IN_MILLS; // 1/10 of min in sec
        } else
            delay = 360 * DateUnit.SEC_IN_MILLS; // 1/10 of hour in sec
        if (hTime != null) {
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    hTime.sendEmptyMessage(SET_PROM_TEXT);
                }
            }, delay);
        }
        return t;
    }

    public void showNotif() {
        final int p = pref.getInt(Const.TIME, Const.TURN_OFF);
        if (p == Const.TURN_OFF)
            return;
        boolean sound = pref.getBoolean(SetNotifDialog.SOUND, false);
        boolean vibration = pref.getBoolean(SetNotifDialog.VIBR, true);
        Intent intent = new Intent(App.context, MainActivity.class);
        intent.setData(Uri.parse(NeoClient.SITE + "Posyl-na-Edinenie.html"));
        PendingIntent piEmpty = PendingIntent.getActivity(App.context, 0, new Intent(), FLAGS);
        PendingIntent piProm = PendingIntent.getActivity(App.context, 0, intent, FLAGS);
        NotificationUtils notifHelper = new NotificationUtils();
        String msg = getPromText();
        if (msg == null) //text.contains("-")
            msg = App.context.getString(R.string.prom);
        PendingIntent piCancel = notifHelper.getCancelPromNotif();

        NotificationCompat.Builder notifBuilder = notifHelper.getNotification(
                App.context.getString(R.string.prom_for_soul_unite),
                msg, NotificationUtils.CHANNEL_PROM);
        notifBuilder.setContentIntent(piProm)
                .setFullScreenIntent(piEmpty, true)
                .addAction(0, App.context.getString(R.string.accept), piCancel)
                .setLights(Color.GREEN, DateUnit.SEC_IN_MILLS, DateUnit.SEC_IN_MILLS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (p == 0)
                notifBuilder.setTimeoutAfter(30 * DateUnit.SEC_IN_MILLS);
            else
                notifBuilder.setTimeoutAfter((long) p * 60 * DateUnit.SEC_IN_MILLS);
        } else {
            if (sound) {
                String uri = pref.getString(SetNotifDialog.URI, null);
                if (uri == null)
                    notifBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                else
                    notifBuilder.setSound(Uri.parse(uri));
            }
            if (vibration)
                notifBuilder.setVibrate(new long[]{500, 1500});
        }
        notifHelper.notify(NotificationUtils.NOTIF_PROM, notifBuilder);
        initNotif(p);
    }

    public void initNotif(int p) {
        Intent intent = new Intent(App.context, Rec.class);
        PendingIntent piProm = PendingIntent.getBroadcast(App.context, 2, intent, FLAGS);
        if (p == Const.TURN_OFF) {
            NotificationUtils.setAlarm(piProm, p);
            return;
        }
        PromUtils prom = new PromUtils(null);
        DateUnit d = prom.getPromDate(false);
        p++;
        d.changeMinutes(-p);
        if (d.getTimeInSeconds() < DateUnit.initNow().getTimeInSeconds()) {
            d = prom.getPromDate(true);
            d.changeMinutes(-p);
        }
        NotificationUtils.setAlarm(piProm, d.getTimeInMills());
    }

    public static class Rec extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            PromUtils prom = new PromUtils(null);
            prom.showNotif();
        }
    }
}

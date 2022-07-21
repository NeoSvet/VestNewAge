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
import ru.neosvet.vestnewage.view.activity.BrowserActivity;
import ru.neosvet.vestnewage.view.activity.MainActivity;
import ru.neosvet.vestnewage.view.basic.BottomAnim;
import ru.neosvet.vestnewage.view.dialog.SetNotifDialog;

public class PromUtils {
    public static final String TAG = "Prom";
    private TextView tvPromTime = null;
    private int period = 0;
    private boolean isStart = false;
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
        isStart = false;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void resume() {
        if (isPromField() && !isStart)
            startPromTime();
    }

    public void hide() {
        stop();
        if (isPromField()) {
            tvPromTime.setText("");
            tvPromTime.setVisibility(View.GONE);
        }
    }

    public void show() {
        if (isPromField() && !isStart) {
            startPromTime();
            tvPromTime.setVisibility(View.VISIBLE);
        }
    }

    private void setViews() {
        if (tvPromTime.getId() == R.id.tvPromTimeFloat) {
            final BottomAnim anim = new BottomAnim(tvPromTime);
            tvPromTime.setOnClickListener(view -> {
                anim.hide();
                new Thread(() -> {
                    try {
                        Thread.sleep(2 * DateUnit.SEC_IN_MILLS);
                    } catch (InterruptedException ignored) {
                    }
                    tvPromTime.post(anim::show);
                }).start();
            });
        } else { //R.id.tvPromTimeInMenu
            tvPromTime.setOnClickListener(view -> {
                BrowserActivity.openReader("Vremya-Posyla.html", null);
            });
        }
    }

    private boolean isPromField() {
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

    private void startPromTime() {
        new Thread(this::setPromTime).start();
    }

    private void setPromTime() {
        isStart = true;
        String t = getPromText();
        if (t == null) { //t.contains("-")
            setTimeText(App.context.getString(R.string.prom));
            hideTimeText();
            restartPromTime();
            return;
        }
        setTimeText(t);
        if (tvPromTime.getId() == R.id.tvPromTimeFloat &&
                t.contains(App.context.getResources().getStringArray(R.array.time)[6])) {
            int n = App.context.getString(R.string.to_prom).length() + 1;
            n = Integer.parseInt(t.substring(n, n + 1));
            setTimeVisible(n < 3);
        }
    }

    private void restartPromTime() {
        new Thread(() -> {
            try {
                Thread.sleep(5 * DateUnit.SEC_IN_MILLS);
            } catch (InterruptedException ignored) {
            }
            setPromTime();
        }).start();
    }

    private void setTimeVisible(boolean isVisible) {
        tvPromTime.post(() -> {
            if (isVisible)
                tvPromTime.setVisibility(View.VISIBLE);
            else
                tvPromTime.setVisibility(View.GONE);
        });
    }

    private void hideTimeText() {
        stop();
        tvPromTime.post(() -> {
            Animation an = AnimationUtils.loadAnimation(App.context, R.anim.hide);
            an.setAnimationListener(new Animation.AnimationListener() {
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
            an.setDuration(2 * DateUnit.SEC_IN_MILLS);
            tvPromTime.startAnimation(an);
        });
    }

    private void setTimeText(String t) {
        tvPromTime.post(() -> tvPromTime.setText(t));
    }

    private String getPromText() {
        DateUnit prom = getPromDate(false);
        long p = prom.getTimeInMills();
        long now = System.currentTimeMillis();
        int n = (int) (p - now);
        if (n < DateUnit.SEC_IN_MILLS)
            return null;
        String t = DateUnit.getDiffDate(p, System.currentTimeMillis());
        t = App.context.getString(R.string.to_prom) + " " + t;
        if (!isPromField())
            return t;
        //period for timer:
        int d;
        if (t.contains(App.context.getString(R.string.sec)))
            d = DateUnit.SEC_IN_MILLS; // 1 sec
        else if (t.contains(App.context.getString(R.string.min))) {
            t = t.replace(App.context.getResources().getStringArray(R.array.time)[3],
                    App.context.getString(R.string.minute));
            d = 6 * DateUnit.SEC_IN_MILLS; // 1/10 of min in sec
        } else
            d = 360 * DateUnit.SEC_IN_MILLS; // 1/10 of hour in sec

        if (d != period) {
            n = n % d; //delay for timer
            //restart timer:
            stop();
            period = d;
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    setPromTime();
                }
            }, n, d);
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
        intent.setData(Uri.parse(NeoClient.SITE + Const.PROM_LINK));
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

package ru.neosvet.vestnewage.helpers;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.neosvet.ui.dialogs.SetNotifDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.activity.SlashActivity;
import ru.neosvet.vestnewage.receiver.PromReceiver;

public class PromHelper {
    public static final byte ERROR = -1;
    private static final byte SET_PROM_TEXT = 0, START_ANIM = 1;
    private final String TIMEDIFF = "timediff";
    private Context context;
    private TextView tvPromTime = null;
    private Handler hTime = null;
    private Timer timer = null;
    private SharedPreferences pref;

    public PromHelper(Context context, @Nullable View textView) {
        this.context = context;
        pref = context.getSharedPreferences(this.getClass().getSimpleName(), Context.MODE_PRIVATE);
        if (textView != null) {
            tvPromTime = (TextView) textView;
            tvPromTime.setVisibility(View.VISIBLE);
            setViews();
        }
    }

    private int timeToProm() {
        DateHelper prom = getPromDate(false);
        DateHelper now = DateHelper.initNow(context);
        return (int) (prom.getTimeInSeconds() - now.getTimeInSeconds());
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
                            hTime.sendEmptyMessage(START_ANIM);
                        }
                    }, 2 * DateHelper.SEC_IN_MILLS);
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

    public DateHelper getPromDate(boolean next) {
        int timeDiff = pref.getInt(TIMEDIFF, 0);
        DateHelper prom = DateHelper.initNow(context);
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
                    hTime.sendEmptyMessage(SET_PROM_TEXT);
                }
            }, 3 * DateHelper.SEC_IN_MILLS);
            tvPromTime.startAnimation(AnimationUtils.loadAnimation(context, R.anim.blink));
        }
    }

    private String getPromText() {
        DateHelper prom = getPromDate(false);
        String t = prom.getDiffDate(System.currentTimeMillis());
        if (t.contains("-") || // prom was been
                t.contains(context.getResources().getStringArray(R.array.time)[0])) //second
            return null;
        t = context.getResources().getString(R.string.to_prom) + " " + t;
        int delay;
        if (t.contains(context.getResources().getString(R.string.sec)))
            delay = DateHelper.SEC_IN_MILLS; // 1 sec
        else if (t.contains(context.getResources().getString(R.string.min)))
            delay = 6 * DateHelper.SEC_IN_MILLS; // 1/10 of min in sec
        else
            delay = 360 * DateHelper.SEC_IN_MILLS; // 1/10 of hour in sec
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

    public void synchronTime(@Nullable final Handler action) {
        if (action == null) {
            int time = pref.getInt(TIMEDIFF, 0);
            if (time > 0 && time < 1000)
                return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Request.Builder builderRequest = new Request.Builder();
                    builderRequest.url(Const.SITE2);
                    builderRequest.header(Const.USER_AGENT, context.getPackageName());
                    OkHttpClient client = Lib.createHttpClient();
                    Response response = client.newCall(builderRequest.build()).execute();
                    String s = response.headers().value(1);
                    long timeServer = DateHelper.parse(context, s).getTimeInSeconds();
                    response.close();
                    long timeDevice = DateHelper.initNow(context).getTimeInSeconds();
                    int timeDiff = (int) (timeDevice - timeServer);
                    if (timeDiff != pref.getInt(TIMEDIFF, 0)) {
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putInt(TIMEDIFF, timeDiff);
                        editor.apply();
                        int t = pref.getInt(Const.TIME, Const.TURN_OFF);
                        if (t != Const.TURN_OFF)
                            PromReceiver.setReceiver(context, t);
                    }
                    if (action != null)
                        action.sendEmptyMessage(timeToProm());
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (action != null)
                    action.sendEmptyMessage(ERROR);
            }
        }).start();
    }

    public void showNotif() {
        SharedPreferences pref = context.getSharedPreferences(Const.PROM, Context.MODE_PRIVATE);
        final int p = pref.getInt(Const.TIME, Const.TURN_OFF);
        if (p == Const.TURN_OFF)
            return;
        boolean sound = pref.getBoolean(SetNotifDialog.SOUND, false);
        boolean vibration = pref.getBoolean(SetNotifDialog.VIBR, true);
        Intent intent = new Intent(context, SlashActivity.class);
        intent.setData(Uri.parse(Const.SITE + "Posyl-na-Edinenie.html"));
        PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piProm = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationHelper notifHelper = new NotificationHelper(context);
        String msg = getPromText();
        if (msg == null) //msg.contains("-")
            msg = context.getResources().getString(R.string.prom);
        PendingIntent piCancel = notifHelper.getCancelPromNotif();

        NotificationCompat.Builder notifBuilder = notifHelper.getNotification(
                context.getResources().getString(R.string.prom_for_soul_unite),
                msg, NotificationHelper.CHANNEL_PROM);
        notifBuilder.setContentIntent(piProm)
                .setFullScreenIntent(piEmpty, true)
                .addAction(0, context.getResources().getString(R.string.accept), piCancel)
                .setLights(Color.GREEN, DateHelper.SEC_IN_MILLS, DateHelper.SEC_IN_MILLS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (p == 0)
                notifBuilder.setTimeoutAfter(30 * DateHelper.SEC_IN_MILLS);
            else
                notifBuilder.setTimeoutAfter(p * 60 * DateHelper.SEC_IN_MILLS);
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
        notifHelper.notify(NotificationHelper.NOTIF_PROM, notifBuilder);
        PromReceiver.setReceiver(context, p);
    }

    public void clearTimeDiff() {
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(TIMEDIFF, 0);
        editor.apply();
    }
}

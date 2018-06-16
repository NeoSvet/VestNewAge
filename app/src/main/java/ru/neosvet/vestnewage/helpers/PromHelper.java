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
import ru.neosvet.vestnewage.fragment.SettingsFragment;
import ru.neosvet.vestnewage.receiver.PromReceiver;

public class PromHelper {
    private static final int hour_prom1 = 8, hour_prom2 = 11;
    private final String TIMEDIFF = "timediff";
    private Context context;
    private TextView tvPromTime = null;
    private Handler hTime = null;
    private Timer timer = null;
    private Lib lib;
    private SharedPreferences pref;

    public PromHelper(Context context, @Nullable View textView) {
        this.context = context;
        pref = context.getSharedPreferences(this.getClass().getSimpleName(), Context.MODE_PRIVATE);
        lib = new Lib(context);
        if (textView != null) {
//            if (timeToProm() > 39600) //11 hours in sec, today prom was been
//                return;
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
                            hTime.sendEmptyMessage(1);
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
        DateHelper prom = DateHelper.initNow(context);;
        prom.setMinutes(0);
        prom.setSeconds(0);
        prom.minusSeconds(timeDiff);
        if (next) {
            if (prom.getHours() < hour_prom1)
                prom.setHours(hour_prom1);
            else if (prom.getHours() < hour_prom2)
                prom.setHours(hour_prom2);
        }
        if (prom.getHours() >= hour_prom1) {
            if (prom.getHours() >= hour_prom2) {
                prom.plusDay(1);
                prom.setHours(hour_prom1);
            } else
                prom.setHours(hour_prom2);
        } else
            prom.setHours(hour_prom1);
        return prom;
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
            }, 3 * DateHelper.SEC_IN_MILLS);
            tvPromTime.startAnimation(AnimationUtils.loadAnimation(context, R.anim.blink));
        }
    }

    private String getPromText() {
        DateHelper prom = getPromDate(false);
        String t = prom.getDiffDate(System.currentTimeMillis());
        if (t.contains("-"))
            return t;
        t = context.getResources().getString(R.string.to_prom) + " " + t;
        DateHelper d = DateHelper.initNow(context);;
        for (int i = 0; i < context.getResources().getStringArray(R.array.time).length; i++) {
            if (t.contains(context.getResources().getStringArray(R.array.time)[i])) {
                if (i < 3) { // less minutes
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
                        }, DateHelper.SEC_IN_MILLS);
                    }
                    break;
                } else if (i < 6) { // less hour
                    if (i == 3)
                        t = t.replace(context.getResources().getStringArray(R.array.time)[i]
                                , context.getResources().getString(R.string.minute));
                    d.plusMinutes(1);
                    d.setSeconds(1);
                } else {
                    d.plusHours(1);
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
                    }, d.getTimeInMills());
                }
                break;
            }
        }
        return t;
    }

    public void synchronTime(@Nullable final Handler action) {
        if (action == null) {
            if (pref.getInt(TIMEDIFF, 0) > 0)
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
                        int t = pref.getInt(SettingsFragment.TIME, -1);
                        if (t > -1)
                            PromReceiver.setReceiver(context, t);
                    }
                    if (action != null)
                        action.sendEmptyMessage(timeToProm());
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (action != null)
                    action.sendEmptyMessage(-1);
            }
        }).start();
    }

    public void showNotif() {
        SharedPreferences pref = context.getSharedPreferences(SettingsFragment.PROM, Context.MODE_PRIVATE);
        final int p = pref.getInt(SettingsFragment.TIME, -1);
        if (p == -1)
            return;
        boolean sound = pref.getBoolean(SetNotifDialog.SOUND, false);
        boolean vibration = pref.getBoolean(SetNotifDialog.VIBR, true);
        Intent intent = new Intent(context, SlashActivity.class);
        intent.setData(Uri.parse(Const.SITE + "Posyl-na-Edinenie.html"));
        PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piProm = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationHelper notifHelper = new NotificationHelper(context);
        String msg = getPromText();
        if (msg.contains("-")) {
            msg = context.getResources().getString(R.string.prom);
        }
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
}

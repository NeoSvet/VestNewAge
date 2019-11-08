package ru.neosvet.vestnewage.helpers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;

import ru.neosvet.ui.dialogs.SetNotifDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.SlashActivity;
import ru.neosvet.vestnewage.fragment.SummaryFragment;
import ru.neosvet.vestnewage.receiver.SummaryReceiver;
import ru.neosvet.vestnewage.service.SummaryService;

/**
 * Created by NeoSvet on 11.06.2018.
 */
public class SummaryHelper {
    private static final int TEN_MIN_IN_MILLS = 600000;
    private Context context;
    private NotificationHelper notifHelper;
    private Intent intent;
    private PendingIntent piEmpty;
    private int notif_id = NotificationHelper.NOTIF_SUMMARY;
    private NotificationCompat.Builder notifBuilder;


    public SummaryHelper(Context context) {
        this.context = context;
        notifHelper = new NotificationHelper(context);
        intent = new Intent(context, SlashActivity.class);
        piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void createNotification(String text, String link) {
        if (!link.contains("://"))
            link = Const.SITE + link;
        intent.setData(Uri.parse(link));
        PendingIntent piSummary = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piPostpone = notifHelper.getPostponeSummaryNotif(notif_id, text, link);
        notifBuilder = notifHelper.getNotification(
                context.getResources().getString(R.string.site_name), text,
                NotificationHelper.CHANNEL_SUMMARY);
        notifBuilder.setContentIntent(piSummary)
                .setGroup(NotificationHelper.GROUP_SUMMARY)
                .addAction(0, context.getResources().getString(R.string.postpone), piPostpone);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            notifBuilder.setFullScreenIntent(piEmpty, true);
    }

    public boolean isNotification() {
        return notifBuilder != null;
    }

    public void muteNotification() {
        notifBuilder.setChannelId(NotificationHelper.CHANNEL_MUTE);
    }

    public void showNotification() {
        notifHelper.notify(notif_id, notifBuilder);
        notif_id++;
    }

    public void groupNotification() {
        notifBuilder = notifHelper.getSummaryNotif(
                context.getResources().getString(R.string.appeared_new_some),
                NotificationHelper.CHANNEL_SUMMARY);
        intent.setData(Uri.parse(Const.SITE + Const.RSS));
        intent.putExtra(DataBase.ID, notif_id);
        PendingIntent piSummary = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        notifBuilder.setContentIntent(piSummary)
                .setGroup(NotificationHelper.GROUP_SUMMARY);
        notifBuilder.setFullScreenIntent(piEmpty, true);
    }

    public void singleNotification(String text) {
        notifBuilder.setContentText(context.getResources().getString(R.string.appeared_new) + text);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            notifBuilder.setFullScreenIntent(piEmpty, true);
    }

    public void setPreferences(SharedPreferences pref) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            boolean sound = pref.getBoolean(SetNotifDialog.SOUND, false);
            boolean vibration = pref.getBoolean(SetNotifDialog.VIBR, true);
            notifBuilder.setLights(Color.GREEN, DateHelper.SEC_IN_MILLS, DateHelper.SEC_IN_MILLS);
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
    }

    public void serviceFinish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent finish = new Intent(InitJobService.ACTION_FINISHED);
            context.sendBroadcast(finish);
        }
    }

    public static void setReceiver(Context context, int p) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ComponentName jobService = new ComponentName(context, InitJobService.class);
            JobInfo.Builder exerciseJobBuilder = new JobInfo.Builder(NotificationHelper.ID_SUMMARY, jobService);
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            jobScheduler.cancel(NotificationHelper.ID_SUMMARY);
            if (p != Const.TURN_OFF) {
                p = (p + 1) * TEN_MIN_IN_MILLS;
                exerciseJobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
                exerciseJobBuilder.setPersisted(true); // save job after reboot
                exerciseJobBuilder.setRequiresDeviceIdle(false); // anyway: use device or not use
                exerciseJobBuilder.setRequiresCharging(false); // anyway: charging device or not charging
                exerciseJobBuilder.setPeriodic(p); // periodic for retry
                jobScheduler.schedule(exerciseJobBuilder.build());
            }
        } else {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, SummaryReceiver.class);
            PendingIntent piCheck = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            am.cancel(piCheck);
            if (p != Const.TURN_OFF) {
                p = (p + 1) * TEN_MIN_IN_MILLS;
                am.set(AlarmManager.RTC_WAKEUP, p + System.currentTimeMillis(), piCheck);
            }
        }
    }

    public static void postpone(Context context, String des, String link) {
        Lib.showToast(context, context.getResources().getString(R.string.postpone_alert));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setSummaryPostponeNew(context, des, link);
        else
            setSummaryPostpone(context, des, link);
    }

    private static void setSummaryPostpone(Context context, String des, String link) {
        Intent intent = new Intent(context, SummaryService.class);
        intent.putExtra(Const.DESCTRIPTION, des);
        intent.putExtra(Const.LINK, link);
        PendingIntent piCheck = PendingIntent.getService(context, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC, TEN_MIN_IN_MILLS + System.currentTimeMillis(), piCheck);
    }

    @RequiresApi(21)
    public static void setSummaryPostponeNew(Context context, String des, String link) {
        ComponentName jobService = new ComponentName(context, InitJobService.class);
        JobInfo.Builder exerciseJobBuilder = new JobInfo.Builder(NotificationHelper.ID_SUMMARY_POSTPONE, jobService);
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        PersistableBundle extras = new PersistableBundle();
        extras.putString(Const.DESCTRIPTION, des);
        extras.putString(Const.LINK, link);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            exerciseJobBuilder.setEstimatedNetworkBytes(70000,1000);
        exerciseJobBuilder.setExtras(extras);
        exerciseJobBuilder.setMinimumLatency(TEN_MIN_IN_MILLS);
        exerciseJobBuilder.setOverrideDeadline(2 * TEN_MIN_IN_MILLS);
        exerciseJobBuilder.setPersisted(true); // save job after reboot
        exerciseJobBuilder.setRequiresDeviceIdle(false); // anyway: use device or not use
        exerciseJobBuilder.setRequiresCharging(false); // anyway: charging device or not charging
        jobScheduler.schedule(exerciseJobBuilder.build());
    }

    /**
     * Created by NeoSvet on 12.02.2018.
     */
    @RequiresApi(21)
    public static class InitJobService extends JobService {
        public static final String ACTION_FINISHED = "NeoActionFinished";

        @Override
        public boolean onStartJob(JobParameters param) {
            Context context = getApplicationContext();
            if (param.getJobId() == NotificationHelper.ID_SUMMARY) {
                Intent intent = new Intent(context, SummaryService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    SummaryService.enqueueWork(context, intent);
                else
                    context.startService(intent);
                initFinishReceiver(param);
                return true; //not finish
            } else if (param.getJobId() == NotificationHelper.ID_SUMMARY_POSTPONE) {
                PersistableBundle extras = param.getExtras();
                SummaryHelper summaryHelper = new SummaryHelper(context);
                summaryHelper.createNotification(
                        extras.getString(Const.DESCTRIPTION),
                        extras.getString(Const.LINK));
                summaryHelper.showNotification();
            }
            return false; //finish
        }

        private void initFinishReceiver(final JobParameters param) {
            BroadcastReceiver finishedReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    context.unregisterReceiver(this);
                    jobFinished(param, false);
                }
            };
            IntentFilter filter = new IntentFilter(ACTION_FINISHED);
            registerReceiver(finishedReceiver, filter);
        }

        @Override
        public boolean onStopJob(JobParameters param) {
            return false; //remove job from scheduler
        }
    }
}

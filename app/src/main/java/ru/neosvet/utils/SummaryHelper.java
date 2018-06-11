package ru.neosvet.utils;

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
import android.os.Build;
import android.os.PersistableBundle;
import android.support.annotation.RequiresApi;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.receiver.SummaryReceiver;
import ru.neosvet.vestnewage.service.SummaryService;

/**
 * Created by NeoSvet on 11.06.2018.
 */
public class SummaryHelper {

    public static void postpone(Context context, String des, String link) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setSummaryPostponeNew(context, des, link);
        else
            setSummaryPostpone(context, des, link);
    }

    public static void serviceFinish(Context context) {
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
            if (p > -1) {
                p = (p + 1) * 600000;
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
            if (p > -1) {
                p = (p + 1) * 600000;
                am.set(AlarmManager.RTC_WAKEUP, p + System.currentTimeMillis(), piCheck);
            }
        }
    }

    private static void setSummaryPostpone(Context context, String des, String link) {
        Lib.showToast(context, context.getResources().getString(R.string.postpone_alert));
        Intent intent = new Intent(context, SummaryService.class);
        intent.putExtra(DataBase.DESCTRIPTION, des);
        intent.putExtra(DataBase.LINK, link);
        PendingIntent piCheck = PendingIntent.getService(context, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC, 600000 + System.currentTimeMillis(), piCheck);
    }

    @RequiresApi(21)
    public static void setSummaryPostponeNew(Context context, String des, String link) {
        Lib.showToast(context, context.getResources().getString(R.string.postpone_alert));
        ComponentName jobService = new ComponentName(context, InitJobService.class);
        JobInfo.Builder exerciseJobBuilder = new JobInfo.Builder(NotificationHelper.ID_SUMMARY_POSTPONE, jobService);
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        long latency = 600000; // 10 min
        PersistableBundle extras = new PersistableBundle();
        extras.putString(DataBase.DESCTRIPTION, des);
        extras.putString(DataBase.LINK, link);
        exerciseJobBuilder.setExtras(extras);
        exerciseJobBuilder.setMinimumLatency(latency);
        exerciseJobBuilder.setOverrideDeadline(latency + latency);
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
                if (param.getJobId() == NotificationHelper.ID_SUMMARY_POSTPONE) {
                    PersistableBundle extras = param.getExtras();
                    intent.putExtra(DataBase.DESCTRIPTION, extras.getString(DataBase.DESCTRIPTION));
                    intent.putExtra(DataBase.LINK, extras.getString(DataBase.LINK));
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    SummaryService.enqueueWork(context, intent);
                else
                    context.startService(intent);
                initFinishReceiver(param);
                return true; //not finish
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

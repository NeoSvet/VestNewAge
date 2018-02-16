package ru.neosvet.vestnewage.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PersistableBundle;

import java.util.Date;

import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Notification;
import ru.neosvet.utils.Prom;

/**
 * Created by NeoSvet on 12.02.2018.
 */

public class InitJobService extends JobService {
    public static final int ID_PROM = 1, ID_SUMMARY = 2, ID_SUMMARY_POSTPONE = 3;
    public static final String ACTION_FINISHED = "NeoActionFinished";

    @Override
    public boolean onStartJob(JobParameters param) {
        Context context = getApplicationContext();
        if (param.getJobId() == ID_PROM) {
            Prom prom = new Prom(context);
            prom.showNotif();
            return false;
        } else { // ID_SUMMARY
            Intent intent = new Intent(context, SummaryService.class);
            if (param.getJobId() == ID_SUMMARY_POSTPONE) {
                PersistableBundle extras = param.getExtras();
                intent.putExtra(DataBase.DESCTRIPTION, extras.getString(DataBase.DESCTRIPTION));
                intent.putExtra(DataBase.LINK, extras.getString(DataBase.LINK));
            }
            context.startService(intent);
            initFinishReceiver(param);
            return true;
        }
    }

    private void initFinishReceiver(final JobParameters param) {
        BroadcastReceiver finishedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                jobFinished(param, false);
                Notification.show(context, "jobFinished", 666);
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_FINISHED);
        registerReceiver(finishedReceiver, filter);
    }

    @Override
    public boolean onStopJob(JobParameters param) {
        Notification.show(getApplicationContext(), "onStopJob", 888);
        return false; //remove job from scheduler
    }

    public static void setSummary(Context context, int p) {
        ComponentName jobService = new ComponentName(context, InitJobService.class);
        JobInfo.Builder exerciseJobBuilder = new JobInfo.Builder(ID_SUMMARY, jobService);
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(ID_SUMMARY);
        if (p > -1) {
            p = (p + 1) * 600000;
            exerciseJobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            exerciseJobBuilder.setPersisted(true); // save job after reboot
            exerciseJobBuilder.setRequiresDeviceIdle(false); // anyway: use device or not use
            exerciseJobBuilder.setRequiresCharging(false); // anyway: charging device or not charging
            exerciseJobBuilder.setPeriodic(p); // periodic for retry
            jobScheduler.schedule(exerciseJobBuilder.build());
        }
    }

    public static void setSummaryPostpone(Context context, String des, String link) {
        ComponentName jobService = new ComponentName(context, InitJobService.class);
        JobInfo.Builder exerciseJobBuilder = new JobInfo.Builder(ID_SUMMARY_POSTPONE, jobService);
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

    public static void setProm(Context context, int p) {
        ComponentName jobService = new ComponentName(context, InitJobService.class);
        JobInfo.Builder exerciseJobBuilder = new JobInfo.Builder(ID_PROM, jobService);
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(ID_SUMMARY);
        if (p > -1) {
            Prom prom = new Prom(context);
            Date d = prom.getPromDate();
            d.setMinutes(d.getMinutes() - p);
            if (p > 0)
                d.setSeconds(d.getSeconds() - 3);
            else
                d.setSeconds(d.getSeconds() - 30);
            if (d.getTime() < System.currentTimeMillis())
                d.setHours(d.getHours() + 24);
            long latency = d.getTime() - System.currentTimeMillis();
            exerciseJobBuilder.setMinimumLatency(latency);
            exerciseJobBuilder.setOverrideDeadline(latency + 5000);
            exerciseJobBuilder.setPersisted(true); // save job after reboot
            exerciseJobBuilder.setRequiresDeviceIdle(false); // anyway: use device or not use
            exerciseJobBuilder.setRequiresCharging(false); // anyway: charging device or not charging
            jobScheduler.schedule(exerciseJobBuilder.build());
        }
    }
}

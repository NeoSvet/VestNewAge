package ru.neosvet.vestnewage.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import ru.neosvet.utils.Prom;

/**
 * Created by NeoSvet on 12.02.2018.
 */

public class InitJobService extends JobService {
    public static final int ID_PROM = 1, ID_SUMMARY = 2;
    @Override
    public boolean onStartJob(JobParameters param) {
        Context context = getApplicationContext();
        if(param.getJobId() == ID_PROM)
        {
            Prom prom = new Prom(context);
            prom.showNotif();
            return false;
        }
        else // ID_SUMMARY
        {
            Intent intent = new Intent(context, SummaryService.class);
            context.startService(intent);
            return true;
        }
    }

    @Override
    public boolean onStopJob(JobParameters param) {
        return false; //remove job from scheduler for prom
    }

    public static void setSummary(Context context, int p) {
        ComponentName jobService = new ComponentName(context, InitJobService.class);
        JobInfo.Builder exerciseJobBuilder = new JobInfo.Builder(ID_SUMMARY, jobService);
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(ID_SUMMARY);
        if (p > -1) {
            p = (p + 1) * 600000;
            //exerciseJobBuilder.setMinimumLatency(p);
            //exerciseJobBuilder.setOverrideDeadline(TimeUnit.SECONDS.toMillis(5));
            exerciseJobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            exerciseJobBuilder.setRequiresDeviceIdle(false); // anyway: use device or not use
            exerciseJobBuilder.setRequiresCharging(false); // anyway: charging device or not charging
            exerciseJobBuilder.setPeriodic(p); // periodic for retry
            jobScheduler.schedule(exerciseJobBuilder.build());
        }
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
            exerciseJobBuilder.setOverrideDeadline(TimeUnit.SECONDS.toMillis(5));
            exerciseJobBuilder.setRequiresDeviceIdle(false); // anyway: use device or not use
            exerciseJobBuilder.setRequiresCharging(false); // anyway: charging device or not charging
            //exerciseJobBuilder.setPeriodic(p); // periodic for retry
            jobScheduler.schedule(exerciseJobBuilder.build());
        }
    }
}

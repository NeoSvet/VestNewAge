package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.support.annotation.NonNull;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.workers.LoaderWorker;

public class LoaderModel extends AndroidViewModel {
    public static final String TAG = "Loader";
    public static boolean inProgress;

    public LoaderModel(@NonNull Application application) {
        super(application);
    }

    public void startLoad(boolean withStyle, String link) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data data = new Data.Builder()
                .putString(Const.TASK, TAG)
                .putInt(Const.MODE, LoaderHelper.DOWNLOAD_PAGE)
                .putBoolean(Const.STYLE, withStyle)
                .putString(Const.LINK, link).build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(LoaderWorker.class)
                .setInputData(data)
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }
}

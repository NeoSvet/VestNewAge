package ru.neosvet.vestnewage.model;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.loader.PageLoader;
import ru.neosvet.vestnewage.workers.MarkersWorker;

public class CollectionsModel extends AndroidViewModel {
    public static final String TAG = "markers";
    public byte task = 0;

    public CollectionsModel(@NonNull Application application) {
        super(application);
    }

    public void start(boolean isExport, String file) {
        task = 1;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data data = new Data.Builder()
                .putBoolean(Const.MODE, isExport)
                .putString(Const.FILE, file)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(MarkersWorker.class)
                .setConstraints(constraints)
                .setInputData(data)
                .build();
        WorkContinuation job = WorkManager.getInstance(getApplication())
                .beginUniqueWork(TAG, ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }

    public void loadPage(String link) {
        task = 2;
        PageLoader loader = new PageLoader(false);
        new Thread(() -> {
            String error = null;
            try {
                loader.download(link, true);
            } catch (Exception e) {
                e.printStackTrace();
                ErrorUtils.setError(e);
                error = e.getMessage();
            }
            ProgressHelper.postProgress(new Data.Builder()
                    .putBoolean(Const.FINISH, true)
                    .putString(Const.ERROR, error)
                    .build());
        }).start();
    }
}
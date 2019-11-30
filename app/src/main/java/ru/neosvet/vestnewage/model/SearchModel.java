package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.support.annotation.NonNull;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.workers.SearchWorker;

public class SearchModel extends AndroidViewModel {
    public static final String TAG = "search";
    public static boolean cancel;

    public SearchModel(@NonNull Application application) {
        super(application);
    }

    public void search(String str, int mode, String start, String end) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build();
        Data.Builder data = new Data.Builder()
                .putString(Const.STRING, str)
                .putInt(Const.MODE, mode)
                .putString(Const.START, start)
                .putString(Const.END, end);
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(SearchWorker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }
}

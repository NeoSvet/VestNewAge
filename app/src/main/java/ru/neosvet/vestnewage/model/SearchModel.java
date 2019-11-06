package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.support.annotation.NonNull;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.workers.SearchWorker;

public class SearchModel extends ProgressModel {
    public static final String TAG = "search", START = "start", END = "end";
    private static SearchModel current = null;

    public static SearchModel getInstance() {
        return current;
    }

    public SearchModel(@NonNull Application application) {
        super(application);
        work = WorkManager.getInstance();
        state = work.getWorkInfosByTagLiveData(TAG);
        inProgress = false;
        current = this;
    }

    public void search(String str, int mode, String start, String end) {
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build();
        Data.Builder data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName())
                .putString(SearchWorker.STRING, str)
                .putInt(SearchWorker.MODE, mode)
                .putString(START, start)
                .putString(END, end);
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(SearchWorker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = work.beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }
}

package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.support.annotation.NonNull;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.workers.BookWorker;

public class BookModel extends ProgressModel {
    public static final String TAG = "book";
    private static BookModel current = null;
    private boolean dialog;

    public static BookModel getInstance() {
        return current;
    }

    public BookModel(@NonNull Application application) {
        super(application);
        current = this;
    }

    public void startLoad(boolean OTKR, boolean FROM_OTKR, boolean KATRENY) {
        ProgressHelper.setBusy(true);
        dialog = OTKR;
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data.Builder data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName())
                .putBoolean(Const.OTKR, OTKR)
                .putBoolean(Const.FROM_OTKR, FROM_OTKR)
                .putBoolean(Const.KATRENY, KATRENY);
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(BookWorker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }

    public boolean isDialog() {
        return dialog;
    }
}

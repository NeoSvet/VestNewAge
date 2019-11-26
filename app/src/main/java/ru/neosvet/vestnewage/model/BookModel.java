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
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.workers.BookWorker;

public class BookModel extends AndroidViewModel {
    public static final String TAG = "book";

    public BookModel(@NonNull Application application) {
        super(application);
    }

    public void startLoad(boolean FROM_OTKR, boolean KATRENY) {
        ProgressHelper.setBusy(true);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data data = new Data.Builder()
                .putString(Const.TASK, this.getClass().getSimpleName())
                .putBoolean(Const.FROM_OTKR, FROM_OTKR)
                .putBoolean(Const.KATRENY, KATRENY)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(BookWorker.class)
                .setInputData(data)
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }
}

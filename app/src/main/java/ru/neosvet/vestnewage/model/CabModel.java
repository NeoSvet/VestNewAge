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
import ru.neosvet.vestnewage.workers.CabWorker;

public class CabModel extends AndroidViewModel {
    public static final String TAG = "cab";
    public static final byte LOGIN = 0, CABINET = 1, WORDS = 2;
    public static String email, cookie = null;

    public CabModel(@NonNull Application application) {
        super(application);
    }

    private void startWorker(Data.Builder data) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(CabWorker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = WorkManager.getInstance(getApplication())
                .beginUniqueWork(TAG, ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }

    public void login(String n_email, String password) {
        email = n_email;
        Data.Builder data = new Data.Builder()
                .putString(Const.TASK, Const.LOGIN)
                .putString(Const.PASSWORD, password);
        startWorker(data);
    }

    public void getListWord() {
        Data.Builder data = new Data.Builder()
                .putString(Const.TASK, Const.GET_WORDS);
        startWorker(data);
    }

    public void selectWord(int index) {
        Data.Builder data = new Data.Builder()
                .putString(Const.TASK, Const.SELECT_WORD)
                .putInt(Const.LIST, index);
        startWorker(data);
    }
}

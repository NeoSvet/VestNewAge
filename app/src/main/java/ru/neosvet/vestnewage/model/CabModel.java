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
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.workers.CabWorker;

public class CabModel extends ProgressModel {
    public static final String TAG = "cab";
    public static final byte LOGIN = 0, CABINET = 1, WORDS = 2;
    private String email, cookie = "";
    private static CabModel current = null;

    public static CabModel getInstance() {
        return current;
    }

    public CabModel(@NonNull Application application) {
        super(application);
        work = WorkManager.getInstance();
        state = work.getWorkInfosByTagLiveData(TAG);
        inProgress = false;
        current = this;
    }

    private void startWorker(Data.Builder data) {
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        data.putString(ProgressModel.NAME, this.getClass().getSimpleName());
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(CabWorker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = work.beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }

    public void login(String email, String password) {
        this.email = email;
        Data.Builder data = new Data.Builder()
                .putString(Const.TASK, CabWorker.LOGIN)
                .putString(CabWorker.PASSWORD, password);
        startWorker(data);
    }

    public void getListWord() {
        Data.Builder data = new Data.Builder()
                .putString(Const.TASK, CabWorker.GET_WORDS);
        startWorker(data);
    }

    public void selectWord(int index) {
        Data.Builder data = new Data.Builder()
                .putString(Const.TASK, CabWorker.SELECT_WORD)
                .putInt(ProgressModel.LIST, index);
        startWorker(data);
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public String getCookie() {
        return cookie;
    }

    public String getEmail() {
        return email;
    }
}

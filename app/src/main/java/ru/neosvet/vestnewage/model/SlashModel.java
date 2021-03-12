package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.workers.SlashWorker;

public class SlashModel extends AndroidViewModel {
    public static final String TAG = "slash";
    public static boolean inProgress = false;
    private static final MutableLiveData<Bundle> live = new MutableLiveData<>();

    public SlashModel(@NonNull Application application) {
        super(application);
    }

    public static void removeObservers(LifecycleOwner owner) {
        if (live.hasObservers())
            live.removeObservers(owner);
    }

    public static void addObserver(LifecycleOwner owner, Observer<Bundle> observer) {
        if (!live.hasObservers())
            live.observe(owner, observer);
    }

    public static void post(Bundle data) {
        live.postValue(data);
    }

    public void startLoad() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data data = new Data.Builder()
                .putString(Const.TASK, this.getClass().getSimpleName())
                .build();
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(SlashWorker.class)
                .setInputData(data)
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = WorkManager.getInstance(getApplication())
                .beginUniqueWork(TAG, ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }
}

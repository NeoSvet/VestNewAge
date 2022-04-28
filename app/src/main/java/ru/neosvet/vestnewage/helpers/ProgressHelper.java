package ru.neosvet.vestnewage.helpers;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.work.Data;

public class ProgressHelper {
    private static boolean busy;
    private static final MutableLiveData<Data> live = new MutableLiveData<>();

    public static boolean isBusy() {
        return busy;
    }

    public static void setBusy(boolean v) {
        busy = v;
        if (!busy)
            live.setValue(new Data.Builder().build());
    }

    public static void addObserver(LifecycleOwner owner, Observer<Data> observer) {
        live.observe(owner, observer);
    }

    public static void postProgress(Data data) {
        live.postValue(data);
    }
}

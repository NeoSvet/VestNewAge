package ru.neosvet.vestnewage.helpers;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;

import androidx.work.Data;

public class ProgressHelper {
    private static boolean busy, cancel;
    private static String msg;
    private static int prog, max;
    private static MutableLiveData<Data> live = new MutableLiveData<Data>();

    public static boolean isBusy() {
        return busy;
    }

    public static void setBusy(boolean v) {
        busy = v;
        cancel = false;
        if (!busy)
            live.setValue(new Data.Builder().build());
    }

    public static void cancelled() {
        cancel = true;
    }

    public static int getProcent(float cur, float max) {
        return (int) (cur / max * 100f);
    }

    public static void removeObservers(LifecycleOwner owner) {
        live.removeObservers(owner);
    }

    public static void addObserver(LifecycleOwner owner, Observer<Data> observer) {
        if (!live.hasObservers())
            live.observe(owner, observer);
    }

    public static void postProgress(Data data) {
        live.postValue(data);
    }

    public static boolean isCancelled() {
        return cancel;
    }

    public static void upProg() {
        prog++;
    }

    public static void setMax(int n_max) {
        prog = 0;
        max = n_max;
    }

    public static int getMax() {
        return max;
    }

    public static int getProg() {
        return prog;
    }

    public static String getMessage() {
        return msg;
    }

    public static void setMessage(String n_msg) {
        msg = n_msg;
    }
}

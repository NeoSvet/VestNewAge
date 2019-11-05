package ru.neosvet.utils;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.List;

import ru.neosvet.vestnewage.model.SlashModel;

public class ProgressModel extends AndroidViewModel {
    public static final String NAME = "CLASS_NAME", ERROR = "ERROR", LIST = "list", PAGE = "page";
    public boolean inProgress = true;
    protected WorkManager work;
    protected LiveData<List<WorkInfo>> state;
    private MutableLiveData<Data> progress = new MutableLiveData<Data>();

    public ProgressModel(@NonNull Application application) {
        super(application);
    }

    public void finish() {
        inProgress = false;
    }

    public LiveData<List<WorkInfo>> getState() {
        return state;
    }

    public void setProgress(Data data) {
        progress.postValue(data);
    }

    public MutableLiveData<Data> getProgress() {
        return progress;
    }

    public static ProgressModel getModelByName(String name) {
        if (name.equals(SlashModel.class.getSimpleName()))
            return SlashModel.getInstance();
        return null;
    }
}

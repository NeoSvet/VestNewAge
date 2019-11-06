package ru.neosvet.utils;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;

import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.List;
import java.util.Set;

import ru.neosvet.vestnewage.model.BookModel;
import ru.neosvet.vestnewage.model.CabModel;
import ru.neosvet.vestnewage.model.CalendarModel;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.model.SearchModel;
import ru.neosvet.vestnewage.model.SiteModel;
import ru.neosvet.vestnewage.model.SlashModel;
import ru.neosvet.vestnewage.model.SummaryModel;

public class ProgressModel extends AndroidViewModel {
    public static final String NAME = "CLASS_NAME";
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

    public void removeObserves(LifecycleOwner owner) {
        state.removeObservers(owner);
        progress.removeObservers(owner);
    }

    public static ProgressModel getModelByName(String name) {
        if (name.equals(SlashModel.class.getSimpleName()))
            return SlashModel.getInstance();
        if (name.equals(CalendarModel.class.getSimpleName()))
            return CalendarModel.getInstance();
        if (name.equals(SummaryModel.class.getSimpleName()))
            return SummaryModel.getInstance();
        if (name.equals(BookModel.class.getSimpleName()))
            return BookModel.getInstance();
        if (name.equals(SiteModel.class.getSimpleName()))
            return SiteModel.getInstance();
        if (name.equals(CabModel.class.getSimpleName()))
            return CabModel.getInstance();
        if (name.equals(SearchModel.class.getSimpleName()))
            return SearchModel.getInstance();
        if (name.equals(LoaderModel.class.getSimpleName()))
            return LoaderModel.getInstance();
        return null;
    }

    public static String getFirstTag(Set<String> tags) {
        for (String t : tags)
            return t;
        return "none";
    }
}

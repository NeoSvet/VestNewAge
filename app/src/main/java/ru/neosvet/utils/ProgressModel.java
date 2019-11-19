package ru.neosvet.utils;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import androidx.work.Data;

import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
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
    public boolean inProgress = false, cancel = false;
    private MutableLiveData<Data> progress = new MutableLiveData<Data>();

    public ProgressModel(@NonNull Application application) {
        super(application);
    }

    public void finish() {
        inProgress = false;
        cancel = false;
    }

    public void postProgress(Data data) {
        progress.postValue(data);
    }

    public MutableLiveData<Data> getProgress() {
        return progress;
    }

    public void removeObservers(LifecycleOwner owner) {
        ProgressHelper.dismissDialog();
        progress.removeObservers(owner);
    }

    public void addObserver(LifecycleOwner owner, Observer<Data> observer) {
        if (!progress.hasObservers())
            progress.observe(owner, observer);
    }

    public static ProgressModel getModelByName(@Nullable String name) {
        if (name == null)
            return null;
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

    public void startService(String name) {
        LoaderHelper.postCommand(getApplication().getBaseContext(), name, false);
    }
}

package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.ErrorUtils;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.fragment.SiteFragment;
import ru.neosvet.vestnewage.helpers.CheckHelper;
import ru.neosvet.vestnewage.helpers.DateHelper;
import ru.neosvet.vestnewage.helpers.LoaderHelper;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.loader.CalendarLoader;
import ru.neosvet.vestnewage.loader.ListLoader;
import ru.neosvet.vestnewage.loader.PageLoader;
import ru.neosvet.vestnewage.loader.SiteLoader;
import ru.neosvet.vestnewage.loader.StyleLoader;
import ru.neosvet.vestnewage.loader.SummaryLoader;
import ru.neosvet.vestnewage.model.CalendarModel;
import ru.neosvet.vestnewage.model.LoaderModel;
import ru.neosvet.vestnewage.model.SiteModel;
import ru.neosvet.vestnewage.model.SummaryModel;
import ru.neosvet.vestnewage.storage.PageStorage;

public class LoaderWorker extends Worker {
    private PageLoader page;
    private int cur, max;
    private String name;

    public LoaderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    private boolean isCancelled() {
        if (name.equals(LoaderModel.TAG))
            return !LoaderModel.inProgress;
        if (name.equals(LoaderHelper.TAG))
            return !LoaderHelper.start;
        return ProgressHelper.isCancelled();
    }

    @NonNull
    @Override
    public Result doWork() {
        LoaderModel.inProgress = true;
        String error;
        ErrorUtils.setData(getInputData());
        name = getInputData().getString(Const.TASK);
        try {
            if (name.equals(LoaderModel.TAG)) {
                doLoad();
                return Result.success();
            } else {
                loadList();
                return postFinish();
            }
        } catch (Exception e) {
            File file = LoaderHelper.getFileList();
            if (file.exists())
                file.delete();
            e.printStackTrace();
            ErrorUtils.setError(e);
            error = e.getMessage();
        }
        LoaderModel.inProgress = false;
        if (name.equals(CheckHelper.class.getSimpleName())) {
            CheckHelper.postCommand(false);
            return Result.failure();
        }
        if (name.equals(LoaderHelper.TAG)) {
            LoaderHelper.postCommand(LoaderHelper.STOP_WITH_NOTIF, error);
            return Result.failure();
        }
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .putString(Const.ERROR, error)
                .build());
        return Result.failure();
    }

    private void loadList() throws Exception {
        if (name.equals(CheckHelper.class.getSimpleName())) {
            page = new PageLoader(false);
            downloadList();
            CheckHelper.postCommand(false);
            LoaderModel.inProgress = false;
            return;
        }

        ListLoader loader;
        if (name.equals(SummaryModel.class.getSimpleName())) {
            page = new PageLoader(false);
            loader = new SummaryLoader();
        } else if (name.equals(SiteModel.class.getSimpleName())) {
            page = new PageLoader(false);
            loader = new SiteLoader(getInputData().getString(Const.FILE));
        } else
            return;

        max = loader.getLinkList();
        downloadList();
    }

    private void doLoad() throws Exception {
        StyleLoader style = new StyleLoader();
        int mode = getInputData().getInt(Const.MODE, 0);
        if (mode != LoaderHelper.DOWNLOAD_PAGE)
            style.download(false);
        switch (mode) {
            case LoaderHelper.DOWNLOAD_ALL:
                download(LoaderHelper.ALL);
                break;
            case LoaderHelper.DOWNLOAD_ID:
                int p = getInputData().getInt(Const.SELECT, 0);
                download(p);
                break;
            case LoaderHelper.DOWNLOAD_YEAR:
                downloadYear(getInputData().getInt(Const.YEAR, 0));
                break;
            case LoaderHelper.DOWNLOAD_PAGE:
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.START, true)
                        .build());
                String link = getInputData().getString(Const.LINK);
                style.download(getInputData().getBoolean(Const.STYLE, false));
                if (link != null) {
                    page = new PageLoader(false);
                    page.download(link, true);
                }
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.FINISH, true)
                        .build());
                if (name.equals(LoaderHelper.TAG))
                    LoaderHelper.postCommand(LoaderHelper.STOP, null);
                LoaderModel.inProgress = false;
        }

        LoaderHelper.postCommand(LoaderHelper.STOP_WITH_NOTIF, null);
        LoaderModel.inProgress = false;
    }

    private Result postFinish() {
        LoaderModel.inProgress = false;
        ProgressHelper.postProgress(new Data.Builder()
                .putBoolean(Const.FINISH, true)
                .build());
        return Result.success();
    }

    private void downloadYear(int year) throws Exception {
        ProgressHelper.setMessage(App.context.getString(R.string.start));
        page = new PageLoader(true);
        DateHelper d = DateHelper.initToday();
        int k;
        if (year == d.getYear())
            k = d.getMonth() + 1;
        else
            k = 13;
        d.setYear(year);
        int m, n = 0;
        for (m = 1; m < k; m++) {
            d.setMonth(m);
            n += countBookList(d.getMY());
        }
        ProgressHelper.setMax(n);
        CalendarLoader loader = new CalendarLoader();
        for (m = 1; m < k && !isCancelled(); m++) {
            loader.setDate(year, m);
            loader.getLinkList();
            downloadList();
        }
    }

    private void downloadList() throws Exception {
        File file = LoaderHelper.getFileList();
        if (!file.exists())
            return;
        BufferedReader br = new BufferedReader(new FileReader(file));
        String s;
        while ((s = br.readLine()) != null && !isCancelled()) {
            page.download(s, false);
            if (max > 0) {
                cur++;
                ProgressHelper.postProgress(new Data.Builder()
                        .putBoolean(Const.DIALOG, true)
                        .putInt(Const.PROG, ProgressHelper.getProcent(cur, max))
                        .build());
            } else
                ProgressHelper.upProg();
        }
        br.close();
        file.delete();
    }

    private void download(int id) throws Exception {
        if (isCancelled())
            return;
        ProgressHelper.setMessage(App.context.getString(R.string.start));
        page = new PageLoader(true);
        // подсчёт количества страниц:
        int k = 0;
        if (id == LoaderHelper.ALL || id == R.id.nav_book)
            k = workWithBook(true);
        if (id == LoaderHelper.ALL || id == R.id.nav_site) {
            SiteLoader loader = new SiteLoader(Lib.getFile(SiteFragment.MAIN).toString());
            k += loader.getLinkList();
        }
        ProgressHelper.setMax(k);
        // загрузка страниц:
        if (id == LoaderHelper.ALL || id == R.id.nav_site) {
            downloadList();
            //SiteWorker.getListLink(context, lib.getFileByName(SiteFragment.NEWS).toString());
            //downloadList();
        }
        if (isCancelled())
            return;
        if (id == LoaderHelper.ALL || id == R.id.nav_book)
            workWithBook(false);
    }

    private int workWithBook(boolean count) throws Exception {
        int end_year, end_month, k = 0;
        DateHelper d = DateHelper.initToday();
        d.setDay(1);
        end_month = d.getMonth();
        end_year = d.getYear();
        d.setMonth(1);
        d.setYear(end_year - 1);
        while (!isCancelled()) {
            if (count)
                k += countBookList(d.getMY());
            else
                downloadBookList(d.getMY());
            if (d.getYear() == end_year && d.getMonth() == end_month)
                break;
            d.changeMonth(1);
        }
        return k;
    }

    private int countBookList(String name) {
        PageStorage storage = new PageStorage();
        storage.open(name);
        Cursor curTitle = storage.getLinks();
        int k = curTitle.getCount() - 1;
        curTitle.close();
        storage.close();
        return k;
    }

    private void downloadBookList(String name) throws Exception {
        PageStorage storage = new PageStorage();
        storage.open(name);
        Cursor curTitle = storage.getLinks();
        if (curTitle.moveToFirst()) {
            // пропускаем первую запись - там только дата изменения списка
            while (curTitle.moveToNext()) {
                page.download(curTitle.getString(0), false);
                ProgressHelper.upProg();
            }
        }
        curTitle.close();
        storage.close();
    }
}

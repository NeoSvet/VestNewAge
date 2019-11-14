package ru.neosvet.vestnewage.model;

import android.app.Application;
import android.arch.lifecycle.LifecycleOwner;
import android.content.DialogInterface;
import android.support.annotation.NonNull;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;

import ru.neosvet.ui.dialogs.ProgressDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.activity.MainActivity;
import ru.neosvet.vestnewage.workers.BookWorker;

public class BookModel extends ProgressModel {
    public static final String TAG = "book";
    private static BookModel current = null;
    private String msg;
    private int prog, max;
    private ProgressDialog dialog;

    public static BookModel getInstance() {
        return current;
    }

    public BookModel(@NonNull Application application) {
        super(application);
        current = this;
    }

    public void startLoad(boolean OTKR, boolean FROM_OTKR, boolean KATRENY) {
        inProgress = true;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();
        Data.Builder data = new Data.Builder()
                .putString(ProgressModel.NAME, this.getClass().getSimpleName())
                .putBoolean(Const.OTKR, OTKR)
                .putBoolean(Const.FROM_OTKR, FROM_OTKR)
                .putBoolean(Const.KATRENY, KATRENY);
        OneTimeWorkRequest task = new OneTimeWorkRequest
                .Builder(BookWorker.class)
                .setInputData(data.build())
                .setConstraints(constraints)
                .addTag(TAG)
                .build();
        WorkContinuation job = WorkManager.getInstance().beginUniqueWork(TAG,
                ExistingWorkPolicy.REPLACE, task);
        job.enqueue();
    }

    @Override
    public void removeObservers(LifecycleOwner owner) {
        super.removeObservers(owner);
        if (dialog != null)
            dialog.dismiss();
    }

    public boolean showDialog(MainActivity act, int new_max) {
        if (dialog != null)
            dialog.dismiss();
        if (new_max > -1) {
            prog = 0;
            max = new_max;
        }
        if (max == 0)
            return false;
        dialog = new ProgressDialog(act, max);
        dialog.setOnCancelListener(new ProgressDialog.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                BookModel.this.inProgress = false;
            }
        });
        dialog.show();
        dialog.setMessage(msg);
        dialog.setProgress(prog);
        return true;
    }

    public void updateDialog() {
        dialog.setMessage(msg);
        dialog.setProgress(prog);
    }

    public void dismissDialog() {
        if (dialog != null)
            dialog.dismiss();
    }

    public void setProgMsg(String msg) {
        this.msg = msg;
    }

    public void upProg() {
        if (prog == max)
            return;
        prog++;
    }
}

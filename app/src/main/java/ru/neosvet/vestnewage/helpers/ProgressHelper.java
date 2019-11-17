package ru.neosvet.vestnewage.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;

import androidx.work.Data;
import androidx.work.ListenableWorker;

import ru.neosvet.ui.dialogs.ProgressDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.model.LoaderModel;

public class ProgressHelper {
    private static ProgressHelper helper;
    private boolean start, busy;
    private String msg, name;
    private int prog, max;
    private ProgressDialog dialog;

    public static ProgressHelper getInstance() {
        if (helper == null)
            helper = new ProgressHelper();
        return helper;
    }

    private ProgressHelper() {
    }

    public void startTimer() {
        start = true;
        final Handler handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                ProgressModel model = ProgressModel.getModelByName(name);
                Lib.LOG("handler: " + name + "=" + (model == null));
                if (model != null && model.inProgress && !model.cancel) {
                    if (!updateDialog())
                        model.postProgress(new Data.Builder().putInt(Const.DIALOG, LoaderModel.DIALOG_SHOW).build());
                } else
                    ProgressHelper.getInstance().stop();
                return false;
            }
        });
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (start) {
                        Thread.sleep(DateHelper.SEC_IN_MILLS);
                        handler.sendEmptyMessage(0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void stop() {
        start = false;
    }

    public boolean isStoped() {
        return !start;
    }

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public static ListenableWorker.Result success() {
        Lib.LOG("result success");
        ProgressHelper.getInstance().setBusy(false);
        return ListenableWorker.Result.success();
    }

    public static ListenableWorker.Result failure() {
        Lib.LOG("result failure");
        ProgressHelper.getInstance().setBusy(false);
        return ListenableWorker.Result.failure();
    }

    public void startProgress(Context context, String name) {
        prog = 0;
        max = 0;
        msg = context.getResources().getString(R.string.start);
        this.name = name;
        Lib.LOG("startProgress " + name);
    }

    public void upProg() {
        prog++;
    }

    public void setMax(int max) {
        dismissDialog();
        Lib.LOG("setMax "+max);
        prog = 0;
        this.max = max;
    }

    public int getMax() {
        return max;
    }

    public void setMessage(String msg) {
        if (name != null && name.equals(LoaderModel.class.getSimpleName())) {
            this.msg = LoaderModel.getInstance().initMsg(msg);
        } else
            this.msg = msg;
    }

    public void setName(String name) {
        Lib.LOG("setName " + name);
        this.name = name;
    }

    public void showDialog(Activity act) {
        Lib.LOG("showDialog " + name);
        if (name == null)
            return;
        if (dialog != null)
            dialog.dismiss();
        Lib.LOG("showDialog " + prog + " / " + max);
        dialog = new ProgressDialog(act, max);
        dialog.setOnCancelListener(new ProgressDialog.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                ProgressModel.getModelByName(name).cancel = true;
            }
        });
        dialog.show();
        dialog.setMessage(msg);
        dialog.setProgress(prog);
    }

    public boolean updateDialog() {
        if (dialog == null)
            return false;
        dialog.setMessage(msg);
        dialog.setProgress(prog);
        return true;
    }

    public void dismissDialog() {
        if (dialog != null)
            dialog.dismiss();
    }
}

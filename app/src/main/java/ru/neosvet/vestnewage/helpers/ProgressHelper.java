package ru.neosvet.vestnewage.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import androidx.work.Data;
import androidx.work.ListenableWorker;

import ru.neosvet.ui.dialogs.ProgressDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.ProgressModel;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.model.LoaderModel;

public class ProgressHelper {
    private static boolean start, busy;
    private static String msg, name;
    private static int prog, max;
    private static ProgressDialog dialog;

    public static void startTimer() {
        start = true;
        final Handler handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                ProgressModel model = ProgressModel.getModelByName(name);
                if (model != null && model.inProgress && !model.cancel) {
                    if (!updateDialog())
                        model.postProgress(new Data.Builder().putInt(Const.DIALOG, LoaderModel.DIALOG_SHOW).build());
                } else
                    ProgressHelper.stop();
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

    public static void stop() {
        start = false;
        final Handler handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                if (!start) {
                    ProgressModel model = ProgressModel.getModelByName(name);
                    if (model != null)
                        model.startService(name);
                }
                return false;
            }
        });
        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(2 * DateHelper.SEC_IN_MILLS);
                    handler.sendEmptyMessage(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static boolean isBusy() {
        return busy;
    }

    public static void setBusy(boolean v) {
        busy = v;
    }

    public static ListenableWorker.Result success() {
        ProgressHelper.setBusy(false);
        return ListenableWorker.Result.success();
    }

    public static ListenableWorker.Result failure() {
        ProgressHelper.setBusy(false);
        return ListenableWorker.Result.failure();
    }

    public static void startProgress(Context context, String n_name) {
        prog = 0;
        max = 0;
        msg = context.getResources().getString(R.string.start);
        name = n_name;
    }

    public static void upProg() {
        prog++;
    }

    public static void setMax(int n_max) {
        dismissDialog();
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
        if (name != null && name.equals(LoaderModel.class.getSimpleName())) {
            msg = LoaderModel.getInstance().initMsg(n_msg);
        } else
            msg = n_msg;
    }

    public static void setName(String n_name) {
        name = n_name;
    }

    public static void showDialog(Activity act) {
        if (name == null)
            return;
        if (dialog != null)
            dialog.dismiss();
        dialog = new ProgressDialog(act, max);
        dialog.setOnCancelListener(new ProgressDialog.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                ProgressModel.getModelByName(name).cancel = true;
            }
        });
        dialog.setMinButton(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoaderHelper.postCommand(v.getContext(), name, true);
            }
        });
        dialog.show();
        dialog.setMessage(msg);
        dialog.setProgress(prog);
    }

    public static boolean updateDialog() {
        if (dialog == null)
            return false;
        dialog.setMessage(msg);
        dialog.setProgress(prog);
        return true;
    }

    public static void dismissDialog() {
        if (dialog != null)
            dialog.dismiss();
    }

    public static int getProcent(float cur, float max) {
        return (int) (cur / max * 100f);
    }
}

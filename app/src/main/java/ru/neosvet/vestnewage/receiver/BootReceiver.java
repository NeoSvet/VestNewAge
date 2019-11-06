package ru.neosvet.vestnewage.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.helpers.SummaryHelper;

/**
 * Created by NeoSvet on 10.02.2018.
 */

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences pref = context.getSharedPreferences(Const.PROM, Context.MODE_PRIVATE);
        int p = pref.getInt(Const.TIME, Const.TURN_OFF);
        if (p != Const.TURN_OFF)
            PromReceiver.setReceiver(context, p);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            pref = context.getSharedPreferences(Const.SUMMARY, Context.MODE_PRIVATE);
            p = pref.getInt(Const.TIME, Const.TURN_OFF);
            if (p != Const.TURN_OFF)
                SummaryHelper.setReceiver(context, 0); // or p?
        }
    }
}

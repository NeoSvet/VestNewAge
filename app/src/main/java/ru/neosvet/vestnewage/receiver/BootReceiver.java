package ru.neosvet.vestnewage.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import ru.neosvet.vestnewage.fragment.SettingsFragment;
import ru.neosvet.vestnewage.helpers.SummaryHelper;

/**
 * Created by NeoSvet on 10.02.2018.
 */

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences pref = context.getSharedPreferences(SettingsFragment.PROM, context.MODE_PRIVATE);
        int p = pref.getInt(SettingsFragment.TIME, -1);
        if (p > -1)
            PromReceiver.setReceiver(context, p);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            pref = context.getSharedPreferences(SettingsFragment.SUMMARY, context.MODE_PRIVATE);
            p = pref.getInt(SettingsFragment.TIME, -1);
            if (p > -1)
                SummaryHelper.setReceiver(context, 0); // or p?
        }
    }
}

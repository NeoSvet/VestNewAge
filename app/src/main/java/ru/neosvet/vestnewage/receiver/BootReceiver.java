package ru.neosvet.vestnewage.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import ru.neosvet.vestnewage.fragment.SettingsFragment;
import ru.neosvet.vestnewage.service.InitJobService;

/**
 * Created by NeoSvet on 10.02.2018.
 */

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences pref = context.getSharedPreferences(SettingsFragment.SUMMARY, context.MODE_PRIVATE);
        int p = pref.getInt(SettingsFragment.TIME, -1);
        if (p > -1)
            InitJobService.setSummary(context, p);
        pref = context.getSharedPreferences(SettingsFragment.PROM, context.MODE_PRIVATE);
        p = pref.getInt(SettingsFragment.TIME, -1);
        if (p > -1)
            InitJobService.setProm(context, p);
    }
}

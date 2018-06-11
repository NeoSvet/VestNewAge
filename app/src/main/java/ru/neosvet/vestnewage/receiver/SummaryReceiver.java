package ru.neosvet.vestnewage.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ru.neosvet.vestnewage.service.SummaryService;

//for API < 21
public class SummaryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service = new Intent(context, SummaryService.class);
        context.startService(service);
    }
}
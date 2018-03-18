package ru.neosvet.utils;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import ru.neosvet.vestnewage.service.InitJobService;
import ru.neosvet.vestnewage.service.SummaryService;

/**
 * Created by NeoSvet on 13.02.2018.
 */

public class Notification extends Activity {
    public static final String MODE = "mode";
//    public static final int COPY = 33;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int mode = getIntent().getIntExtra(MODE, -1);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (mode == Prom.notif_id) {
            manager.cancel(Prom.notif_id);
        } else if (mode == InitJobService.ID_SUMMARY_POSTPONE) {
            manager.cancel(SummaryService.notif_id);
            String des = getIntent().getStringExtra(DataBase.DESCTRIPTION);
            String link = getIntent().getStringExtra(DataBase.LINK);
            InitJobService.setSummaryPostpone(this, des, link);
        }
        finish();
    }

    public static PendingIntent getCancelPromNotif(Context context) {
        Intent intent = new Intent(context, Notification.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MODE, Prom.notif_id);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        return pendingIntent;
    }

    public static PendingIntent getPostponeSummaryNotif(Context context, String des, String link) {
        Intent intent = new Intent(context, Notification.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MODE, InitJobService.ID_SUMMARY_POSTPONE);
        intent.putExtra(DataBase.DESCTRIPTION, des);
        intent.putExtra(DataBase.LINK, link);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        return pendingIntent;
    }
}

package ru.neosvet.utils;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.service.InitJobService;
import ru.neosvet.vestnewage.service.SummaryService;

/**
 * Created by NeoSvet on 13.02.2018.
 */

public class Notification extends Activity {
    public static final String MODE = "mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int mode = getIntent().getIntExtra(MODE, -1);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (mode == InitJobService.ID_PROM) {
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
        intent.putExtra(MODE, InitJobService.ID_PROM);
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

    public static void show(Context context, String msg, int id) {
        PendingIntent piEmpty = PendingIntent.getActivity(context, 0, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.star)
                .setContentTitle("Debug notif")
                .setContentText(msg)
                .setTicker(msg)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(piEmpty)
                .setAutoCancel(true);
        nm.notify(id, mBuilder.build());
    }
}

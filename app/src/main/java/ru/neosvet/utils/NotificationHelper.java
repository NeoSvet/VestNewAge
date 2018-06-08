/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.neosvet.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;

/**
 * Helper class to manage notification channels, and create notifications.
 */
//@RequiresApi(26)
public class NotificationHelper extends ContextWrapper {
    private NotificationManager manager;
    public static final String CHANNEL_PRIMARY = "primary", CHANNEL_SECONDARY = "second",
            GROUP_TIPS = "ru.neosvet.vestnewage.TIPS";
    private List<String> notifList;

    /**
     * Registers notification channels, which can be used later by individual notifications.
     *
     * @param ctx The application context
     */
    public NotificationHelper(Context ctx) {
        super(ctx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan1 = new NotificationChannel(CHANNEL_PRIMARY,
                    getString(R.string.channel_privary), NotificationManager.IMPORTANCE_HIGH);
            chan1.setLightColor(Color.GREEN);
            chan1.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            getManager().createNotificationChannel(chan1);

            NotificationChannel chan2 = new NotificationChannel(CHANNEL_SECONDARY,
                    getString(R.string.channel_secondary), NotificationManager.IMPORTANCE_HIGH);
            chan2.setLightColor(Color.BLUE);
            chan2.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            getManager().createNotificationChannel(chan2);
        }
    }

    /**
     * Get a notification of type 1
     * <p>
     * Provide the builder rather than the notification it's self as useful for making notification
     * changes.
     *
     * @param title the title of the notification
     * @param body  the body text for the notification
     * @return the builder as it keeps a reference to the notification (since API 24)
     */
    public Notification.Builder getNotification(String title, String body, String channel) {
        Notification.Builder notifBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            notifBuilder = new Notification.Builder(getApplicationContext(), channel);
        else {
            notifBuilder = new Notification.Builder(getApplicationContext());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                if (notifList == null)
                    notifList = new ArrayList<>();
                notifList.add(title + " " + body);
            }
        }
        notifBuilder.setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(getSmallIcon())
                .setAutoCancel(true);
        if (body.length() > 44)
            notifBuilder.setStyle(new Notification.BigTextStyle().bigText(body));
        return notifBuilder;
    }

    public Notification.Builder getSummaryNotif(String title, String channel) {
        Notification.Builder notifBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            notifBuilder = new Notification.Builder(this, channel);
        else
            notifBuilder = new Notification.Builder(this);
        Notification.InboxStyle style = new Notification.InboxStyle()
                .setSummaryText(title);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            style.setBigContentTitle(getResources().getString(R.string.app_name));
            if (notifList != null) {
                for (int i = 0; i < notifList.size(); i++) {
                    style.addLine(notifList.get(i));
                }
                notifList.clear();
            }
        }
        notifBuilder.setContentTitle(getResources().getString(R.string.app_name))
                .setSmallIcon(R.drawable.star)
                .setStyle(style)
                .setGroupSummary(true);
        return notifBuilder;
    }

    /**
     * Send a notification.
     *
     * @param id           The ID of the notification
     * @param notification The notification object
     */
    public void notify(int id, Notification.Builder notification) {
        getManager().notify(id, notification.build());
    }

    /**
     * Get the small icon for this app
     *
     * @return The small icon resource id
     */
    private int getSmallIcon() {
        return R.drawable.star;
    }

    /**
     * Get the notification manager.
     * <p>
     * Utility method as this helper works with it a lot.
     *
     * @return The system service NotificationManager
     */
    private NotificationManager getManager() {
        if (manager == null) {
            manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return manager;
    }
}

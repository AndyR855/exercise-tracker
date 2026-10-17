package com.example.exercisetracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ReminderScheduler.isEnabled(ReminderScheduler.preferences(context))) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(ReminderScheduler.CHANNEL_ID,
                "Exercise reminders", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Daily reminder to do some intentional movement");
        manager.createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(context, 2,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (manager.areNotificationsEnabled()) {
                Notification notification = new Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setColor(0xFF2563EB)
                        .setContentTitle("Exercise Tracker")
                        .setContentText("How are you feeling today? Just 5 minutes is enough.")
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build();
                manager.notify(1, notification);
            }
        } catch (SecurityException notificationsRevoked) {
            // Revocation must not crash the receiver or stop tomorrow's schedule.
        } finally {
            ReminderScheduler.scheduleNext(context);
        }
    }
}

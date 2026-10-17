package com.example.exercisetracker;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "exercise_reminder";

    @Override
    public void onReceive(Context context, Intent intent) {

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Daily exercise reminder",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription(
                    "Reminder to do your daily exercise"
            );

            manager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(context, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                2,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        android.app.Notification.Builder notification =
                new android.app.Notification.Builder(
                        context,
                        CHANNEL_ID
                )
                .setSmallIcon(android.R.drawable.ic_menu_today)
                .setContentTitle("Exercise Tracker")
                .setContentText(
                        "How are you feeling today? Just 5 minutes is enough."
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        manager.notify(1, notification.build());

        scheduleNextReminder(context);
    }

    private void scheduleNextReminder(Context context) {
        android.content.SharedPreferences prefs =
                context.getSharedPreferences(
                        "exercise_tracker",
                        Context.MODE_PRIVATE
                );

        int hour = prefs.getInt("reminder_hour", -1);
        int minute = prefs.getInt("reminder_minute", -1);

        if (hour < 0 || minute < 0) {
            return;
        }

        Calendar next = Calendar.getInstance();
        next.add(Calendar.DAY_OF_YEAR, 1);
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        Intent reminderIntent =
                new Intent(context, ReminderReceiver.class);

        PendingIntent reminderPendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        1,
                        reminderIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE
                );

        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(
                        Context.ALARM_SERVICE
                );

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                next.getTimeInMillis(),
                reminderPendingIntent
        );
    }
}

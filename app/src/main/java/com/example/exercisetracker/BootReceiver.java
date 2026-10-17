package com.example.exercisetracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

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
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent reminderIntent =
                new Intent(context, ReminderReceiver.class);

        PendingIntent pendingIntent =
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
                pendingIntent
        );
    }
}

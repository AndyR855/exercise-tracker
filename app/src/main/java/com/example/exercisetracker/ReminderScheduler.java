package com.example.exercisetracker;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/** Uses the original preference names, receiver and PendingIntent request code. */
final class ReminderScheduler {
    static final String CHANNEL_ID = "exercise_reminder";

    private ReminderScheduler() {}

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("exercise_tracker", Context.MODE_PRIVATE);
    }

    static boolean isEnabled(SharedPreferences prefs) {
        int hour = prefs.getInt("reminder_hour", -1);
        int minute = prefs.getInt("reminder_minute", -1);
        return hour >= 0 && hour < 24 && minute >= 0 && minute < 60;
    }

    private static PendingIntent pendingIntent(Context context) {
        return PendingIntent.getBroadcast(context, 1, new Intent(context, ReminderReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleNext(Context context) {
        SharedPreferences prefs = preferences(context);
        if (!isEnabled(prefs)) return;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, prefs.getInt("reminder_hour", -1));
        next.set(Calendar.MINUTE, prefs.getInt("reminder_minute", -1));
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DAY_OF_YEAR, 1);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = pendingIntent(context);
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
                return;
            } catch (SecurityException revokedDuringScheduling) {
                // The user can revoke exact-alarm access between the check and the call.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
    }

    static void disable(Context context) {
        // Clear first so an already-delivering broadcast cannot re-arm the reminder.
        preferences(context).edit().remove("reminder_hour").remove("reminder_minute").apply();
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = pendingIntent(context);
        alarms.cancel(pending);
        pending.cancel();
        context.getSystemService(NotificationManager.class).cancel(1);
    }
}

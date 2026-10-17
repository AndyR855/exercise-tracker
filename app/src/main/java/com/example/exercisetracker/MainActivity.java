package com.example.exercisetracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS = "exercise_tracker";
    private static final String CHANNEL_ID = "exercise_reminder";
    private static final String EXERCISE_NONE = "No exercise";
    private static final int REMINDER_REQUEST_CODE = 1;

    private SharedPreferences prefs;
    private TextView historyText;
    private Button reminderButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100
            );
        }

        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Exercise Tracker");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView date = new TextView(this);
        date.setText("\n" + today());
        date.setTextSize(18);
        date.setGravity(Gravity.CENTER);
        root.addView(date);

        TextView feelTitle = new TextView(this);
        feelTitle.setText("\nHow are you feeling?");
        feelTitle.setTextSize(20);
        root.addView(feelTitle);

        String[] feelings = {"😴 Tired", "😐 SoSo", "🙂 Good", "😃 Great"};

        for (String feeling : feelings) {
            Button button = new Button(this);
            button.setText(feeling);
            button.setOnClickListener(v -> {
                prefs.edit()
                        .putString("feeling_" + dateKey(), feeling)
                        .apply();
                updateScreen();
            });
            root.addView(button);
        }

        TextView exerciseTitle = new TextView(this);
        exerciseTitle.setText("\nHow much exercise?");
        exerciseTitle.setTextSize(20);
        root.addView(exerciseTitle);

        addExerciseButton(root, "Minimum — 5 minutes");
        addExerciseButton(root, "Normal — 20–30 minutes");
        addExerciseButton(root, "Bonus — 30+ minutes");
        addExerciseButton(root, EXERCISE_NONE);

        reminderButton = new Button(this);
        reminderButton.setOnClickListener(v -> onReminderButtonClick());
        root.addView(reminderButton);

        updateReminderButton();

        historyText = new TextView(this);
        historyText.setTextSize(16);
        historyText.setPadding(0, 30, 0, 20);
        root.addView(historyText);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        updateScreen();
    }

    private void addExerciseButton(LinearLayout root, String exercise) {
        Button button = new Button(this);
        button.setText(exercise);
        button.setOnClickListener(v -> {
            prefs.edit()
                    .putString("exercise_" + dateKey(), exercise)
                    .apply();
            updateScreen();
        });
        root.addView(button);
    }

    private void updateScreen() {
        StringBuilder history = new StringBuilder();
        history.append("\n30-day history\n\n");

        Calendar cal = Calendar.getInstance();

        int completed = 0;

        for (int i = 0; i < 30; i++) {
            String key = new SimpleDateFormat(
                    "yyyy-MM-dd", Locale.US
            ).format(cal.getTime());

            String f = prefs.getString("feeling_" + key, "");
            String e = prefs.getString("exercise_" + key, "");

            if (!e.isEmpty() && !EXERCISE_NONE.equals(e)) {
                completed++;
            }

            history.append(
                    new SimpleDateFormat("dd MMM", Locale.US)
                            .format(cal.getTime())
            );
            history.append(" - ");
            history.append(f.isEmpty() ? "—" : f);
            history.append(" - ");
            history.append(formatExerciseForHistory(e));
            history.append("\n");

            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        history.insert(0, "Completed: " + completed + " / 30 days\n");

        historyText.setText(history.toString());
    }

    private String formatExerciseForHistory(String exercise) {
        if (exercise.isEmpty()) {
            return "—";
        }
        if (EXERCISE_NONE.equals(exercise)) {
            return EXERCISE_NONE;
        }
        if (exercise.startsWith("Minimum")) {
            return "5 min";
        }
        if (exercise.startsWith("Normal")) {
            return "25 min";
        }
        if (exercise.startsWith("Bonus")) {
            return "30+ min";
        }
        return exercise;
    }

    private void onReminderButtonClick() {
        int hour = prefs.getInt("reminder_hour", -1);
        int minute = prefs.getInt("reminder_minute", -1);

        if (hour >= 0 && minute >= 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Daily exercise reminder")
                    .setItems(
                            new String[]{"Change time", "Disable reminder"},
                            (dialog, which) -> {
                                if (which == 0) {
                                    showReminderTimePicker();
                                } else {
                                    disableReminder();
                                }
                            }
                    )
                    .show();
        } else {
            showReminderTimePicker();
        }
    }

    private void showReminderTimePicker() {
        Calendar now = Calendar.getInstance();

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hour, minute) -> scheduleReminder(hour, minute),
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true
        );

        dialog.setTitle("Daily exercise reminder");
        dialog.show();
    }

    private void disableReminder() {
        Intent intent = new Intent(this, ReminderReceiver.class);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                REMINDER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(ALARM_SERVICE);

        alarmManager.cancel(pendingIntent);

        prefs.edit()
                .remove("reminder_hour")
                .remove("reminder_minute")
                .apply();

        updateReminderButton();
    }

    private void scheduleReminder(int hour, int minute) {
        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.HOUR_OF_DAY, hour);
        trigger.set(Calendar.MINUTE, minute);
        trigger.set(Calendar.SECOND, 0);
        trigger.set(Calendar.MILLISECOND, 0);

        if (trigger.getTimeInMillis() <= System.currentTimeMillis()) {
            trigger.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent intent = new Intent(this, ReminderReceiver.class);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                REMINDER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager =
                (AlarmManager) getSystemService(ALARM_SERVICE);

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                trigger.getTimeInMillis(),
                pendingIntent
        );

        prefs.edit()
                .putInt("reminder_hour", hour)
                .putInt("reminder_minute", minute)
                .apply();

        updateReminderButton();
    }

    private void updateReminderButton() {
        int hour = prefs.getInt("reminder_hour", -1);
        int minute = prefs.getInt("reminder_minute", -1);

        if (hour >= 0 && minute >= 0) {
            reminderButton.setText(
                    "🔔 Daily reminder — " +
                    String.format(Locale.US, "%02d:%02d", hour, minute) +
                    "\nTap to change or disable"
            );
        } else {
            reminderButton.setText("🔔 Daily reminder — Not set");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Exercise reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            channel.setDescription(
                    "Daily reminder to do some intentional movement"
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    private String dateKey() {
        return new SimpleDateFormat(
                "yyyy-MM-dd", Locale.US
        ).format(new Date());
    }

    private String today() {
        return new SimpleDateFormat(
                "EEEE, d MMMM yyyy", Locale.US
        ).format(new Date());
    }
}

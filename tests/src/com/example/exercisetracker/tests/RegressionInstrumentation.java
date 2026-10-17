package com.example.exercisetracker.tests;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Device-side integration checks. Run only on the disposable test emulator. */
public class RegressionInstrumentation extends Instrumentation {
    private Activity activity;
    private String mode;
    private int assertions;
    private SharedPreferences prefs;
    private static final String[] FEELINGS = {"😴 Tired", "😐 SoSo", "🙂 Good", "😃 Great"};
    private static final String[] EXERCISES = {"Minimum — 5 minutes", "Normal — 20–30 minutes", "Bonus — 30+ minutes", "No exercise"};

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mode = arguments.getString("mode", "verify");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"),
                    "Refusing to modify a physical device's data");
            prefs = getTargetContext().getSharedPreferences("exercise_tracker", Context.MODE_PRIVATE);
            launch();
            if ("seed".equals(mode)) seed();
            else if ("verify".equals(mode)) verify();
            else if ("restart".equals(mode)) restart();
            else if ("layout".equals(mode)) onUi(() -> checkHistoryLayout());
            else if ("notification".equals(mode)) notification();
            else if ("restore".equals(mode)) restore();
            else if ("denied".equals(mode)) denied();
            else if ("fallback".equals(mode)) fallback();
            else if ("changeTime".equals(mode)) changeTime();
            else if ("disabled".equals(mode)) disabled();
            else throw new AssertionError("Unknown mode: " + mode);
            // Instrumentation teardown stops the process without the normal Activity stop flush.
            check(prefs.edit().commit(), "Async preference writes flushed before test process exits");
            result.putString("stream", "\nRESULT: PASS (" + mode + ", " + assertions + " assertions)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            StringWriter stack = new StringWriter();
            failure.printStackTrace(new PrintWriter(stack));
            result.putString("stream", "\nRESULT: FAIL (" + mode + ")\n" + stack);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void launch() {
        Intent intent = new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext(),
                "com.example.exercisetracker.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = startActivitySync(intent);
        waitForIdleSync();
    }

    private String key(int daysAgo) {
        Calendar date = Calendar.getInstance();
        date.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date.getTime());
    }

    private Map<String, Object> fixture() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            if (i == 11) continue; // An unrecorded day remains unrecorded.
            values.put("feeling_" + key(i), FEELINGS[(i + 1) % 4]);
            values.put("exercise_" + key(i), EXERCISES[(i + 1) % 4]);
        }
        values.put("exercise_" + key(7), "Legacy exercise");
        values.put("feeling_" + key(40), "😴 Tired");
        values.put("exercise_" + key(40), EXERCISES[2]);
        values.put("reminder_hour", 18);
        values.put("reminder_minute", 0);
        return values;
    }

    private void seed() throws Exception {
        check("1.2".equals(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(), 0).versionName),
                "Seed must run against the original 1.2 APK");
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, Object> entry : fixture().entrySet()) {
            if (entry.getValue() instanceof Integer) editor.putInt(entry.getKey(), (Integer) entry.getValue());
            else editor.putString(entry.getKey(), (String) entry.getValue());
        }
        check(editor.commit(), "Seed saved to real SharedPreferences");
        check(prefs.getAll().equals(fixture()), "All original-version fixture records saved");
    }

    private void verify() throws Exception {
        check("1.3".equals(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(), 0).versionName), "Release version");
        check(prefs.getAll().equals(fixture()), "Every preference survives signed in-place update unchanged");
        check(getTargetContext().getPackageManager().getApplicationIcon(getTargetContext().getPackageName()) instanceof AdaptiveIconDrawable,
                "Installed launcher icon is adaptive");
        onUi(() -> {
            check(selected("feelingCards", "😐 SoSo"), "Previous feeling is highlighted");
            check(selected("exerciseCards", EXERCISES[1]), "Previous exercise is highlighted");
            checkHistory();
            checkHistoryLayout();
        });
        Map<String, ?> prior = new LinkedHashMap<>(prefs.getAll());
        for (String feeling : FEELINGS) {
            onUi(() -> {
                clickCard("feelingCards", feeling);
                check(feeling.equals(prefs.getString("feeling_" + key(0), "")), "Immediate feeling persistence");
                check(selected("feelingCards", feeling), "Feeling selected state");
            });
        }
        for (String exercise : EXERCISES) {
            onUi(() -> {
                clickCard("exerciseCards", exercise);
                check(exercise.equals(prefs.getString("exercise_" + key(0), "")), "Immediate exercise persistence");
                check(selected("exerciseCards", exercise), "Exercise selected state");
                checkHistory();
            });
        }
        for (Map.Entry<String, ?> entry : prior.entrySet()) {
            if (!entry.getKey().equals("feeling_" + key(0)) && !entry.getKey().equals("exercise_" + key(0))) {
                check(entry.getValue().equals(prefs.getAll().get(entry.getKey())), "Past data unchanged: " + entry.getKey());
            }
        }
        onUi(() -> {
            clickCard("feelingCards", "🙂 Good");
            clickCard("exerciseCards", EXERCISES[2]);
            ((View) field("disableButton")).performClick();
            check(!prefs.contains("reminder_hour") && !prefs.contains("reminder_minute"), "Disable removes original reminder keys");
            check(((TextView) field("reminderText")).getText().toString().equals("Daily Reminder — Disabled"), "Disabled display");
            check(((View) field("disableButton")).getVisibility() == View.GONE, "Disabled controls update");
        });
        // Exercise the real Enable button and native TimePickerDialog confirmation.
        onUi(() -> ((View) field("reminderButton")).performClick());
        waitForIdleSync();
        AccessibilityNodeInfo ok = findText("OK");
        check(ok.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Native reminder picker confirmation");
        waitForIdleSync();
        check(prefs.getInt("reminder_hour", -1) == 18 && prefs.getInt("reminder_minute", -1) == 0, "Enable saves chosen time");
        onUi(() -> {
            check(((View) field("disableButton")).getVisibility() == View.VISIBLE, "Disable is directly available while enabled");
            check(((TextView) field("reminderText")).getText().toString().equals("Daily Reminder — 18:00"), "Enabled display");
        });
    }

    private void restart() throws Exception {
        onUi(() -> {
            check("🙂 Good".equals(prefs.getString("feeling_" + key(0), "")), "Feeling saved across process death");
            check(EXERCISES[2].equals(prefs.getString("exercise_" + key(0), "")), "Exercise saved across process death");
            check(selected("feelingCards", "🙂 Good"), "Feeling highlight after process death");
            check(selected("exerciseCards", EXERCISES[2]), "Exercise highlight after process death");
            check(prefs.getInt("reminder_hour", -1) == 18 && prefs.getInt("reminder_minute", -1) == 0,
                    "Changed reminder time persists across process death");
            checkHistory();
            checkHistoryLayout();
        });
    }

    private void notification() throws Exception {
        check(prefs.contains("reminder_hour"), "Enabled before notification");
        Class<?> receiver = Class.forName("com.example.exercisetracker.ReminderReceiver", true, getTargetContext().getClassLoader());
        onUi(() -> ((android.content.BroadcastReceiver) receiver.getDeclaredConstructor().newInstance())
                .onReceive(getTargetContext(), new Intent()));
        NotificationManager manager = getTargetContext().getSystemService(NotificationManager.class);
        check(manager.getActiveNotifications().length == 1, "Reminder notification delivered");
        android.app.Notification notification = manager.getActiveNotifications()[0].getNotification();
        check(notification.getSmallIcon().getResId() != android.R.drawable.ic_menu_today, "Custom notification icon");
        check(notification.contentIntent != null, "Notification opens the app");
        // Disabled broadcasts must neither notify nor re-arm.
        onUi(() -> ((View) field("disableButton")).performClick());
        onUi(() -> ((android.content.BroadcastReceiver) receiver.getDeclaredConstructor().newInstance())
                .onReceive(getTargetContext(), new Intent()));
        check(manager.getActiveNotifications().length == 0, "Disabled notification cancelled and stale broadcast ignored");
    }

    private void restore() throws Exception {
        // App onResume also schedules; the shell runner separately checks the alarm before launch after reboot.
        check(prefs.getInt("reminder_hour", -1) == 18, "Reminder hour persists after reboot");
        check(prefs.getInt("reminder_minute", -1) == 0, "Reminder minute persists after reboot");
        onUi(() -> check(((View) field("disableButton")).getVisibility() == View.VISIBLE, "Reboot keeps disable control available"));
    }

    private void denied() throws Exception {
        NotificationManager manager = getTargetContext().getSystemService(NotificationManager.class);
        check(!manager.areNotificationsEnabled(), "Notifications are denied for this test");
        Class<?> receiver = Class.forName("com.example.exercisetracker.ReminderReceiver", true, getTargetContext().getClassLoader());
        onUi(() -> ((android.content.BroadcastReceiver) receiver.getDeclaredConstructor().newInstance())
                .onReceive(getTargetContext(), new Intent()));
        check(manager.getActiveNotifications().length == 0, "Denied notifications do not crash or post");
        onUi(() -> {
            check(((View) field("notificationSettingsButton")).getVisibility() == View.VISIBLE, "Permission recovery is available");
            check(((View) field("disableButton")).getVisibility() == View.VISIBLE, "Permission denial never hides Disable");
        });
    }

    private void disabled() throws Exception {
        check(!prefs.contains("reminder_hour") && !prefs.contains("reminder_minute"), "Disabled reminder stays disabled after restart");
        onUi(() -> {
            check(((TextView) field("reminderText")).getText().toString().equals("Daily Reminder — Disabled"), "Disabled status after restart");
            check(((TextView) field("reminderButton")).getText().toString().equals("Enable reminder"), "Enable remains available");
        });
    }

    private void fallback() throws Exception {
        check(!getTargetContext().getSystemService(AlarmManager.class).canScheduleExactAlarms(), "Exact-alarm access denied for fallback test");
        check(prefs.contains("reminder_hour"), "Fallback preserves reminder preference");
        onUi(() -> check(((View) field("disableButton")).getVisibility() == View.VISIBLE, "Fallback reminder remains easy to disable"));
    }

    private void changeTime() throws Exception {
        android.accessibilityservice.AccessibilityServiceInfo service = getUiAutomation().getServiceInfo();
        service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        getUiAutomation().setServiceInfo(service);
        setNativeTime(19, 15);
        setNativeTime(18, 0);
    }

    private void setNativeTime(int hour, int minute) throws Exception {
        onUi(() -> ((View) field("reminderButton")).performClick());
        AccessibilityNodeInfo toggle = findId("android:id/toggle_mode");
        check(toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Native time picker keyboard mode");
        setText(findId("android:id/input_hour"), String.valueOf(hour));
        setText(findId("android:id/input_minute"), String.valueOf(minute));
        check(findText("OK").performAction(AccessibilityNodeInfo.ACTION_CLICK), "Confirm changed time");
        waitForIdleSync();
        check(prefs.getInt("reminder_hour", -1) == hour && prefs.getInt("reminder_minute", -1) == minute,
                "Changed reminder time is saved");
        onUi(() -> check(((TextView) field("reminderText")).getText().toString().equals(
                String.format(Locale.US, "Daily Reminder — %02d:%02d", hour, minute)), "Changed time is displayed"));
    }

    private void setText(AccessibilityNodeInfo node, String text) {
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments), "Native time input");
    }

    private AccessibilityNodeInfo findId(String id) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                java.util.List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByViewId(id);
                if (!matches.isEmpty()) return matches.get(0);
            }
            android.os.SystemClock.sleep(100);
        }
        throw new AssertionError("Missing native control: " + id);
    }

    private void checkHistory() throws Exception {
        TextView[] rows = (TextView[]) field("historyRows");
        check(rows.length == 30, "Exactly 30 history dates");
        int completed = 0;
        for (int i = 0; i < 30; i++) {
            String e = prefs.getString("exercise_" + key(i), "");
            String f = prefs.getString("feeling_" + key(i), "");
            if (!e.isEmpty() && !"No exercise".equals(e)) completed++;
            Calendar cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -i);
            String date = new SimpleDateFormat("dd MMM", Locale.US).format(cal.getTime());
            String duration = e.isEmpty() ? "—" : e.startsWith("Minimum") ? "5 min" : e.startsWith("Normal") ? "25 min" : e.startsWith("Bonus") ? "30+ min" : e;
            check(rows[i].getText().toString().equals(date + " - " + (f.isEmpty() ? "—" : f) + " - " + duration), "History content for " + key(i));
        }
        check(((TextView) field("completedText")).getText().toString().equals("Exercise completed: " + completed + " / 30 days"), "Original completion calculation");
        check(((android.widget.ProgressBar) field("progress")).getProgress() == completed, "Progress matches completion");
    }

    private void checkHistoryLayout() throws Exception {
        for (TextView row : (TextView[]) field("historyRows")) {
            check(row.getLineCount() == 1, "One visual line per date");
            check(row.getLayout() != null && row.getLayout().getEllipsisCount(0) == 0, "No history ellipsis");
            float available = row.getWidth() - row.getPaddingLeft() - row.getPaddingRight();
            check(row.getPaint().measureText(row.getText().toString()) <= available + 1, "Entire record fits history width");
        }
        View content = activity.findViewById(android.R.id.content);
        checkTouchTargets(content);
    }

    private void checkTouchTargets(View view) {
        if (view.isClickable() && view.getVisibility() == View.VISIBLE) {
            check(view.getHeight() >= Math.round(48 * activity.getResources().getDisplayMetrics().density), "48dp touch target");
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkTouchTargets(group.getChildAt(i));
        }
    }

    private boolean selected(String name, String key) throws Exception {
        Map<?, ?> cards = (Map<?, ?>) field(name);
        int count = 0;
        for (Object card : cards.values()) if (((View) card).isSelected()) count++;
        View card = (View) cards.get(key);
        AccessibilityNodeInfo info = card.createAccessibilityNodeInfo();
        return count == 1 && card.isSelected() && info.isCheckable() && info.isChecked();
    }

    private void clickCard(String name, String key) throws Exception {
        ((View) ((Map<?, ?>) field(name)).get(key)).performClick();
    }

    private AccessibilityNodeInfo findText(String text) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                java.util.List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(text);
                if (!matches.isEmpty()) return matches.get(0);
            }
            android.os.SystemClock.sleep(100);
        }
        throw new AssertionError("Missing native dialog button: " + text);
    }

    private Object field(String name) throws Exception {
        Field field = activity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private interface Action { void run() throws Exception; }
    private void onUi(Action action) throws Exception {
        Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { failure[0] = t; } });
        waitForIdleSync();
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    private void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}

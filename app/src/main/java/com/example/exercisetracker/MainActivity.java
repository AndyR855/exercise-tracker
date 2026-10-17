package com.example.exercisetracker;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PREFS = "exercise_tracker";
    private static final String EXERCISE_NONE = "No exercise";
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int DARK_BLUE = Color.rgb(29, 78, 216);
    private static final int LIGHT_BLUE = Color.rgb(219, 234, 254);
    private static final int BACKGROUND = Color.rgb(245, 247, 252);
    private static final int TEXT = Color.rgb(30, 41, 59);
    private static final int MUTED = Color.rgb(100, 116, 139);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int BORDER = Color.rgb(226, 232, 240);

    private SharedPreferences prefs;
    private TextView dateText;
    private TextView completedText;
    private TextView reminderText;
    private TextView reminderHint;
    private Button reminderButton;
    private Button disableButton;
    private Button notificationSettingsButton;
    private ProgressBar progress;
    private final TextView[] historyRows = new TextView[30];
    private final Map<String, SelectionCard> feelingCards = new LinkedHashMap<>();
    private final Map<String, SelectionCard> exerciseCards = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable midnightRefresh = () -> {
        updateScreen();
        scheduleMidnightRefresh();
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateScreen();
        // Also restores a saved reminder after an app update or permission change.
        ReminderScheduler.scheduleNext(this);
        scheduleMidnightRefresh();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(midnightRefresh);
        super.onPause();
    }

    private void scheduleMidnightRefresh() {
        handler.removeCallbacks(midnightRefresh);
        Calendar next = Calendar.getInstance();
        next.add(Calendar.DAY_OF_YEAR, 1);
        next.set(Calendar.HOUR_OF_DAY, 0);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        handler.postDelayed(midnightRefresh, Math.max(1000, next.getTimeInMillis() - System.currentTimeMillis()));
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        FrameLayout canvas = new FrameLayout(this);
        canvas.setPadding(dp(16), dp(8), dp(16), dp(24));
        LinearLayout root = column();
        int width = Math.min(getResources().getConfiguration().screenWidthDp - 32, 600);
        FrameLayout.LayoutParams content = new FrameLayout.LayoutParams(dp(width), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        canvas.addView(root, content);
        scroll.addView(canvas);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(22), dp(18), dp(22));
        GradientDrawable headerBackground = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{BLUE, DARK_BLUE});
        headerBackground.setCornerRadius(dp(24));
        header.setBackground(headerBackground);
        header.setElevation(dp(3));
        LinearLayout heading = column();
        TextView title = text("Exercise Tracker", 25, Color.WHITE, true);
        heading.addView(title);
        dateText = text("", 13, Color.rgb(219, 234, 254), false);
        LinearLayout.LayoutParams dateLayout = new LinearLayout.LayoutParams(-1, -2);
        dateLayout.topMargin = dp(7);
        heading.addView(dateText, dateLayout);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_notification);
        logo.setPadding(dp(9), dp(9), dp(9), dp(9));
        logo.setBackground(shape(0x24FFFFFF, 16, 0));
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams logoLayout = new LinearLayout.LayoutParams(dp(44), dp(44));
        logoLayout.leftMargin = dp(8);
        header.addView(logo, logoLayout);
        root.addView(header);

        section(root, "How are you feeling?", "A quick check-in for today.");
        String[] feelings = {"😴 Tired", "😐 SoSo", "🙂 Good", "😃 Great"};
        for (int row = 0; row < 2; row++) {
            LinearLayout pair = new LinearLayout(this);
            for (int col = 0; col < 2; col++) {
                String feeling = feelings[row * 2 + col];
                SelectionCard card = new SelectionCard(feeling, null, false);
                card.setOnClickListener(v -> {
                    prefs.edit().putString("feeling_" + dateKey(), feeling).apply();
                    updateScreen();
                });
                feelingCards.put(feeling, card);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
                if (col == 0) params.rightMargin = dp(10);
                pair.addView(card, params);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(10);
            root.addView(pair, params);
        }

        section(root, "How much exercise?", "Every bit of movement counts.");
        addExercise(root, "Minimum", "5 minutes", "Minimum — 5 minutes");
        addExercise(root, "Normal", "20–30 minutes", "Normal — 20–30 minutes");
        addExercise(root, "Bonus", "30+ minutes", "Bonus — 30+ minutes");
        addExercise(root, EXERCISE_NONE, null, EXERCISE_NONE);

        LinearLayout reminder = surface();
        LinearLayout.LayoutParams reminderLayout = new LinearLayout.LayoutParams(-1, -2);
        reminderLayout.topMargin = dp(18);
        root.addView(reminder, reminderLayout);
        reminderText = text("", 16, TEXT, true);
        reminder.addView(reminderText);
        reminderHint = text("", 13, MUTED, false);
        LinearLayout.LayoutParams hintLayout = new LinearLayout.LayoutParams(-1, -2);
        hintLayout.topMargin = dp(8);
        reminder.addView(reminderHint, hintLayout);
        LinearLayout actions = new LinearLayout(this);
        LinearLayout.LayoutParams actionsLayout = new LinearLayout.LayoutParams(-1, -2);
        actionsLayout.topMargin = dp(12);
        reminder.addView(actions, actionsLayout);
        reminderButton = actionButton("", true);
        reminderButton.setOnClickListener(v -> showReminderTimePicker());
        actions.addView(reminderButton, new LinearLayout.LayoutParams(0, -2, 1));
        disableButton = actionButton("Disable", false);
        disableButton.setOnClickListener(v -> {
            ReminderScheduler.disable(this);
            updateReminder();
        });
        LinearLayout.LayoutParams disableLayout = new LinearLayout.LayoutParams(-2, -2);
        disableLayout.leftMargin = dp(8);
        actions.addView(disableButton, disableLayout);
        notificationSettingsButton = actionButton("Allow notifications", false);
        notificationSettingsButton.setOnClickListener(v -> {
            Intent settings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            settings.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(settings);
        });
        reminder.addView(notificationSettingsButton, new LinearLayout.LayoutParams(-1, -2));

        section(root, "30-day history", "Today and the previous 29 days.");
        LinearLayout history = surface();
        root.addView(history);
        completedText = text("", 16, TEXT, true);
        history.addView(completedText);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(30);
        progress.setProgressTintList(ColorStateList.valueOf(BLUE));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(LIGHT_BLUE));
        progress.setProgressDrawable(progressDrawable());
        LinearLayout.LayoutParams progressLayout = new LinearLayout.LayoutParams(-1, dp(5));
        progressLayout.topMargin = dp(12);
        progressLayout.bottomMargin = dp(14);
        history.addView(progress, progressLayout);
        for (int i = 0; i < historyRows.length; i++) {
            TextView line = text("", 13, TEXT, false);
            line.setSingleLine(true);
            line.setHorizontallyScrolling(false);
            // Fit the entire record instead of truncating or scrolling it, including large fonts.
            line.setAutoSizeTextTypeUniformWithConfiguration(8, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(6), dp(8), dp(6), dp(8));
            line.setMinHeight(dp(36));
            if (i % 2 == 0) line.setBackground(shape(BACKGROUND, 6, 0));
            historyRows[i] = line;
            history.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
        setContentView(scroll);
        updateScreen();
    }

    private void addExercise(LinearLayout root, String title, String duration, String storedValue) {
        SelectionCard card = new SelectionCard(title, duration, true);
        exerciseCards.put(storedValue, card);
        card.setOnClickListener(v -> {
            prefs.edit().putString("exercise_" + dateKey(), storedValue).apply();
            updateScreen();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        root.addView(card, params);
    }

    private void updateScreen() {
        if (dateText == null) return;
        dateText.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(new Date()));
        String key = dateKey();
        String feeling = prefs.getString("feeling_" + key, "");
        String exercise = prefs.getString("exercise_" + key, "");
        for (Map.Entry<String, SelectionCard> card : feelingCards.entrySet()) {
            card.getValue().highlight(card.getKey().equals(feeling), false);
        }
        for (Map.Entry<String, SelectionCard> card : exerciseCards.entrySet()) {
            card.getValue().highlight(card.getKey().equals(exercise), !EXERCISE_NONE.equals(card.getKey()));
        }
        Calendar cal = Calendar.getInstance();
        int completed = 0;
        for (int i = 0; i < 30; i++) {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
            String f = prefs.getString("feeling_" + day, "");
            String e = prefs.getString("exercise_" + day, "");
            // Preserve the existing completion calculation, including legacy nonempty values.
            if (!e.isEmpty() && !EXERCISE_NONE.equals(e)) completed++;
            String label = new SimpleDateFormat("dd MMM", Locale.US).format(cal.getTime())
                    + " - " + (f.isEmpty() ? "—" : f) + " - " + formatExerciseForHistory(e);
            historyRows[i].setText(label);
            historyRows[i].setContentDescription(label);
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        completedText.setText(getString(R.string.completion_summary, completed));
        progress.setProgress(completed);
        progress.setContentDescription("Exercise completed on " + completed + " of 30 days");
        updateReminder();
    }

    private String formatExerciseForHistory(String exercise) {
        if (exercise.isEmpty()) return "—";
        if (EXERCISE_NONE.equals(exercise)) return EXERCISE_NONE;
        if (exercise.startsWith("Minimum")) return "5 min";
        if (exercise.startsWith("Normal")) return "25 min";
        if (exercise.startsWith("Bonus")) return "30+ min";
        return exercise;
    }

    private void showReminderTimePicker() {
        int hour = prefs.getInt("reminder_hour", 18);
        int minute = prefs.getInt("reminder_minute", 0);
        TimePickerDialog dialog = new TimePickerDialog(this, (view, h, m) -> {
            prefs.edit().putInt("reminder_hour", h).putInt("reminder_minute", m).apply();
            ReminderScheduler.scheduleNext(this);
            updateReminder();
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }, hour, minute, true);
        dialog.setTitle("Daily exercise reminder");
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateReminder();
    }

    private void updateReminder() {
        boolean enabled = ReminderScheduler.isEnabled(prefs);
        boolean allowed = getSystemService(NotificationManager.class).areNotificationsEnabled();
        reminderText.setText(getString(R.string.reminder_summary, enabled
                ? String.format(Locale.US, "%02d:%02d", prefs.getInt("reminder_hour", -1), prefs.getInt("reminder_minute", -1))
                : "Disabled"));
        reminderHint.setText(enabled
                ? (allowed ? "A little nudge to make time for movement." : "Notifications are off. Allow them in Settings to receive reminders.")
                : "Choose a time that works for you.");
        reminderButton.setText(enabled ? "Change time" : "Enable reminder");
        disableButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        notificationSettingsButton.setVisibility(enabled && !allowed ? View.VISIBLE : View.GONE);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(ReminderScheduler.CHANNEL_ID,
                "Exercise reminders", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Daily reminder to do some intentional movement");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout surface() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(18), dp(16), dp(18));
        card.setBackground(shape(Color.WHITE, 20, 0));
        card.setElevation(dp(2));
        return card;
    }

    private void section(LinearLayout parent, String title, String subtitle) {
        TextView heading = text(title, 19, TEXT, true);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        LinearLayout.LayoutParams headingLayout = new LinearLayout.LayoutParams(-1, -2);
        headingLayout.topMargin = dp(24);
        parent.addView(heading, headingLayout);
        TextView caption = text(subtitle, 13, MUTED, false);
        LinearLayout.LayoutParams captionLayout = new LinearLayout.LayoutParams(-1, -2);
        captionLayout.topMargin = dp(5);
        captionLayout.bottomMargin = dp(14);
        parent.addView(caption, captionLayout);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("tnum");
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private Button actionButton(String title, boolean primary) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? Color.WHITE : MUTED);
        button.setMinHeight(dp(48));
        button.setMinimumWidth(0);
        button.setMinWidth(0);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x242563EB),
                shape(primary ? BLUE : BACKGROUND, 12, 0), null));
        button.setStateListAnimator(null);
        return button;
    }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private android.graphics.drawable.LayerDrawable progressDrawable() {
        android.graphics.drawable.ClipDrawable fill = new android.graphics.drawable.ClipDrawable(
                shape(BLUE, 3, 0), Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable drawable = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{shape(LIGHT_BLUE, 3, 0), fill});
        drawable.setId(0, android.R.id.background);
        drawable.setId(1, android.R.id.progress);
        return drawable;
    }

    private String dateKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class SelectionCard extends LinearLayout {
        private final TextView title;
        private final TextView detail;
        private final TextView indicator;
        private final boolean exercise;
        private final String label;

        SelectionCard(String name, String duration, boolean isExercise) {
            super(MainActivity.this);
            exercise = isExercise;
            label = name + (duration == null ? "" : " — " + duration);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(14), dp(16), dp(12), dp(16));
            setMinimumHeight(dp(isExercise ? 64 : 72));
            setClickable(true);
            setFocusable(true);
            setContentDescription(label);
            setElevation(dp(1));
            LinearLayout words = column();
            words.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            title = text(name, 16, TEXT, true);
            words.addView(title);
            detail = text(duration == null ? "" : duration, 13, MUTED, false);
            if (duration != null) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.topMargin = dp(4);
                words.addView(detail, params);
            }
            addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            indicator = text("", 14, BLUE, true);
            indicator.setGravity(Gravity.CENTER);
            indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams checkLayout = new LinearLayout.LayoutParams(dp(22), dp(22));
            checkLayout.leftMargin = dp(6);
            addView(indicator, checkLayout);
            highlight(false, false);
        }

        void highlight(boolean selected, boolean completed) {
            setSelected(selected);
            int accent = completed ? GREEN : (exercise ? MUTED : BLUE);
            int fill = completed ? Color.rgb(240, 253, 244) : (exercise ? Color.rgb(241, 245, 249) : LIGHT_BLUE);
            setBackground(new RippleDrawable(ColorStateList.valueOf(0x202563EB),
                    shape(selected ? fill : Color.WHITE, 16, selected ? accent : BORDER), null));
            title.setTextColor(selected ? (completed ? Color.rgb(21, 128, 61) : (exercise ? TEXT : DARK_BLUE)) : TEXT);
            indicator.setText(selected ? "✓" : "");
            indicator.setTextColor(Color.WHITE);
            indicator.setBackground(shape(selected ? accent : Color.WHITE, 11, selected ? 0 : BORDER));
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.RadioButton");
            info.setCheckable(true);
            info.setChecked(isSelected());
            info.setContentDescription(label);
        }
    }
}

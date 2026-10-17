# Exercise Tracker

A small, offline Java Android app for a daily feeling and exercise check-in, a
30-day history, and an optional daily reminder. Version **1.3 (4)** uses native
Android views, blue and white selection cards, and a locally bundled dumbbell
launcher icon. No Gradle or third-party Android dependencies are required.

## Compatibility and saved data

- Application ID remains `com.example.exercisetracker`.
- Minimum Android version remains Android 8.0 / API 26, as in the original APK.
- The target SDK is 33 so Android 13+ notification permission can be requested
  when the user enables a reminder. Compilation uses SDK 35.
- SharedPreferences remains `exercise_tracker`, with `feeling_yyyy-MM-dd`,
  `exercise_yyyy-MM-dd`, `reminder_hour`, and `reminder_minute` unchanged.
- Stored feelings and exercise classifications are unchanged. No migration,
  deletion, database replacement, or reset happens during an update.
- Completion retains the existing calculation: any nonempty exercise value
  other than `No exercise` counts as completed within today and the prior 29 days.
- History renders one full, single-line row per date. Text automatically fits
  the available width; the full record is also available to accessibility tools.
- Reminder actions expose **Disable** directly whenever a reminder is enabled.
  Disabling cancels its alarm and visible notification. Reboot, app replacement,
  and clock changes restore the saved schedule. If exact-alarm access is denied,
  reminders use Android's idle-compatible inexact alarm instead of crashing;
  Android can delay that delivery. Notification denial does not block disabling.
  See [Android alarm scheduling documentation](https://developer.android.com/develop/background-work/services/alarms).

## Build and signing

Requires a JDK supporting `javac --release 8`, Android SDK platform 35, build tools
35.0.0, command-line tools (including lint), and `zip`. By default the SDK is at
`$HOME/Android/Sdk`; override it with `ANDROID_SDK_ROOT`.

```sh
./tools/build.sh
```

The build script uses `SIGNING_KEYSTORE` (default: `$HOME/.android/debug.keystore`)
and `SIGNING_KEY_ALIAS` (default: `androiddebugkey`). It prompts for credentials
unless `SIGNING_STORE_PASS` / `SIGNING_KEY_PASS` are supplied through the local
environment. Never put signing passwords or keys in Git.

The original distributed v1.2 APK used an Android debug certificate. This release
intentionally preserves that identity so it can update existing installations.
The expected public certificate SHA-256 is:

```text
4ee722ea894bf72a8739eae8f3bf82ce4628244642ad4b91aa1836e749d1242d
```

The similarly named repository-local `debug.keystore` has a different identity;
it must not be substituted. The build rejects a mismatched certificate and never
creates a new signing key. If the original key is missing, restore it securely
before building an update.

The script compiles resources and Java, runs Android lint, converts classes with
D8, aligns and signs the APK, verifies signatures and certificate identity, and
checks the application ID, version, SDK levels, and launcher resource metadata.
Verified output: `build/release/ExerciseTracker.apk`. Build outputs are ignored
by Git. The script does not overwrite the Desktop distribution automatically.

Only after device/emulator checks pass, publish the verified output locally:

```sh
install -m 644 build/release/ExerciseTracker.apk "$HOME/Desktop/ExerciseTracker.apk.new"
mv "$HOME/Desktop/ExerciseTracker.apk.new" "$HOME/Desktop/ExerciseTracker.apk"
```

## Icons

Adaptive background/foreground layers keep the white dumbbell within Android's
central safe circle. Android 13+ also has a monochrome layer for themed icons.
Rounded-square and circular legacy PNGs cover mdpi through xxxhdpi. Notification
art is a white vector silhouette on transparency. All art is local.

The checked-in legacy PNGs can be regenerated with the development-only Pillow
package and `python3 tools/generate_icons.py`. Pillow is not an app dependency.

## Automated Android checks

`tests/` contains a native instrumentation runner, without JUnit or Gradle
requirements. **Use a disposable emulator**: the seed test replaces its test
application preferences. The runner refuses physical devices.

With an emulator running, set `ANDROID_SERIAL` if it is not `emulator-5554`.
Retain an original v1.2 APK outside Git, then test a real in-place update:

```sh
adb -s emulator-5554 install -r -d /path/to/original-v1.2.apk
./tools/test-android.sh seed
adb -s emulator-5554 install -r build/release/ExerciseTracker.apk
adb -s emulator-5554 shell pm grant com.example.exercisetracker android.permission.POST_NOTIFICATIONS
./tools/test-android.sh verify
./tools/test-android.sh changeTime
adb -s emulator-5554 shell am force-stop com.example.exercisetracker
./tools/test-android.sh restart
```

The seed uses the original app's actual preference file, populating the history,
all classifications, an empty day, a legacy nonempty value, records older than
30 days, and a saved reminder. Verification checks exact preference preservation,
all eight choices, selection/accessibility state, historical records, completed
counts, progress, 30 full single-line rows, touch targets, enable, and disable.
Other modes are `layout`, `restore`, `fallback`, `denied`, `disabled`, and `notification`.
The last mode disables the reminder as its final check.

For compact-screen checks, use a 320dp-wide emulator and run `layout`, including
with a larger system font. To test fallback/denial, change the emulator's
exact-alarm access or notification permission first. For reboot restoration,
launch normally, reboot, and inspect `adb shell dumpsys alarm` **before** opening
the app; then run `restore`. Instrumentation completion itself stops its process,
so launch the app normally when inspecting persistent alarms outside the runner.

Physical-device checks remain necessary for OEM launcher mask/caching behavior,
real battery/Doze reminder timing, notification permission/settings UX, and an
update of the user's actual installed app and records. Never uninstall that app
as part of an update check.

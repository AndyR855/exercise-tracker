#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
tools="$sdk/build-tools/35.0.0"
platform="$sdk/platforms/android-35/android.jar"
serial="${ANDROID_SERIAL:-emulator-5554}"
[[ "$serial" == emulator-* ]] || { echo 'Use a disposable emulator only.' >&2; exit 1; }
adb=("$sdk/platform-tools/adb" -s "$serial")
work="$(mktemp -d "$PWD/build/instrumentation.XXXXXX")"
mkdir -p "$work/classes" "$work/dex"
"$tools/aapt2" link -o "$work/base.apk" -I "$platform" --manifest tests/AndroidManifest.xml
mapfile -t sources < <(find tests/src -name '*.java' -print | sort)
javac --release 8 -encoding UTF-8 -classpath "$platform" -d "$work/classes" "${sources[@]}"
mapfile -t classes < <(find "$work/classes" -name '*.class' -print | sort)
"$tools/d8" --min-api 26 --lib "$platform" --output "$work/dex" "${classes[@]}"
(cd "$work/dex" && zip -q -X "$work/base.apk" classes.dex)
"$tools/zipalign" -f -p 4 "$work/base.apk" "$work/aligned.apk"
signing_args=(--ks "${SIGNING_KEYSTORE:-$HOME/.android/debug.keystore}" --ks-key-alias "${SIGNING_KEY_ALIAS:-androiddebugkey}")
if [[ -n "${SIGNING_STORE_PASS:-}" ]]; then signing_args+=(--ks-pass env:SIGNING_STORE_PASS); fi
if [[ -n "${SIGNING_KEY_PASS:-}" ]]; then signing_args+=(--key-pass env:SIGNING_KEY_PASS); fi
"$tools/apksigner" sign "${signing_args[@]}" --out "$work/tests.apk" "$work/aligned.apk"
"${adb[@]}" install -r "$work/tests.apk"
"${adb[@]}" shell am instrument -w -e mode "${1:-verify}" com.example.exercisetracker.tests/.RegressionInstrumentation | tee "$work/result.txt"
rg -q 'RESULT: PASS' "$work/result.txt"

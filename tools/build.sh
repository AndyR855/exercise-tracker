#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
build_tools="$sdk/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"
platform="$sdk/platforms/android-35/android.jar"
keystore="${SIGNING_KEYSTORE:-$HOME/.android/debug.keystore}"
alias="${SIGNING_KEY_ALIAS:-androiddebugkey}"
# Public certificate of the original v1.2 APK. Never generate a replacement key.
expected_certificate="4ee722ea894bf72a8739eae8f3bf82ce4628244642ad4b91aa1836e749d1242d"

for executable in aapt2 d8 zipalign apksigner; do
    test -x "$build_tools/$executable" || { echo "Missing SDK tool: $executable" >&2; exit 1; }
done
test -f "$keystore" || { echo "Original signing keystore is unavailable: $keystore" >&2; exit 1; }
test -f "$platform" || { echo "Missing Android 35 platform" >&2; exit 1; }
mkdir -p build/release
work="$(mktemp -d "$PWD/build/release/work.XXXXXX")"
mkdir -p "$work/gen" "$work/classes" "$work/dex"

"$build_tools/aapt2" compile --dir app/src/main/res -o "$work/resources.zip"
"$build_tools/aapt2" link -o "$work/base.apk" -I "$platform" \
    --manifest app/src/main/AndroidManifest.xml --java "$work/gen" "$work/resources.zip"
mapfile -t sources < <(find app/src/main/java "$work/gen" -name '*.java' -print | sort)
javac --release 8 -encoding UTF-8 -classpath "$platform" -d "$work/classes" "${sources[@]}"
"$sdk/cmdline-tools/latest/bin/lint" --sdk-home "$sdk" --compile-sdk-version 35 \
    --java-language-level 8 --exitcode --text "$work/lint.txt" \
    --sources app/src/main/java --resources app/src/main/res --classpath "$work/classes" app/src/main
mapfile -t classes < <(find "$work/classes" -name '*.class' -print | sort)
"$build_tools/d8" --release --min-api 26 --lib "$platform" --output "$work/dex" "${classes[@]}"
cp "$work/base.apk" "$work/unsigned.apk"
(cd "$work/dex" && zip -q -X "$work/unsigned.apk" classes.dex)
"$build_tools/zipalign" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"

signing_args=(--ks "$keystore" --ks-key-alias "$alias")
if [[ -n "${SIGNING_STORE_PASS:-}" ]]; then signing_args+=(--ks-pass env:SIGNING_STORE_PASS); fi
if [[ -n "${SIGNING_KEY_PASS:-}" ]]; then signing_args+=(--key-pass env:SIGNING_KEY_PASS); fi
"$build_tools/apksigner" sign "${signing_args[@]}" --out "$work/ExerciseTracker.apk" "$work/aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$work/ExerciseTracker.apk" > "$work/signing-verification.txt"
certificate="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$work/signing-verification.txt")"
[[ "$certificate" == "$expected_certificate" ]] || { echo "Signing identity does not match the original APK; release rejected." >&2; exit 1; }
"$build_tools/zipalign" -c -p 4 "$work/ExerciseTracker.apk"
"$build_tools/aapt" dump badging "$work/ExerciseTracker.apk" > "$work/apk-badging.txt"
python3 - "$work/apk-badging.txt" <<'PY'
import sys
from pathlib import Path
text = Path(sys.argv[1]).read_text()
for required in ["name='com.example.exercisetracker'", "versionCode='4'", "versionName='1.3'", "sdkVersion:'26'", "targetSdkVersion:'33'", "icon='res/"]:
    assert required in text, f"APK metadata mismatch: {required}"
PY
# Only replace the build output after compilation, signature and metadata checks pass.
cp "$work/ExerciseTracker.apk" build/release/ExerciseTracker.apk
cp "$work/signing-verification.txt" build/release/signing-verification.txt
cp "$work/apk-badging.txt" build/release/apk-badging.txt
cp "$work/lint.txt" build/release/lint.txt
echo "Verified v1.3 (4): $PWD/build/release/ExerciseTracker.apk"

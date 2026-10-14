#!/usr/bin/env bash
set -euo pipefail
mkdir -p startup-diagnostics
PACKAGE=com.emh01.transcribeoffline.smoke
APK=app/build/outputs/apk/smoke/app-smoke.apk
trap 'adb logcat -d -v threadtime > startup-diagnostics/logcat.txt 2>&1 || true; adb shell dumpsys activity activities > startup-diagnostics/activities.txt 2>&1 || true' EXIT

test -s "$APK" || { echo "::error::Smoke APK not found: $APK"; exit 1; }
adb install -r "$APK" 2>&1 | tee startup-diagnostics/install.txt
adb logcat -c
adb shell am start -W -n "$PACKAGE/com.emh01.transcribe.MainActivity" 2>&1 | tee startup-diagnostics/start.txt
sleep 12

if ! adb shell pidof "$PACKAGE" > startup-diagnostics/pid.txt; then
  echo "::error::Loquia exited during startup; inspect uploaded Logcat"
  adb logcat -d -v threadtime | grep -E -A 25 'FATAL EXCEPTION|AndroidRuntime|Process: com.emh01.transcribeoffline.smoke' | tail -n 100 || true
  exit 1
fi
echo "Loquia remained running after startup."

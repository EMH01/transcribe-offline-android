#!/usr/bin/env bash
set -euo pipefail
mkdir -p startup-diagnostics

run_case() {
  local label="$1" apk="$2" package="$3"
  local dir="startup-diagnostics/$label"
  mkdir -p "$dir"
  echo "=== Testing $label: $package ==="
  if ! test -s "$apk"; then
    echo "::error::$label: APK missing: $apk"
    echo "APK missing" > "$dir/result.txt"
    return 1
  fi
  if ! adb install -r "$apk" > "$dir/install.txt" 2>&1; then
    echo "::error::$label: install failed"
    cat "$dir/install.txt"
    echo "INSTALL FAILED" > "$dir/result.txt"
    return 1
  fi
  adb logcat -c
  adb shell am force-stop "$package" || true
  adb shell am start -W -n "$package/com.emh01.transcribe.MainActivity" > "$dir/start.txt" 2>&1 || true
  sleep 12
  adb logcat -d -v threadtime > "$dir/logcat.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$dir/activities.txt" 2>&1 || true
  if adb shell pidof "$package" > "$dir/pid.txt"; then
    echo "PASS" > "$dir/result.txt"
    echo "$label: PASS"
    return 0
  else
    echo "CRASH" > "$dir/result.txt"
    echo "::error::$label: app exited during startup"
    grep -E -A 8 'FATAL EXCEPTION|Caused by:|Process: com.emh01' "$dir/logcat.txt" | tail -n 60 || true
    return 1
  fi
}

optimized=0
plain=0
run_case "optimized" "app/build/outputs/apk/smoke/app-smoke.apk" "com.emh01.transcribeoffline.smoke" || optimized=1
run_case "unoptimized" "app/build/outputs/apk/smokePlain/app-smokePlain.apk" "com.emh01.transcribeoffline.smokeplain" || plain=1

echo "=== Summary ==="
cat startup-diagnostics/{optimized,unoptimized}/result.txt
if [ "$optimized" -ne 0 ] || [ "$plain" -ne 0 ]; then
  echo "::error::At least one startup variant failed; review per-variant Logcat artifacts"
  exit 1
fi

#!/usr/bin/env bash
# Scripted bench on a connected phone: ./tools/bench.sh [seconds] [mode...]
# Each run force-restarts the bench build, auto-scrolls, and prints its VitreousBench line.
set -uo pipefail
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
PKG=io.github.zainbits.vitreous.sample.bench
SECS=${1:-10}; shift || true
MODES=${*:-none solid noblur full}
EXTRA=${EXTRA:-0}; CLARITY=${CLARITY:-0.35}; SPEED=${SPEED:-2400}; REPEAT=${REPEAT:-1}; DS=${DS:-0}; BLUR=${BLUR:-1}
# Keep the screen on during the runs, then put the owner's timeout back.
OLD_TIMEOUT=$(adb shell settings get system screen_off_timeout | tr -d '\r')
trap 'adb shell settings put system screen_off_timeout "$OLD_TIMEOUT" >/dev/null' EXIT
adb shell settings put system screen_off_timeout 600000 >/dev/null
for r in $(seq "$REPEAT"); do
  for m in $MODES; do
    adb logcat -c
    adb shell am start -S -n "$PKG/io.github.zainbits.vitreous.sample.MainActivity" \
      --es mode "$m" --ef clarity "$CLARITY" --ei extra "$EXTRA" --ei bench "$SECS" --ei speed "$SPEED" --ei ds "$DS" --ei blur "$BLUR" >/dev/null
    for _ in $(seq $((SECS + 15))); do
      line=$(adb logcat -d -s VitreousBench:I | grep VitreousBench | tail -1)
      [[ -n "$line" ]] && break
      sleep 1
    done
    echo "blur=$BLUR ds=$DS ${line#*VitreousBench: }"
  done
done

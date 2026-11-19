#!/usr/bin/env bash
# GPU busy time of the bench app (driver gpu_work_period) during an auto-scroll run.
# Usage: ./tools/gputrace.sh MODE [extra intent args...]   e.g. ./tools/gputrace.sh full --ei ds 2
set -uo pipefail
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
HERE="$(cd "$(dirname "$0")" && pwd)"
PKG=io.github.zainbits.vitreous.sample.bench
MODE=${1:-full}; shift || true
cat > /tmp/vit-gpucfg.txt <<CFG
buffers { size_kb: 16384 fill_policy: RING_BUFFER }
data_sources { config { name: "linux.ftrace" ftrace_config { ftrace_events: "power/gpu_work_period" ftrace_events: "power/gpu_frequency" } } }
duration_ms: 6000
CFG
adb push /tmp/vit-gpucfg.txt /data/local/tmp/vit-gpucfg.txt >/dev/null
adb shell am start -S -n "$PKG/io.github.zainbits.vitreous.sample.MainActivity" --es mode "$MODE" --ei bench 12 "$@" >/dev/null
sleep 4
adb shell "cat /data/local/tmp/vit-gpucfg.txt | perfetto --txt -c - -o /data/misc/perfetto-traces/vg.pftrace" >/dev/null 2>&1
adb pull /data/misc/perfetto-traces/vg.pftrace /tmp/vg.pftrace >/dev/null
UID_=$(adb shell pm list packages -U --user 0 "$PKG" | grep -m1 -oE 'uid:[0-9]+' | cut -d: -f2)
# Needs a Python with the perfetto package (pip install perfetto); set VITREOUS_TRACE_PYTHON to use another one.
"${VITREOUS_TRACE_PYTHON:-python3}" "$HERE/gpuwork.py" /tmp/vg.pftrace "$UID_" "$MODE $*"

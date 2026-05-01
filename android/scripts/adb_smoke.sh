#!/usr/bin/env bash
# android/scripts/adb_smoke.sh
#
# ADB-driven smoke test for the Dictate Android app.
# Requires: adb on PATH, a connected emulator or device, and a working Android SDK.
#
# Usage:
#   bash android/scripts/adb_smoke.sh          # run from repo root
#   cd android && bash scripts/adb_smoke.sh    # run from android/ subdirectory
#
# The script intentionally does NOT test the audio path (see spec: audio injection
# into the emulator microphone is out of scope for this smoke test).

set -euo pipefail

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
PASS=0
FAIL=0

step() {
    echo ""
    echo "==> Step $1: $2"
}

fail() {
    echo ""
    echo "FAIL: $1"
    FAIL=$((FAIL + 1))
    echo ""
    echo "============================================================"
    echo " RESULT: FAIL ($FAIL failure(s))"
    echo "============================================================"
    exit 1
}

# ---------------------------------------------------------------------------
# Locate the android/ directory regardless of where the script is invoked from
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# ---------------------------------------------------------------------------
# Step 1: Wait for a connected device
# ---------------------------------------------------------------------------
step 1 "Waiting for a connected device (adb wait-for-device)"
adb wait-for-device
echo "Device is ready."

# ---------------------------------------------------------------------------
# Step 2: Build and install the debug APK
# ---------------------------------------------------------------------------
step 2 "Building and installing debug APK (./gradlew installDebug)"
cd "$ANDROID_DIR"
./gradlew installDebug
echo "APK installed."

# ---------------------------------------------------------------------------
# Step 3: Grant RECORD_AUDIO permission
# ---------------------------------------------------------------------------
step 3 "Granting RECORD_AUDIO permission"
adb shell pm grant ru.er_log.dictate android.permission.RECORD_AUDIO
echo "RECORD_AUDIO granted."

# ---------------------------------------------------------------------------
# Step 4: Allow SYSTEM_ALERT_WINDOW via appops
# ---------------------------------------------------------------------------
step 4 "Allowing SYSTEM_ALERT_WINDOW via appops"
adb shell appops set ru.er_log.dictate SYSTEM_ALERT_WINDOW allow
echo "SYSTEM_ALERT_WINDOW allowed."

# ---------------------------------------------------------------------------
# Step 5: Enable PasteAccessibilityService
# ---------------------------------------------------------------------------
step 5 "Enabling accessibility service"
adb shell settings put secure enabled_accessibility_services \
    "ru.er_log.dictate/ru.er_log.dictate.core.accessibility.PasteAccessibilityService"
adb shell settings put secure accessibility_enabled 1
echo "Accessibility service enabled."

# ---------------------------------------------------------------------------
# Step 6: Clear logcat
# ---------------------------------------------------------------------------
step 6 "Clearing logcat buffer"
adb logcat -c
echo "Logcat cleared."

# ---------------------------------------------------------------------------
# Step 7: Launch the main activity
# ---------------------------------------------------------------------------
step 7 "Launching MainActivity"
adb shell am start -n "ru.er_log.dictate/.MainActivity"
echo "MainActivity started. Sleeping 3 s..."
sleep 3

# ---------------------------------------------------------------------------
# Step 8: Crash check after launch
# ---------------------------------------------------------------------------
step 8 "Checking for crashes after launch"
CRASH_OUTPUT="$(adb logcat -d -b crash 2>&1 | grep -i "ru.er_log.dictate" || true)"
if [ -n "$CRASH_OUTPUT" ]; then
    echo "Crash detected:"
    echo "$CRASH_OUTPUT"
    fail "App crashed on launch (step 8)"
fi
echo "No crashes detected."

# ---------------------------------------------------------------------------
# Step 9: Start FloatingButtonService (overlay)
# ---------------------------------------------------------------------------
step 9 "Starting FloatingButtonService (overlay)"
adb shell am start-foreground-service -n \
    "ru.er_log.dictate/.core.overlay.FloatingButtonService"
echo "FloatingButtonService started. Sleeping 2 s..."
sleep 2

# ---------------------------------------------------------------------------
# Step 10: Confirm overlay window is present
# ---------------------------------------------------------------------------
step 10 "Confirming overlay window is present in dumpsys"
WINDOWS_DUMP="$(adb shell dumpsys window windows)"
OVERLAY_PRESENT="$(echo "$WINDOWS_DUMP" | grep -iE "FloatingButton|OverlayView" || true)"
if [ -z "$OVERLAY_PRESENT" ]; then
    fail "Overlay window not found in dumpsys window windows (step 10)"
fi
echo "Overlay window confirmed:"
echo "$OVERLAY_PRESENT"

# ---------------------------------------------------------------------------
# Step 11: Discover overlay tap coordinates
# ---------------------------------------------------------------------------
step 11 "Discovering overlay button coordinates"

# Parse the Frame: line that immediately follows a FloatingButton window entry.
# dumpsys window windows typically has lines like:
#   Window #N Window{...} ru.er_log.dictate/...FloatingButtonService:
#     ...
#     Frame: [<left>,<top>][<right>,<bottom>]
#
# Attempt to extract the rect for FloatingButton-related windows.
FRAME_LINE="$(echo "$WINDOWS_DUMP" | \
    awk '/FloatingButton|OverlayView/{found=1} found && /Frame:/{print; exit}' || true)"

TAP_X=""
TAP_Y=""

if [ -n "$FRAME_LINE" ]; then
    # Frame: [left,top][right,bottom]
    # Extract numbers with a basic regex approach compatible with bash+grep
    LEFT="$(echo "$FRAME_LINE"  | grep -oE '\[[-0-9]+,' | head -1 | tr -d '[,')"
    TOP="$(echo "$FRAME_LINE"   | grep -oE ',[0-9]+\]'  | head -1 | tr -d ',]')"
    RIGHT="$(echo "$FRAME_LINE" | grep -oE '\[[-0-9]+,' | tail -1 | tr -d '[,')"
    BOTTOM="$(echo "$FRAME_LINE"| grep -oE ',[0-9]+\]'  | tail -1 | tr -d ',]')"

    if [ -n "$LEFT" ] && [ -n "$TOP" ] && [ -n "$RIGHT" ] && [ -n "$BOTTOM" ]; then
        TAP_X=$(( (LEFT + RIGHT)  / 2 ))
        TAP_Y=$(( (TOP  + BOTTOM) / 2 ))
        echo "Parsed frame: left=$LEFT top=$TOP right=$RIGHT bottom=$BOTTOM"
        echo "Computed tap coordinates: x=$TAP_X y=$TAP_Y"
    fi
fi

if [ -z "$TAP_X" ] || [ -z "$TAP_Y" ]; then
    # Fall back to a sensible default: center-right of a typical 1080x1920 screen
    SCREEN_SIZE="$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' || echo "1080x1920")"
    SCREEN_W="$(echo "$SCREEN_SIZE" | cut -dx -f1)"
    SCREEN_H="$(echo "$SCREEN_SIZE" | cut -dx -f2)"
    TAP_X=$(( SCREEN_W - 100 ))
    TAP_Y=$(( SCREEN_H / 2 ))
    echo "Could not parse frame rect; falling back to default: x=$TAP_X y=$TAP_Y"
fi

# ---------------------------------------------------------------------------
# Step 12: Simulate a tap on the overlay button + crash check
# ---------------------------------------------------------------------------
step 12 "Simulating tap at ($TAP_X, $TAP_Y) and checking for crashes"
adb shell input tap "$TAP_X" "$TAP_Y"
echo "Tap sent. Sleeping 1 s..."
sleep 1

CRASH_OUTPUT="$(adb logcat -d -b crash 2>&1 | grep -i "ru.er_log.dictate" || true)"
if [ -n "$CRASH_OUTPUT" ]; then
    echo "Crash detected after tap:"
    echo "$CRASH_OUTPUT"
    fail "App crashed after tap (step 12)"
fi
echo "No crashes detected after tap."

# ---------------------------------------------------------------------------
# Step 13: Summary
# ---------------------------------------------------------------------------
echo ""
echo "============================================================"
echo " RESULT: PASS — all smoke-test steps completed successfully."
echo "============================================================"

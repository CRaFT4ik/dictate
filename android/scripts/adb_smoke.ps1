# android/scripts/adb_smoke.ps1
#
# ADB-driven smoke test for the Dictate Android app (native Windows PowerShell).
# Requires: adb on PATH, a connected emulator or device, and a working Android SDK.
#
# Usage (from repo root):
#   .\android\scripts\adb_smoke.ps1
#
# The script intentionally does NOT test the audio path (see spec: audio injection
# into the emulator microphone is out of scope for this smoke test).

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
$global:StepFailed = $false

function Write-Step {
    param([int]$Number, [string]$Description)
    Write-Host ""
    Write-Host "==> Step ${Number}: ${Description}"
}

function Fail-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "FAIL: $Message"
    Write-Host ""
    Write-Host "============================================================"
    Write-Host " RESULT: FAIL"
    Write-Host "============================================================"
    exit 1
}

# ---------------------------------------------------------------------------
# Locate the android/ directory regardless of where the script is invoked from
# ---------------------------------------------------------------------------
$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$AndroidDir = Split-Path -Parent $ScriptDir

# ---------------------------------------------------------------------------
# Step 1: Wait for a connected device
# ---------------------------------------------------------------------------
Write-Step 1 "Waiting for a connected device (adb wait-for-device)"
adb wait-for-device
Write-Host "Device is ready."

# ---------------------------------------------------------------------------
# Step 2: Build and install the debug APK
# ---------------------------------------------------------------------------
Write-Step 2 "Building and installing debug APK (gradlew.bat installDebug)"
Push-Location $AndroidDir
try {
    & .\gradlew.bat installDebug
    if ($LASTEXITCODE -ne 0) { Fail-Step "gradlew.bat installDebug failed (exit code $LASTEXITCODE)" }
} finally {
    Pop-Location
}
Write-Host "APK installed."

# ---------------------------------------------------------------------------
# Step 3: Grant RECORD_AUDIO permission
# ---------------------------------------------------------------------------
Write-Step 3 "Granting RECORD_AUDIO permission"
adb shell pm grant ru.er_log.dictate android.permission.RECORD_AUDIO
Write-Host "RECORD_AUDIO granted."

# ---------------------------------------------------------------------------
# Step 4: Allow SYSTEM_ALERT_WINDOW via appops
# ---------------------------------------------------------------------------
Write-Step 4 "Allowing SYSTEM_ALERT_WINDOW via appops"
adb shell appops set ru.er_log.dictate SYSTEM_ALERT_WINDOW allow
Write-Host "SYSTEM_ALERT_WINDOW allowed."

# ---------------------------------------------------------------------------
# Step 5: Enable PasteAccessibilityService
# ---------------------------------------------------------------------------
Write-Step 5 "Enabling accessibility service"
adb shell settings put secure enabled_accessibility_services `
    "ru.er_log.dictate/ru.er_log.dictate.core.accessibility.PasteAccessibilityService"
adb shell settings put secure accessibility_enabled 1
Write-Host "Accessibility service enabled."

# ---------------------------------------------------------------------------
# Step 6: Clear logcat
# ---------------------------------------------------------------------------
Write-Step 6 "Clearing logcat buffer"
adb logcat -c
Write-Host "Logcat cleared."

# ---------------------------------------------------------------------------
# Step 7: Launch the main activity
# ---------------------------------------------------------------------------
Write-Step 7 "Launching MainActivity"
adb shell am start -n "ru.er_log.dictate/.MainActivity"
Write-Host "MainActivity started. Sleeping 3 s..."
Start-Sleep -Seconds 3

# ---------------------------------------------------------------------------
# Step 8: Crash check after launch
# ---------------------------------------------------------------------------
Write-Step 8 "Checking for crashes after launch"
$CrashOutput = adb logcat -d -b crash 2>&1 | Select-String -Pattern "ru.er_log.dictate" -CaseSensitive:$false
if ($CrashOutput) {
    Write-Host "Crash detected:"
    Write-Host $CrashOutput
    Fail-Step "App crashed on launch (step 8)"
}
Write-Host "No crashes detected."

# ---------------------------------------------------------------------------
# Step 9: Start FloatingButtonService (overlay)
# ---------------------------------------------------------------------------
Write-Step 9 "Starting FloatingButtonService (overlay)"
adb shell am start-foreground-service -n `
    "ru.er_log.dictate/.core.overlay.FloatingButtonService"
Write-Host "FloatingButtonService started. Sleeping 2 s..."
Start-Sleep -Seconds 2

# ---------------------------------------------------------------------------
# Step 10: Confirm overlay window is present
# ---------------------------------------------------------------------------
Write-Step 10 "Confirming overlay window is present in dumpsys"
$WindowsDump   = adb shell dumpsys window windows
$OverlayLine   = $WindowsDump | Select-String -Pattern "FloatingButton|OverlayView" -CaseSensitive:$false
if (-not $OverlayLine) {
    Fail-Step "Overlay window not found in dumpsys window windows (step 10)"
}
Write-Host "Overlay window confirmed:"
Write-Host $OverlayLine

# ---------------------------------------------------------------------------
# Step 11: Discover overlay tap coordinates
# ---------------------------------------------------------------------------
Write-Step 11 "Discovering overlay button coordinates"

# Parse the Frame: line that follows a FloatingButton window entry.
# dumpsys window windows typically has lines like:
#   Window #N Window{...} ru.er_log.dictate/...FloatingButtonService:
#     ...
#     Frame: [left,top][right,bottom]
$TapX = $null
$TapY = $null

$Lines = $WindowsDump -split "`n"
$FoundOverlay = $false
foreach ($Line in $Lines) {
    if ($Line -match "FloatingButton|OverlayView") {
        $FoundOverlay = $true
    }
    if ($FoundOverlay -and $Line -match "Frame:\s*\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]") {
        $Left   = [int]$Matches[1]
        $Top    = [int]$Matches[2]
        $Right  = [int]$Matches[3]
        $Bottom = [int]$Matches[4]
        $TapX   = [int](($Left + $Right)  / 2)
        $TapY   = [int](($Top  + $Bottom) / 2)
        Write-Host "Parsed frame: left=$Left top=$Top right=$Right bottom=$Bottom"
        Write-Host "Computed tap coordinates: x=$TapX y=$TapY"
        break
    }
}

if ($null -eq $TapX -or $null -eq $TapY) {
    # Fall back to a sensible default: center-right of a typical 1080x1920 screen
    $ScreenSizeLine = (adb shell wm size) -join ""
    if ($ScreenSizeLine -match "(\d+)x(\d+)") {
        $ScreenW = [int]$Matches[1]
        $ScreenH = [int]$Matches[2]
    } else {
        $ScreenW = 1080
        $ScreenH = 1920
    }
    $TapX = $ScreenW - 100
    $TapY = [int]($ScreenH / 2)
    Write-Host "Could not parse frame rect; falling back to default: x=$TapX y=$TapY"
}

# ---------------------------------------------------------------------------
# Step 12: Simulate a tap on the overlay button + crash check
# ---------------------------------------------------------------------------
Write-Step 12 "Simulating tap at ($TapX, $TapY) and checking for crashes"
adb shell input tap $TapX $TapY
Write-Host "Tap sent. Sleeping 1 s..."
Start-Sleep -Seconds 1

$CrashOutput = adb logcat -d -b crash 2>&1 | Select-String -Pattern "ru.er_log.dictate" -CaseSensitive:$false
if ($CrashOutput) {
    Write-Host "Crash detected after tap:"
    Write-Host $CrashOutput
    Fail-Step "App crashed after tap (step 12)"
}
Write-Host "No crashes detected after tap."

# ---------------------------------------------------------------------------
# Step 13: Summary
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "============================================================"
Write-Host " RESULT: PASS -- all smoke-test steps completed successfully."
Write-Host "============================================================"

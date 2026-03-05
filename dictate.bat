@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion

:: Get GPU name via PowerShell (CIM/WMI). Prefer non-Microsoft Basic Display Adapter.
for /f "usebackq delims=" %%G in (`powershell -NoProfile -ExecutionPolicy Bypass ^
  "(Get-CimInstance Win32_VideoController | Where-Object { $_.Name -and $_.Name -notmatch 'Microsoft Basic Display Adapter' } | Select-Object -First 1 -ExpandProperty Name)"`) do (
  set "GPU_NAME=%%G"
)

:: Fallback if nothing was found
if not defined GPU_NAME set "GPU_NAME=unknown GPU"

:: Admin rights check
net session >nul 2>&1
if %errorLevel% == 0 (
    echo Starting voice dictation on: %GPU_NAME%...
    python "%~dp0dictate.py"
) else (
    echo This program requires administrator privileges to intercept keyboard input.
    echo Right-click this file and select "Run as administrator".
    pause
)

endlocal
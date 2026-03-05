#!/usr/bin/env bash
set -euo pipefail

# Detect GPU name (Linux)
GPU_NAME="unknown GPU"

if command -v lspci >/dev/null 2>&1; then
  # Prefer 3D controller / VGA controller lines
  GPU_NAME="$(lspci | awk -F': ' '
    /VGA compatible controller|3D controller/ { print $2; exit }
  ')"
elif command -v glxinfo >/dev/null 2>&1; then
  # Requires mesa-utils package
  GPU_NAME="$(glxinfo -B 2>/dev/null | awk -F': ' '/Device:/ { print $2; exit }')"
fi

# If empty for some reason
GPU_NAME="${GPU_NAME:-unknown GPU}"

echo "Starting voice dictation on: ${GPU_NAME}..."
python3 "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/dictate.py"
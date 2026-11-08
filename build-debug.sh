#!/usr/bin/env bash
set -euo pipefail
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle 8.9 is required. Install Gradle or open this project in Android Studio." >&2
  exit 1
fi
gradle :app:assembleDebug

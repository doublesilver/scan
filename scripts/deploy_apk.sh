#!/bin/bash
# APK 빌드 + 설치
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ANDROID_DIR="$PROJECT_DIR/android"

echo "=== APK 빌드 + 설치 ==="

cd "$ANDROID_DIR"

# 빌드
echo "[1/3] Debug APK 빌드..."
./gradlew assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "APK 빌드 실패: $APK_PATH 없음"
    exit 1
fi
echo "[2/3] 빌드 완료: $APK_PATH"

# 설치
echo "[3/3] ADB 설치..."
adb install -r "$APK_PATH"
echo "설치 완료"

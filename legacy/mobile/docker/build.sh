#!/bin/bash
set -e

echo "==> Installing dependencies..."
npm install

echo "==> Running expo prebuild..."
npx expo prebuild --platform android --clean

echo "==> Accepting SDK licenses and installing missing components..."
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 || true
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0" > /dev/null 2>&1 || true

echo "==> Building APK..."
cd android
./gradlew assembleRelease --no-daemon

APK=$(find app/build/outputs/apk/release -name "*.apk" | head -1)
if [ -z "$APK" ]; then
  echo "ERROR: APK not found"
  exit 1
fi

cp "$APK" /output/memo-release.apk
echo ""
echo "==> Done! APK: /output/memo-release.apk"

#!/bin/sh
# Install the pinned Android toolchain in a Linux cloud workspace.
set -eu
cd "$(dirname "$0")/.."
POCKET_ROOT="$PWD"
export ANDROID_HOME="${ANDROID_HOME:-$POCKET_ROOT/.tooling/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p "$ANDROID_HOME" .tooling
if [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    POCKET_SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
elif command -v sdkmanager >/dev/null 2>&1; then
    POCKET_SDKMANAGER="$(command -v sdkmanager)"
else
    echo 'Android command-line tools are required. Install them from developer.android.com/studio#command-tools or use the Android APK workflow.' >&2
    exit 1
fi
# yes exits with SIGPIPE once sdkmanager finishes; only sdkmanager's result matters.
yes | "$POCKET_SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
"$POCKET_SDKMANAGER" --sdk_root="$ANDROID_HOME" \
    'platform-tools' 'platforms;android-36' 'build-tools;35.0.0' \
    'ndk;30.0.16248370' 'cmake;3.31.6'
printf 'Android SDK ready: %s\n' "$ANDROID_HOME"

#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
POCKET_ROOT="$PWD"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$POCKET_ROOT/.tooling/gradle-home}"
python3 scripts/check_model_catalog.py
mkdir -p .tooling app/src/main/jniLibs/arm64-v8a releases verification
POCKET_NDK="$ANDROID_HOME/ndk/30.0.16248370"
POCKET_CMAKE="$ANDROID_HOME/cmake/3.31.6/bin/cmake"
POCKET_NINJA="$ANDROID_HOME/cmake/3.31.6/bin/ninja"
for POCKET_ENGINE in llm; do
    "$POCKET_CMAKE" -S "native/$POCKET_ENGINE" -B ".tooling/native-$POCKET_ENGINE" -G Ninja \
        -DCMAKE_MAKE_PROGRAM="$POCKET_NINJA" -DCMAKE_TOOLCHAIN_FILE="$POCKET_NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fvisibility=hidden -DCMAKE_CXX_FLAGS=-fvisibility=hidden
    "$POCKET_CMAKE" --build ".tooling/native-$POCKET_ENGINE" --parallel 2
done
cp .tooling/native-llm/bin/libpocketmemory.so app/src/main/jniLibs/arm64-v8a/
if [ "${POCKET_GRADLE_OFFLINE:-0}" = 1 ]; then
    ./gradlew --offline --no-daemon assembleDebug assembleDebugAndroidTest lintDebug
else
    ./gradlew --no-daemon assembleDebug assembleDebugAndroidTest lintDebug
fi
cp app/build/outputs/apk/debug/app-debug.apk releases/PocketMemory-Plans-0.6.0.apk
python3 scripts/check_native_alignment.py releases/PocketMemory-Plans-0.6.0.apk
python3 - <<'PY'
import hashlib,pathlib
p=pathlib.Path('releases/PocketMemory-Plans-0.6.0.apk')
(p.with_suffix('.apk.sha256')).write_text(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')
PY
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify releases/PocketMemory-Plans-0.6.0.apk
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 4 releases/PocketMemory-Plans-0.6.0.apk

#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Run the same instrumentation suite directly: Gradle uninstalls the target app
# afterwards, deleting its app-specific screenshots before they can be pulled.
run_profile() {
    local profile_name="$1"
    adb shell am instrument -w -e profile "$profile_name" com.mostadiag.app.test/androidx.test.runner.AndroidJUnitRunner | tee "instrumentation-${profile_name}.txt"
    adb pull /sdcard/Android/data/com.mostadiag.app/files/screenshots/. screenshots/
    grep -q 'OK (7 tests)' "instrumentation-${profile_name}.txt"
}
run_profile phone
adb shell wm size 640x1136
adb shell wm density 320
run_profile compact
adb shell wm size 1600x2560
adb shell wm density 320
run_profile tablet
adb shell wm size 1080x1920
adb shell wm density 420
adb shell settings put system font_scale 1.5
run_profile large_text


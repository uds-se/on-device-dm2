echo "Usage: ./launch.sh <APK_DIR> <OUT_DIR> [<NUM_RUNS> (default 1]"

DEFAULT_SLEEP=5
APK_DIR=$1
OUT_DIR=$2
NUM_RUNS=$3 || 1
APP=$(ls -a $APK_DIR/*.apk | sort | head -1)
DM=../app/build/outputs/apk/release/app-release.apk

./uninstall_apk.sh "$APP" || true

./uninstall_apk.sh $DM || true

sleep $DEFAULT_SLEEP

# Push config
adb push defaultConfig.properties /sdcard/

echo "Creating output dir $OUT_DIR"
mkdir $OUT_DIR

#!/bin/bash
for ((i=0; i<NUM_RUNS; i++))
do
    echo "Removing old DM-2 files on device"
    adb shell rm -rf /sdcard/DM-2

    adb install "$APP"
    adb install $DM

    # Force some window updates to prevent errors when launching the app
    echo "Launching search"
    adb shell input keyevent 84
    sleep $DEFAULT_SLEEP

    echo "Pressing home"
    adb shell input keyevent 3
    sleep $DEFAULT_SLEEP

    # Launch app
    echo "Launching app"
    adb shell am start -n "org.droidmate.accessibility/org.droidmate.accessibility.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -D
    sleep $DEFAULT_SLEEP

    echo "Click runtime permission \"allow\" [691,1017][876,1143]"
    adb shell input tap 700 1050
    sleep $DEFAULT_SLEEP

    echo "Click \"start now\" for screen recording [711,1005][978,1131]"
    adb shell input tap 750 1100
    sleep $DEFAULT_SLEEP

    # Click on app
    echo "Click on app"
    adb shell input tap 500 300
    sleep $DEFAULT_SLEEP

    echo "Click on accessibility in the menu"
    adb shell input tap 500 600
    sleep $DEFAULT_SLEEP

    echo "Click on toggle to activate app"
    adb shell input tap 1000 300
    sleep $DEFAULT_SLEEP

    echo "Click on ok to start accessibility [810,1258][978,1384]"
    adb shell input tap 850 1300

    # Wait exploration to complete
    TOTAL_TIME=0
    while : ; do
        sleep 30
        # Break if the "exploration done" signal file is there
        RESULT=$(adb shell "[ -f /sdcard/DM-2/exploration.done ] || echo 1")
        [[ $RESULT == 1 ]] || break
        # Break after 3h
        if ((TOTAL_TIME > 10800)); then break; fi
        echo "Waiting DM-2 to finish";
    done

    echo "Click on toggle to deactivate app"
    adb shell input tap 1000 300
    sleep $DEFAULT_SLEEP

    echo "Click on ok to stop accessibility [810,998][978,1124]"
    adb shell input tap 850 1000
    sleep $DEFAULT_SLEEP

    echo "Press back"
    adb shell input keyevent 4
    sleep $DEFAULT_SLEEP

    echo "Press back"
    adb shell input keyevent 4
    sleep $DEFAULT_SLEEP

    echo "Press back"
    adb shell input keyevent 4
    sleep $DEFAULT_SLEEP

    echo "Press back"
    adb shell input keyevent 4
    sleep $DEFAULT_SLEEP

    ./uninstall_apk.sh $DM || true

    ./uninstall_apk.sh "$APP" || true

    echo "Pulling results"
    adb pull /sdcard/DM-2/ $OUT_DIR/$i

done

echo "Done"
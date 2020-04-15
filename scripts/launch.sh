echo "Usage: ./launch.sh <APK_DIR> <OUT_DIR> [<DM_APK>] [<NUM_RUNS> (default 1)]"

DEFAULT_SLEEP=30
APK_DIR=/test/experiment/apks
OUT_DIR=/test/experiment/out
NUM_RUNS=10
DM=/test/app-release.apk
DMLAUNCHER_BASE=/test/launcher.apk
DMLAUNCHER=/test/launcher-test.apk
APP=$(ls -a $APK_DIR/*.apk | sort | head -1)

sleep $DEFAULT_SLEEP

echo "Creating output dir $OUT_DIR"
mkdir $OUT_DIR

#!/bin/bash
for ((i=0; i<NUM_RUNS; i++))
do
    echo "Stopping emulator"
    /test/stopEmu.sh

    echo "Starting the emulator"
    /test/startEmu.sh

    /test/uninstall_apk.sh "$APP" || true

    /test/uninstall_apk.sh $DM || true

    echo "Removing old DM-2 files on device"
    adb shell rm -rf /sdcard/DM-2 || true

    # Push config
    adb push defaultConfig.properties /sdcard/

    adb install "$APP"
    adb install $DM
    adb install $DMLAUNCHER_BASE
    adb install $DMLAUNCHER

    # Launch app
    echo "Launching app"
    adb shell am instrument -w -r -e class 'com.example.dm2launcher.StartDMTest' com.example.dm2launcher.test/androidx.test.runner.AndroidJUnitRunner
    sleep $DEFAULT_SLEEP

    # Wait exploration to complete
    TOTAL_TIME=0
    while : ; do
        sleep 30
        # Break if the "exploration done" signal file is there
        RESULT=$(adb shell "[ -f /sdcard/DM-2/exploration.done ] || echo 1")
        [[ $RESULT == 1 ]] || break
        # Break after 3h
        TOTAL_TIME=$((TOTAL_TIME+30))
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

    /test/uninstall_apk.sh $DM || true
    /test/uninstall_apk.sh "$APP" || true
    /test/uninstall_apk.sh $DMLAUNCHER_BASE || true
    /test/uninstall_apk.sh $DMLAUNCHER || true

    echo "Pulling results"
    adb pull /sdcard/DM-2/ $OUT_DIR/$i

    chmod 777 $OUT_DIR/$i
done

chmod -R 777 $OUT_DIR
echo "Done"
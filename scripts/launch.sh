echo "Usage: ./launch.sh <APK_DIR> <OUT_DIR>"

APK_DIR=$1
OUT_DIR=$2
APP=$(ls -a $APK_DIR/*.apk | sort | head -1)
DM=../app/build/outputs/apk/release/app-release.apk

./uninstall_apk.sh "$APP" || true

sleep 1

./uninstall_apk.sh $DM || true

sleep 1

adb install "$APP"

sleep 1

adb install $DM

sleep 1

# Push config
Adb push defaultConfig.properties /sdcard/

# Force some window updates to prevent errors when launching the app
adb shell input keyevent 84

sleep 1

adb shell input keyevent 3

sleep 1

# Launch app
adb shell am start -n "org.droidmate.accessibility/org.droidmate.accessibility.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -D

sleep 1

# Click runtime permission "allow" [691,1017][876,1143]
adb shell input tap 700 1050

sleep 1

# Click "start now" for screen recording [711,1005][978,1131]
adb shell input tap 750 1100

sleep 1

# Click on app
adb shell input tap 500 300

sleep 1

# Click on accessibility in the menu
adb shell input tap 500 600

sleep 1

# Click on toggle to activate app
adb shell input tap 1000 300

sleep 1

# Click on ok to start accessibility [810,1258][978,1384]
adb shell input tap 850 1300

# Wait exploration to complete
while : ; do
    sleep 30
    RESULT=$(adb shell "[ -f /sdcard/DM-2/exploration.done ] || echo 1")
    [[ $RESULT == 1 ]] || break
    echo "File does not exist";
done

adb pull /sdcard/DM-2/ $OUT_DIR/


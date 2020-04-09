PACKAGE=$(aapt dump badging $1 | awk -v FS="'" '/package: name=/{print $2}')
adb uninstall $PACKAGE
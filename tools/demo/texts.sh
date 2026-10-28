#!/bin/bash
# Prints the visible texts on the emulator screen, for scripting the demo walk.
A=~/Library/Android/sdk/platform-tools/adb
$A -s emulator-5554 shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
$A -s emulator-5554 shell cat /sdcard/u.xml | grep -o 'text="[^"]\+"\|content-desc="[^"]\+"' | head -${1:-40} | tr '\n' ' '; echo

#!/bin/bash
# Taps the first on-screen element whose text or description contains $1 (emulator-5554).
# tap.sh "<text substring>" : taps the first node whose text or content-desc contains it
A=~/Library/Android/sdk/platform-tools/adb
$A -s emulator-5554 shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
$A -s emulator-5554 shell cat /sdcard/u.xml | python3 -c '
import sys,re
x=sys.stdin.read(); q=sys.argv[1]
for m in re.finditer(r"<node [^>]*>", x):
    n=m.group(0)
    g=lambda k: (re.search(r"\b"+k+r"=\"([^\"]*)\"",n) or [None,""])[1]
    t=g("text")+"|"+g("content-desc")
    if q.lower() in t.lower():
        b=list(map(int,re.findall(r"\d+",re.search(r"bounds=\"([^\"]*)\"",n).group(1))))
        print((b[0]+b[2])//2,(b[1]+b[3])//2); break
' "$1" | { read X Y; if [ -n "$X" ]; then $A -s emulator-5554 shell input tap $X $Y; echo "tapped $1"; else echo "NOT FOUND $1"; fi; }

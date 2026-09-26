#!/bin/sh
# Installs the app on a running emulator with some sample subscriptions,
# and takes a screenshot of each screen and of the reminder notifications.
# Used by the Mobile workflow; the pictures go to build/screenshots/.
set -e
cd "$(dirname "$0")/.."
PKG=subscriptiontracker.mobile
OUT=build/screenshots
mkdir -p "$OUT"

day() { date -d "$1" +%F; }

adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true

# Sample data, with dates around today so the reminders and "in N days" show.
printf 'BUDGET\t1200.00\nCURRENCY\tR\n' > /tmp/seed.txt
printf '1\tNetflix\t199.00\tMONTHLY\t%s\tEntertainment\tNOTE=Shared with family\tPRICE=%s:169.00:199.00\n' "$(day '+5 days')" "$(day '-6 months')" >> /tmp/seed.txt
printf '2\tSpotify\t79.99\tMONTHLY\t%s\tMusic\n' "$(day '+1 day')" >> /tmp/seed.txt
printf '3\tMicrosoft 365\t1099.00\tYEARLY\t%s\tSoftware\n' "$(day '+111 days')" >> /tmp/seed.txt
printf '4\tDisney+\t99.00\tMONTHLY\t%s\tEntertainment\tTRIAL\n' "$(day '+2 days')" >> /tmp/seed.txt
printf '5\tGym\t450.00\tMONTHLY\t%s\tHealth\n' "$(day '+7 days')" >> /tmp/seed.txt
printf '6\tYouTube Premium\t71.99\tMONTHLY\t%s\tEntertainment\n' "$(day '+14 days')" >> /tmp/seed.txt
printf '7\tShowmax\t99.00\tMONTHLY\t%s\tEntertainment\tCANCELLED=%s\n' "$(day '-100 days')" "$(day '-110 days')" >> /tmp/seed.txt
printf '8\tCoffee club\t40.00\tWEEKLY\t%s\tFood\n' "$(day '+3 days')" >> /tmp/seed.txt
adb push /tmp/seed.txt /data/local/tmp/seed.txt
adb shell "run-as $PKG mkdir -p files"
adb shell "cat /data/local/tmp/seed.txt | run-as $PKG sh -c 'cat > files/subscriptions.txt'"

for screen in subscriptions upcoming spending cancelled add; do
  adb shell am start -S -W -n $PKG/.MainActivity --es screen $screen
  sleep 6
  adb exec-out screencap -p > "$OUT/$screen.png"
done

# The app checks for reminders when it opens: Spotify is due tomorrow, and
# the Disney+ trial ends in 2 days.
adb shell cmd statusbar expand-notifications
sleep 3
adb exec-out screencap -p > "$OUT/notifications.png"
adb shell cmd statusbar collapse
ls -l "$OUT"

# Full Kiosk / Device Owner setup

Warning: Yeh company-owned test phone par karein. Factory reset se phone ka data delete hota hai.

## ADB method

1. Company phone ka zaroori data backup karein aur factory reset karein.
2. Initial setup mein personal Google account add na karein.
3. Developer Options aur USB debugging enable karein.
4. APK install karein:

```text
adb install app-debug.apk
```

5. Device Owner command:

```text
adb shell dpm set-device-owner com.company.kiosk/.KioskAdminReceiver
```

6. App kholen, Admin PIN `2552` dalein, URL save karein aur `START / RE-APPLY KIOSK` dabayein.

## Exit

Launcher par `ADMIN / EXIT KIOSK` -> PIN -> `EXIT KIOSK AND OPEN SETTINGS`.

Exit policies ko temporary remove karta hai. Device Owner component installed rehta hai, is liye admin baad mein kiosk dobara start kar sakta hai.

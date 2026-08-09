# Old Kiosk se Setup 2 par switch

Yeh update sirf existing `com.company.kiosk` Device Owner ko release karne ke liye hai.

1. GitHub Actions me `3 - Owner Release Signed Build` run karein.
2. Artifact `Thramart-Kiosk-v6-owner-release-signed` download karein.
3. Existing old kiosk ke upar same signing key se update install karein:
   `adb install -r app-release.apk`
4. Old kiosk Admin screen kholen.
5. `SWITCH TO SETUP 2 - REMOVE DEVICE OWNER` dabayen aur confirm karein.
6. PC par verify karein: `adb shell dpm list-owners`
7. Jab 0 owner ho, Setup 2 install karein aur phir:
   `adb shell dpm set-device-owner com.thramart.setup2/.KioskAdminReceiver`

IMPORTANT: signing key/password ko repository me commit na karein. GitHub Actions Secrets use karein.

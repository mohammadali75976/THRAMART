# Thramart Kiosk v4 - Dual WhatsApp

## Main changes

- Separate **WHATSAPP NORMAL** button for package `com.whatsapp`.
- Separate **WHATSAPP BUSINESS** button for package `com.whatsapp.w4b`.
- Both packages are included in the Device Owner lock-task allowlist.
- Admin screen reports installation status for both WhatsApp apps.
- Odoo, Phone/Dialer, WA View Once Guard, Camera, Gallery, Calculator and Thramart URL remain available.
- Battery and Wi-Fi/connectivity system information remain visible while notifications and Quick Settings stay restricted.
- Android Settings stays hidden while kiosk is ON; display/brightness/screen-timeout changes are restricted.
- Version: `1.3.0-thramart-dual-wa` (versionCode 4).

## Important signing rule

Use the same release keystore for every future update. Do not use a fresh GitHub debug APK on production phones, because Android will reject updates signed with a different certificate.

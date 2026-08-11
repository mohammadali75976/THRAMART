# Thramart Kiosk — Setup 2

A dedicated-device Android kiosk/DPC launcher for shop phones.

## What Setup 2 does

- Makes **Thramart Kiosk Setup 2** the locked HOME launcher.
- Admin opens **Deep Settings** by tapping `THRAMART • SETUP 2` **7 times**.
- Default admin PIN: **2580** (change it immediately in Deep Settings).
- Lists installed launcher apps (WhatsApp Business, Odoo, etc.).
- Admin can enable/disable each app.
- Admin can set a daily **From / To** time for every enabled app.
- Outside that time the app is removed from Android lock-task allowlist.
- All other apps/system settings remain unavailable through the kiosk UI.
- Boot receiver reapplies the policy after restart.

## Build

Recommended versions used by this project:

- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- Kotlin: 2.2.21
- compileSdk / targetSdk: 36
- minSdk: 28 (Android 9)

Open the project in Android Studio, sync Gradle, then build `app`.

### GitHub automatic APK build

This repo includes `.github/workflows/android-build.yml`.
After you push it to GitHub, open **Actions → Android Build → Run workflow** (or push to `main`).
The workflow builds a debug APK and uploads it as the artifact **thramart-kiosk-setup2-debug**.

## Install and make Device Owner

A dedicated-device DPC must be Device Owner. The phone must have **no normal accounts and no work profile/secondary user** before provisioning.

```bat
adb install -r app-release.apk
adb shell pm list users
adb shell dumpsys account
adb shell dpm set-device-owner com.thramart.setup2/.KioskAdminReceiver
adb shell monkey -p com.thramart.setup2 -c android.intent.category.LAUNCHER 1
```

If `set-device-owner` says accounts/users exist, remove those first (as you did on Setup 1), then retry.

## How to configure

1. Launch the kiosk.
2. Tap **THRAMART • SETUP 2** seven times.
3. Enter PIN `2580`.
4. Tick the apps you want available.
5. Set each app's `From` and `To` time.
6. Tap **SAVE & APPLY KIOSK**.

Example:

- WhatsApp Business: 08:00 → 22:00
- Odoo: 08:00 → 23:00

Overnight schedules are supported too, e.g. `22:00 → 06:00`.

## Important

This project is designed for company-owned dedicated devices. Test on one spare phone first. Device Owner policy is intentionally powerful and can make normal Android navigation/settings unavailable.

## Gradle wrapper note

The source ZIP includes `gradle-wrapper.properties` but not the binary `gradle-wrapper.jar`.
You can either:

- build using the included GitHub Actions workflow, or
- in a machine that already has Gradle 8.13, run `gradle wrapper --gradle-version 8.13`, then commit the generated wrapper files.


## Full V3 changes
- Home button is visible while kiosk remains in lock-task mode.
- System info is visible: Wi-Fi/mobile signal, battery and time.
- Recents and notification access remain disabled.
- UI uses wrap-content/minimum heights so long labels are not cut.
- Deep Settings now has **EXIT KIOSK / REMOVE DEVICE OWNER** with current-PIN confirmation.

> Note: `DevicePolicyManager.clearDeviceOwnerApp()` is intended for testing/recovery use and Android describes cleanup as best-effort. This project explicitly clears the kiosk policies it applies before releasing ownership.

## V4 launch/schedule fix
- Allows selected apps to launch normally before Device Owner provisioning, which makes fresh-phone testing possible.
- Uses lock-task ActivityOptions only when this app is Device Owner and the target app is lock-task permitted.
- Separates launch failures into clear messages: not allowed, outside schedule, not installed/no launcher, or launch failed.
- Keeps the existing schedule behavior, Home/system-info lock-task features, and Exit Kiosk / Remove Device Owner recovery option.

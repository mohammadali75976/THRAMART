# Thramart Dual WhatsApp Kiosk v4

Android Device Owner kiosk launcher plus WA View Once Guard in one GitHub repository.

## App buttons

- WhatsApp Normal (`com.whatsapp`)
- WhatsApp Business (`com.whatsapp.w4b`)
- Odoo
- Phone / incoming-call support
- WA View Once Guard (`com.company.waguard`)
- Camera
- Gallery
- Calculator
- Thramart URL

## Recommended build

Run GitHub Actions workflow **1 - First Signed Build - Save Key** for the first production build. It produces both the Kiosk APK and WA Guard APK with the same saved signing key. Download and privately save all files from the artifact. For future updates, configure repository secrets and run **2 - Future Signed Update**.

## Device Owner command

```bat
adb shell dpm set-device-owner com.company.kiosk/.KioskAdminReceiver
```

Accounts must be removed before this command. Install all required apps before enabling the final kiosk policies.

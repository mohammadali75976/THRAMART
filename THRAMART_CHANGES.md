# Thramart Kiosk changes

- Company name changed to Thramart.
- Battery and Wi-Fi system information remains visible in lock-task mode.
- Quick settings and notifications are not enabled.
- Phone button added. Default dialer and common Samsung/Android in-call packages are allowlisted.
- Incoming call UI packages are included where installed.
- WA View Once Guard package is allowlisted and protected from uninstall.
- Android Settings is hidden while kiosk is active when the OEM permits it.
- Display brightness, screen-timeout and ambient-display changes are restricted while kiosk is active.
- Admin PIN exit restores Settings and clears restrictions.

Important: Android requires the same signing key for an APK update. A new GitHub debug APK cannot update an already-installed APK signed by a lost key.

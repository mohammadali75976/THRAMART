package com.company.kiosk;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.UserManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telecom.TelecomManager;

import java.util.LinkedHashSet;
import java.util.Set;

public final class KioskPolicyManager {
    public static final String WHATSAPP = "com.whatsapp";
    public static final String WHATSAPP_BUSINESS = "com.whatsapp.w4b";
    public static final String GUARD_PACKAGE = "com.company.waguard";
    public static final String SETTINGS_PACKAGE = "com.android.settings";

    private static final String[] PHONE_PACKAGES = new String[] {
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.contacts",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.contacts",
            "com.android.server.telecom",
            "com.android.phone"
    };

    private KioskPolicyManager() {}

    public static ComponentName admin(Context context) {
        return new ComponentName(context, KioskAdminReceiver.class);
    }

    public static DevicePolicyManager dpm(Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static boolean isDeviceOwner(Context context) {
        DevicePolicyManager manager = dpm(context);
        return manager != null && manager.isDeviceOwnerApp(context.getPackageName());
    }

    public static String applyPolicies(Context context) {
        if (!isDeviceOwner(context)) {
            return "Device Owner active nahi hai";
        }

        DevicePolicyManager manager = dpm(context);
        ComponentName admin = admin(context);
        if (manager == null) {
            return "DevicePolicyManager available nahi";
        }

        try {
            Set<String> allowlist = buildAllowlist(context);
            manager.setLockTaskPackages(admin, allowlist.toArray(new String[0]));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // HOME returns staff to the controlled launcher. SYSTEM_INFO shows
                // connectivity/battery and NOTIFICATIONS keeps WhatsApp alerts visible.
                // Quick settings and Overview remain blocked.
                manager.setLockTaskFeatures(
                        admin,
                        DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                                | DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                                | DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
                );
            }

            IntentFilter homeFilter = new IntentFilter(Intent.ACTION_MAIN);
            homeFilter.addCategory(Intent.CATEGORY_HOME);
            homeFilter.addCategory(Intent.CATEGORY_DEFAULT);
            manager.addPersistentPreferredActivity(
                    admin,
                    homeFilter,
                    new ComponentName(context, MainActivity.class)
            );

            // Status information and approved app notifications remain visible.
            safeSetStatusBar(manager, admin, false);
            safeSetKeyguard(manager, admin, true);
            grantRequiredRuntimePermissions(context, manager, admin);
            addRestrictions(manager, admin);
            safeSetApplicationHidden(manager, admin, SETTINGS_PACKAGE, true);
            blockCompanyAppUninstall(context, manager, admin, true);
            AppPrefs.setKioskEnabled(context, true);
            return "Thramart kiosk policies applied";
        } catch (SecurityException | IllegalArgumentException exception) {
            return "Policy error: " + exception.getMessage();
        }
    }

    public static void startLockTaskIfAllowed(Activity activity) {
        if (!isDeviceOwner(activity) || !AppPrefs.isKioskEnabled(activity)) {
            return;
        }
        DevicePolicyManager manager = dpm(activity);
        if (manager != null && manager.isLockTaskPermitted(activity.getPackageName())) {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                try {
                    activity.startLockTask();
                } catch (IllegalStateException | SecurityException ignored) {
                    // Avoid crashes on OEM builds that apply the allowlist asynchronously.
                }
            }
        }
    }

    public static String disableKiosk(Activity activity) {
        if (!isDeviceOwner(activity)) {
            AppPrefs.setKioskEnabled(activity, false);
            return "Device Owner active nahi hai";
        }

        DevicePolicyManager manager = dpm(activity);
        ComponentName admin = admin(activity);
        if (manager == null) {
            return "DevicePolicyManager available nahi";
        }

        try {
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) {
                activity.stopLockTask();
            }
        } catch (IllegalStateException | SecurityException ignored) {
        }

        try {
            manager.clearPackagePersistentPreferredActivities(admin, activity.getPackageName());
            clearRestrictions(manager, admin);
            blockCompanyAppUninstall(activity, manager, admin, false);
            safeSetApplicationHidden(manager, admin, SETTINGS_PACKAGE, false);
            safeSetStatusBar(manager, admin, false);
            safeSetKeyguard(manager, admin, false);
            manager.setLockTaskPackages(admin, new String[0]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            }
            AppPrefs.setKioskEnabled(activity, false);
            return "Kiosk unlocked";
        } catch (SecurityException | IllegalArgumentException exception) {
            return "Unlock error: " + exception.getMessage();
        }
    }

    public static void openAndroidSettings(Activity activity) {
        Intent settings = new Intent(Settings.ACTION_SETTINGS);
        settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(settings);
    }

    public static String resolveCameraPackage(Context context) {
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        ResolveInfo info = context.getPackageManager().resolveActivity(
                camera,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        if (info != null && info.activityInfo != null) {
            return info.activityInfo.packageName;
        }
        return "";
    }

    public static String resolveDialerPackage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecom != null) {
                String defaultDialer = telecom.getDefaultDialerPackage();
                if (isInstalled(context, defaultDialer)) {
                    return defaultDialer;
                }
            }
        }

        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"));
        ResolveInfo info = context.getPackageManager().resolveActivity(
                dial,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        if (info != null && info.activityInfo != null) {
            return info.activityInfo.packageName;
        }
        return "";
    }

    public static boolean isSettingsHidden(Context context) {
        if (!isDeviceOwner(context)) {
            return false;
        }
        DevicePolicyManager manager = dpm(context);
        if (manager == null) {
            return false;
        }
        try {
            return manager.isApplicationHidden(admin(context), SETTINGS_PACKAGE);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isInstalled(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    private static Set<String> buildAllowlist(Context context) {
        Set<String> packages = new LinkedHashSet<>();
        packages.add(context.getPackageName());

        addIfInstalled(context, packages, WHATSAPP);
        addIfInstalled(context, packages, WHATSAPP_BUSINESS);
        addIfInstalled(context, packages, GUARD_PACKAGE);
        addIfInstalled(context, packages, AppPrefs.getOdooPackage(context));

        String camera = resolveCameraPackage(context);
        addIfInstalled(context, packages, camera);

        String dialer = resolveDialerPackage(context);
        addIfInstalled(context, packages, dialer);
        for (String packageName : PHONE_PACKAGES) {
            addIfInstalled(context, packages, packageName);
        }

        return packages;
    }

    private static void addIfInstalled(Context context, Set<String> packages, String packageName) {
        if (isInstalled(context, packageName)) {
            packages.add(packageName);
        }
    }

    private static void grantRequiredRuntimePermissions(
            Context context,
            DevicePolicyManager manager,
            ComponentName admin
    ) {
        // The DPC can silently grant the kiosk camera permission. This lets the
        // torch tile work after a signed in-place update without opening Settings.
        safeGrantRuntimePermission(
                context,
                manager,
                admin,
                context.getPackageName(),
                Manifest.permission.CAMERA
        );

        // Android 13+ requires POST_NOTIFICATIONS. Grant it to the approved work
        // apps, while LOCK_TASK_FEATURE_NOTIFICATIONS allows their notifications
        // to be shown during full lock-task kiosk mode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] notificationPackages = new String[] {
                    WHATSAPP,
                    WHATSAPP_BUSINESS,
                    GUARD_PACKAGE,
                    AppPrefs.getOdooPackage(context)
            };
            for (String packageName : notificationPackages) {
                safeGrantRuntimePermission(
                        context,
                        manager,
                        admin,
                        packageName,
                        Manifest.permission.POST_NOTIFICATIONS
                );
            }
        }
    }

    private static void safeGrantRuntimePermission(
            Context context,
            DevicePolicyManager manager,
            ComponentName admin,
            String packageName,
            String permission
    ) {
        if (!isInstalled(context, packageName)) {
            return;
        }
        try {
            manager.setPermissionGrantState(
                    admin,
                    packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            );
        } catch (RuntimeException ignored) {
            // Some OEM/app combinations do not expose every runtime permission
            // to the DPC. The app remains usable and can request it normally.
        }
    }

    private static void addRestrictions(DevicePolicyManager manager, ComponentName admin) {
        String[] restrictions = new String[] {
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_FACTORY_RESET,
                UserManager.DISALLOW_ADD_USER,
                UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
                UserManager.DISALLOW_DEBUGGING_FEATURES,
                UserManager.DISALLOW_APPS_CONTROL,
                UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_SET_WALLPAPER
        };
        for (String restriction : restrictions) {
            safeAddRestriction(manager, admin, restriction);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            safeAddRestriction(manager, admin, UserManager.DISALLOW_USER_SWITCH);
            safeAddRestriction(manager, admin, UserManager.DISALLOW_CONFIG_BRIGHTNESS);
            safeAddRestriction(manager, admin, UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT);
            safeAddRestriction(manager, admin, UserManager.DISALLOW_AMBIENT_DISPLAY);
        }
    }

    private static void clearRestrictions(DevicePolicyManager manager, ComponentName admin) {
        String[] restrictions = new String[] {
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_FACTORY_RESET,
                UserManager.DISALLOW_ADD_USER,
                UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
                UserManager.DISALLOW_DEBUGGING_FEATURES,
                UserManager.DISALLOW_APPS_CONTROL,
                UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_SET_WALLPAPER
        };
        for (String restriction : restrictions) {
            safeClearRestriction(manager, admin, restriction);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            safeClearRestriction(manager, admin, UserManager.DISALLOW_USER_SWITCH);
            safeClearRestriction(manager, admin, UserManager.DISALLOW_CONFIG_BRIGHTNESS);
            safeClearRestriction(manager, admin, UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT);
            safeClearRestriction(manager, admin, UserManager.DISALLOW_AMBIENT_DISPLAY);
        }
    }

    private static void safeAddRestriction(
            DevicePolicyManager manager,
            ComponentName admin,
            String restriction
    ) {
        try {
            manager.addUserRestriction(admin, restriction);
        } catch (RuntimeException ignored) {
        }
    }

    private static void safeClearRestriction(
            DevicePolicyManager manager,
            ComponentName admin,
            String restriction
    ) {
        try {
            manager.clearUserRestriction(admin, restriction);
        } catch (RuntimeException ignored) {
        }
    }

    private static void blockCompanyAppUninstall(
            Context context,
            DevicePolicyManager manager,
            ComponentName admin,
            boolean blocked
    ) {
        String[] packages = new String[] {
                context.getPackageName(),
                GUARD_PACKAGE,
                WHATSAPP,
                WHATSAPP_BUSINESS,
                AppPrefs.getOdooPackage(context)
        };
        for (String packageName : packages) {
            if (isInstalled(context, packageName)) {
                try {
                    manager.setUninstallBlocked(admin, packageName, blocked);
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private static void safeSetStatusBar(
            DevicePolicyManager manager,
            ComponentName admin,
            boolean disabled
    ) {
        try {
            manager.setStatusBarDisabled(admin, disabled);
        } catch (RuntimeException ignored) {
        }
    }

    private static void safeSetKeyguard(
            DevicePolicyManager manager,
            ComponentName admin,
            boolean disabled
    ) {
        try {
            manager.setKeyguardDisabled(admin, disabled);
        } catch (RuntimeException ignored) {
        }
    }

    private static void safeSetApplicationHidden(
            DevicePolicyManager manager,
            ComponentName admin,
            String packageName,
            boolean hidden
    ) {
        try {
            manager.setApplicationHidden(admin, packageName, hidden);
        } catch (RuntimeException ignored) {
        }
    }
}

package com.thramart.setup2

import android.app.Activity
import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import java.util.Calendar

object KioskPolicy {
    enum class LaunchResult {
        OPENED,
        NOT_ALLOWED,
        OUTSIDE_SCHEDULE,
        NOT_INSTALLED,
        FAILED
    }

    fun admin(context: Context) = ComponentName(context, KioskAdminReceiver::class.java)

    fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean = dpm(context).isDeviceOwnerApp(context.packageName)

    fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun currentAllowedPackages(context: Context): Array<String> {
        val list = mutableListOf(context.packageName)
        val now = nowMinutes()
        RuleStore.loadRules(context)
            .filter { it.isAllowedNow(now) }
            .forEach { list.add(it.packageName) }
        return list.distinct().toTypedArray()
    }

    fun applyPolicies(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = dpm(context)
        val admin = admin(context)

        dpm.setLockTaskPackages(admin, currentAllowedPackages(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
            )
        }

        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(
            admin,
            filter,
            ComponentName(context, MainActivity::class.java)
        )

        safeRestriction(context, UserManager.DISALLOW_SAFE_BOOT)
        safeRestriction(context, UserManager.DISALLOW_ADD_USER)
        safeRestriction(context, UserManager.DISALLOW_CREATE_WINDOWS)
        safeRestriction(context, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
    }

    private fun safeRestriction(context: Context, restriction: String) {
        try {
            dpm(context).addUserRestriction(admin(context), restriction)
        } catch (_: Exception) {
        }
    }

    fun enterSelfLockTask(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        if (!dpm(activity).isLockTaskPermitted(activity.packageName)) return
        try {
            activity.startLockTask()
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    fun exitKioskAndRemoveDeviceOwner(activity: Activity): Boolean {
        val manager = dpm(activity)
        val admin = admin(activity)

        try { activity.stopLockTask() } catch (_: Exception) {}

        if (!manager.isDeviceOwnerApp(activity.packageName)) return true

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
        } catch (_: Exception) {}
        try { manager.setLockTaskPackages(admin, emptyArray()) } catch (_: Exception) {}
        try { manager.clearPackagePersistentPreferredActivities(admin, activity.packageName) } catch (_: Exception) {}
        try { manager.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) } catch (_: Exception) {}
        try { manager.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER) } catch (_: Exception) {}
        try { manager.clearUserRestriction(admin, UserManager.DISALLOW_CREATE_WINDOWS) } catch (_: Exception) {}
        try { manager.clearUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA) } catch (_: Exception) {}

        return try {
            manager.clearDeviceOwnerApp(activity.packageName)
            !manager.isDeviceOwnerApp(activity.packageName)
        } catch (_: Exception) {
            false
        }
    }

    fun launchAllowedApp(context: Context, packageName: String): LaunchResult {
        val rule = RuleStore.loadRules(context).firstOrNull { it.packageName == packageName }
            ?: return LaunchResult.NOT_ALLOWED

        if (!rule.enabled) return LaunchResult.NOT_ALLOWED
        if (!rule.isAllowedNow(nowMinutes())) return LaunchResult.OUTSIDE_SCHEDULE

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return LaunchResult.NOT_INSTALLED
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            val useLockedLaunch =
                isDeviceOwner(context) && dpm(context).isLockTaskPermitted(packageName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && useLockedLaunch) {
                val options = ActivityOptions.makeBasic().apply { setLockTaskEnabled(true) }
                context.startActivity(intent, options.toBundle())
            } else {
                // Normal launch is intentionally supported before Device Owner provisioning
                // so an admin can test selected apps on a fresh phone.
                context.startActivity(intent)
            }
            LaunchResult.OPENED
        } catch (_: Exception) {
            LaunchResult.FAILED
        }
    }
}

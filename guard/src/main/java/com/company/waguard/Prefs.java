package com.company.waguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

final class Prefs {
    static final String FILE = "wa_guard";
    static final String MASTER_ENABLED = "master_enabled";
    static final String WHATSAPP_ENABLED = "whatsapp_enabled";
    static final String BUSINESS_ENABLED = "business_enabled";
    static final String AUTO_DETECT_ENABLED = "auto_detect_enabled";
    static final String ZERO_GAP_ENABLED = "zero_gap_enabled";
    static final String BLOCKER_SIZE_DP = "blocker_size_dp";
    static final String CALIBRATION_REQUESTED = "calibration_requested";
    static final String CALIBRATION_PACKAGE = "calibration_package";

    static final int MIN_BLOCKER_SIZE_DP = 48;
    static final int MAX_BLOCKER_SIZE_DP = 140;
    static final int DEFAULT_BLOCKER_SIZE_DP = 88;

    private Prefs() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean isTargetEnabled(SharedPreferences prefs, String packageName) {
        if (!prefs.getBoolean(MASTER_ENABLED, true)) {
            return false;
        }
        if (GuardAccessibilityService.WHATSAPP_PACKAGE.equals(packageName)) {
            return prefs.getBoolean(WHATSAPP_ENABLED, true);
        }
        if (GuardAccessibilityService.BUSINESS_PACKAGE.equals(packageName)) {
            return prefs.getBoolean(BUSINESS_ENABLED, true);
        }
        return false;
    }

    static int getBlockerSizeDp(Context context) {
        int value = get(context).getInt(BLOCKER_SIZE_DP, DEFAULT_BLOCKER_SIZE_DP);
        return Math.max(MIN_BLOCKER_SIZE_DP, Math.min(MAX_BLOCKER_SIZE_DP, value));
    }

    static void setBlockerSizeDp(Context context, int value) {
        int safeValue = Math.max(MIN_BLOCKER_SIZE_DP, Math.min(MAX_BLOCKER_SIZE_DP, value));
        get(context).edit().putInt(BLOCKER_SIZE_DP, safeValue).apply();
    }

    static String orientationSuffix(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait";
    }

    static String calibrationKey(String packageName, String part, String orientation) {
        return "cal_" + packageName + "_" + orientation + "_" + part;
    }

    static void saveCalibration(Context context, String packageName, float normalizedX, float normalizedY) {
        String orientation = orientationSuffix(context);
        get(context).edit()
                .putFloat(calibrationKey(packageName, "x", orientation), normalizedX)
                .putFloat(calibrationKey(packageName, "y", orientation), normalizedY)
                .putBoolean(calibrationKey(packageName, "set", orientation), true)
                .apply();
    }

    static boolean hasCalibration(Context context, String packageName) {
        String orientation = orientationSuffix(context);
        return get(context).getBoolean(calibrationKey(packageName, "set", orientation), false);
    }

    static float getCalibrationX(Context context, String packageName) {
        String orientation = orientationSuffix(context);
        return get(context).getFloat(calibrationKey(packageName, "x", orientation), 0.20f);
    }

    static float getCalibrationY(Context context, String packageName) {
        String orientation = orientationSuffix(context);
        return get(context).getFloat(calibrationKey(packageName, "y", orientation), 0.78f);
    }

    static void resetCalibration(Context context) {
        SharedPreferences prefs = get(context);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("cal_")) {
                editor.remove(key);
            }
        }
        editor.remove(CALIBRATION_REQUESTED).remove(CALIBRATION_PACKAGE).apply();
    }
}

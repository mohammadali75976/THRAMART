package com.company.kiosk;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String FILE = "company_kiosk_prefs";
    private static final String KEY_URL = "company_url";
    private static final String KEY_ODOO = "odoo_package";
    private static final String KEY_PIN = "admin_pin";
    private static final String KEY_KIOSK = "kiosk_enabled";

    private AppPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String getUrl(Context context) {
        return prefs(context).getString(KEY_URL, "https://example.com");
    }

    public static void setUrl(Context context, String value) {
        prefs(context).edit().putString(KEY_URL, value.trim()).apply();
    }

    public static String getOdooPackage(Context context) {
        return prefs(context).getString(KEY_ODOO, "com.odoo.mobile");
    }

    public static void setOdooPackage(Context context, String value) {
        prefs(context).edit().putString(KEY_ODOO, value.trim()).apply();
    }

    public static String getPin(Context context) {
        return prefs(context).getString(KEY_PIN, "2552");
    }

    public static void setPin(Context context, String value) {
        prefs(context).edit().putString(KEY_PIN, value).apply();
    }

    public static boolean isKioskEnabled(Context context) {
        return prefs(context).getBoolean(KEY_KIOSK, false);
    }

    public static void setKioskEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_KIOSK, value).apply();
    }
}

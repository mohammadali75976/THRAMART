package com.company.kiosk;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class KioskAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "Thramart Kiosk admin enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Company kiosk protection will be removed.";
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        AppPrefs.setKioskEnabled(context, true);
        KioskPolicyManager.applyPolicies(context);
    }
}

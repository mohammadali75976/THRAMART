package com.company.kiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class AdminActivity extends Activity {
    private TextView ownerStatus;
    private EditText urlInput;
    private EditText odooInput;
    private EditText pinInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        ownerStatus = findViewById(R.id.txtOwnerStatus);
        urlInput = findViewById(R.id.editUrl);
        odooInput = findViewById(R.id.editOdooPackage);
        pinInput = findViewById(R.id.editPin);
        Button save = findViewById(R.id.btnSave);
        Button startKiosk = findViewById(R.id.btnStartKiosk);
        Button exitKiosk = findViewById(R.id.btnExitKiosk);
        Button releaseOwner = findViewById(R.id.btnReleaseOwner);
        Button back = findViewById(R.id.btnBack);

        urlInput.setText(AppPrefs.getUrl(this));
        odooInput.setText(AppPrefs.getOdooPackage(this));
        pinInput.setText(AppPrefs.getPin(this));

        save.setOnClickListener(v -> saveSettings(true));
        startKiosk.setOnClickListener(v -> startOrReapplyKiosk());
        exitKiosk.setOnClickListener(v -> exitKiosk());
        releaseOwner.setOnClickListener(v -> confirmReleaseDeviceOwner());
        back.setOnClickListener(v -> finish());

        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        String camera = KioskPolicyManager.resolveCameraPackage(this);
        String dialer = KioskPolicyManager.resolveDialerPackage(this);

        StringBuilder status = new StringBuilder();
        status.append("Device Owner: ")
                .append(KioskPolicyManager.isDeviceOwner(this) ? "YES" : "NO")
                .append("\nKiosk preference: ")
                .append(AppPrefs.isKioskEnabled(this) ? "ON" : "OFF")
                .append("\nBattery/Wi-Fi bar: ENABLED")
                .append("\nAndroid Settings: ")
                .append(KioskPolicyManager.isSettingsHidden(this) ? "HIDDEN" : "VISIBLE")
                .append("\nPhone package: ")
                .append(dialer.isEmpty() ? "not found" : dialer)
                .append("\nWhatsApp Normal: ")
                .append(KioskPolicyManager.isInstalled(this, KioskPolicyManager.WHATSAPP)
                        ? "INSTALLED" : "NOT INSTALLED")
                .append("\nWhatsApp Business: ")
                .append(KioskPolicyManager.isInstalled(this, KioskPolicyManager.WHATSAPP_BUSINESS)
                        ? "INSTALLED" : "NOT INSTALLED")
                .append("\nWA Guard: ")
                .append(KioskPolicyManager.isInstalled(this, KioskPolicyManager.GUARD_PACKAGE)
                        ? "INSTALLED" : "NOT INSTALLED")
                .append("\nCamera package: ")
                .append(camera.isEmpty() ? "not found" : camera);
        ownerStatus.setText(status.toString());
    }

    private boolean saveSettings(boolean showToast) {
        String url = urlInput.getText().toString().trim();
        String odoo = odooInput.getText().toString().trim();
        String pin = pinInput.getText().toString().trim();

        if (!URLUtil.isHttpUrl(url) && !URLUtil.isHttpsUrl(url)) {
            urlInput.setError("http:// ya https:// URL required");
            return false;
        }
        Uri parsed = Uri.parse(url);
        if (parsed.getHost() == null || parsed.getHost().trim().isEmpty()) {
            urlInput.setError("Valid URL required");
            return false;
        }
        if (odoo.isEmpty() || !odoo.contains(".")) {
            odooInput.setError("Valid package name required");
            return false;
        }
        if (pin.length() < 4 || pin.length() > 8) {
            pinInput.setError("PIN 4 se 8 digits");
            return false;
        }

        AppPrefs.setUrl(this, url);
        AppPrefs.setOdooPackage(this, odoo);
        AppPrefs.setPin(this, pin);
        if (showToast) {
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void startOrReapplyKiosk() {
        if (!saveSettings(false)) {
            return;
        }
        if (!KioskPolicyManager.isDeviceOwner(this)) {
            Toast.makeText(
                    this,
                    "Full kiosk ke liye app ko Device Owner banana zaroori hai",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        String result = KioskPolicyManager.applyPolicies(this);
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        finish();
    }

    private void confirmReleaseDeviceOwner() {
        if (!KioskPolicyManager.isDeviceOwner(this)) {
            Toast.makeText(this, "Device Owner already removed", Toast.LENGTH_LONG).show();
            refreshStatus();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("REMOVE DEVICE OWNER")
                .setMessage(
                        "Use this only to move this phone from the old Thramart kiosk to Setup 2. "
                                + "Kiosk restrictions will be cleared first, then the old Device Owner will be released."
                )
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("REMOVE", (dialog, which) -> releaseDeviceOwner())
                .show();
    }

    @SuppressWarnings("deprecation")
    private void releaseDeviceOwner() {
        if (!KioskPolicyManager.isDeviceOwner(this)) {
            Toast.makeText(this, "Device Owner already removed", Toast.LENGTH_LONG).show();
            refreshStatus();
            return;
        }

        KioskPolicyManager.disableKiosk(this);
        DevicePolicyManager manager = KioskPolicyManager.dpm(this);
        if (manager == null) {
            Toast.makeText(this, "DevicePolicyManager available nahi", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            manager.clearDeviceOwnerApp(getPackageName());
            AppPrefs.setKioskEnabled(this, false);
            Toast.makeText(
                    this,
                    "Old Device Owner removed. Ab ADB se dpm list-owners check karein.",
                    Toast.LENGTH_LONG
            ).show();
            refreshStatus();
        } catch (SecurityException | IllegalArgumentException exception) {
            Toast.makeText(
                    this,
                    "Device Owner remove error: " + exception.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void exitKiosk() {
        String result = KioskPolicyManager.disableKiosk(this);
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        KioskPolicyManager.openAndroidSettings(this);
        finish();
    }
}

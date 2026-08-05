package com.company.waguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends Activity {
    private static final String PIN_SHA256 =
            "beda66e7862b7ad41c56bbe0f38ad082aef73f1ec96cebb2d61d03322c40ad86";

    private TextView statusText;
    private SharedPreferences prefs;
    private View contentRoot;
    private boolean uiInitialized;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = Prefs.get(this);
        contentRoot = findViewById(R.id.contentRoot);
        contentRoot.setVisibility(View.INVISIBLE);
        showPinDialog();
    }

    private void showPinDialog() {
        EditText input = new EditText(this);
        input.setHint("Enter 4-digit PIN");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setSingleLine(true);
        input.setMaxLines(1);
        input.setPadding(dp(20), dp(12), dp(20), dp(12));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("WA Guard locked")
                .setMessage("Enter the company PIN to open settings.")
                .setView(input)
                .setNegativeButton("CLOSE", (d, which) -> finish())
                .setPositiveButton("UNLOCK", null)
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String entered = input.getText().toString().trim();
                    if (matchesPin(entered)) {
                        dialog.dismiss();
                        contentRoot.setVisibility(View.VISIBLE);
                        initializeUiOnce();
                    } else {
                        input.setText("");
                        input.setError("Wrong PIN");
                        input.requestFocus();
                    }
                }));
        dialog.setOnCancelListener(d -> finish());
        dialog.show();
        input.requestFocus();
    }

    private boolean matchesPin(String entered) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(entered.getBytes(StandardCharsets.UTF_8));
            return constantTimeEquals(toHex(hash), PIN_SHA256);
        } catch (NoSuchAlgorithmException impossible) {
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < a.length(); i++) {
            difference |= a.charAt(i) ^ b.charAt(i);
        }
        return difference == 0;
    }

    private String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }

    private void initializeUiOnce() {
        if (uiInitialized) {
            return;
        }
        uiInitialized = true;

        statusText = findViewById(R.id.statusText);

        CheckBox masterEnabled = findViewById(R.id.masterEnabled);
        CheckBox whatsappEnabled = findViewById(R.id.whatsappEnabled);
        CheckBox businessEnabled = findViewById(R.id.businessEnabled);
        CheckBox autoDetectEnabled = findViewById(R.id.autoDetectEnabled);
        CheckBox zeroGapEnabled = findViewById(R.id.zeroGapEnabled);
        TextView blockerSizeText = findViewById(R.id.blockerSizeText);
        SeekBar blockerSizeSeekBar = findViewById(R.id.blockerSizeSeekBar);

        masterEnabled.setChecked(prefs.getBoolean(Prefs.MASTER_ENABLED, true));
        whatsappEnabled.setChecked(prefs.getBoolean(Prefs.WHATSAPP_ENABLED, true));
        businessEnabled.setChecked(prefs.getBoolean(Prefs.BUSINESS_ENABLED, true));
        autoDetectEnabled.setChecked(prefs.getBoolean(Prefs.AUTO_DETECT_ENABLED, true));
        zeroGapEnabled.setChecked(prefs.getBoolean(Prefs.ZERO_GAP_ENABLED, true));

        masterEnabled.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(Prefs.MASTER_ENABLED, checked).apply());
        whatsappEnabled.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(Prefs.WHATSAPP_ENABLED, checked).apply());
        businessEnabled.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(Prefs.BUSINESS_ENABLED, checked).apply());
        autoDetectEnabled.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(Prefs.AUTO_DETECT_ENABLED, checked).apply());
        zeroGapEnabled.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(Prefs.ZERO_GAP_ENABLED, checked).apply());

        int initialSize = Prefs.getBlockerSizeDp(this);
        blockerSizeSeekBar.setMax(Prefs.MAX_BLOCKER_SIZE_DP - Prefs.MIN_BLOCKER_SIZE_DP);
        blockerSizeSeekBar.setProgress(initialSize - Prefs.MIN_BLOCKER_SIZE_DP);
        blockerSizeText.setText("Red circle size: " + initialSize + " dp");
        blockerSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = Prefs.MIN_BLOCKER_SIZE_DP + progress;
                blockerSizeText.setText("Red circle size: " + size + " dp");
                if (fromUser) {
                    Prefs.setBlockerSizeDp(MainActivity.this, size);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int size = Prefs.MIN_BLOCKER_SIZE_DP + seekBar.getProgress();
                Prefs.setBlockerSizeDp(MainActivity.this, size);
                Toast.makeText(MainActivity.this,
                        "Circle size saved. Recalibrate for best alignment.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        Button openAccessibility = findViewById(R.id.openAccessibility);
        openAccessibility.setOnClickListener(v -> {
            Toast.makeText(this,
                    "Select WA View Once Guard, read the disclosure, then enable it.",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        findViewById(R.id.calibrateWhatsApp).setOnClickListener(v ->
                beginCalibration(GuardAccessibilityService.WHATSAPP_PACKAGE));
        findViewById(R.id.calibrateBusiness).setOnClickListener(v ->
                beginCalibration(GuardAccessibilityService.BUSINESS_PACKAGE));
        findViewById(R.id.resetCalibration).setOnClickListener(v -> {
            Prefs.resetCalibration(this);
            Toast.makeText(this, "Saved calibration removed.", Toast.LENGTH_SHORT).show();
            refreshStatus();
        });

        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (uiInitialized) {
            refreshStatus();
        }
    }

    private void beginCalibration(String packageName) {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this,
                    "Enable the Accessibility service first.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            Toast.makeText(this, "That WhatsApp app is not installed.", Toast.LENGTH_LONG).show();
            return;
        }

        prefs.edit()
                .putBoolean(Prefs.CALIBRATION_REQUESTED, true)
                .putString(Prefs.CALIBRATION_PACKAGE, packageName)
                .apply();

        Toast.makeText(this,
                "Open any chat, select a photo/video, then use the red calibration circle on the preview screen.",
                Toast.LENGTH_LONG).show();
        startActivity(launchIntent);
    }

    private void refreshStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        boolean waCal = Prefs.hasCalibration(this, GuardAccessibilityService.WHATSAPP_PACKAGE);
        boolean bizCal = Prefs.hasCalibration(this, GuardAccessibilityService.BUSINESS_PACKAGE);

        String status = (enabled ? "Accessibility: ENABLED" : "Accessibility: DISABLED")
                + "\nWhatsApp calibration: " + (waCal ? "saved" : "not saved")
                + "\nBusiness calibration: " + (bizCal ? "saved" : "not saved")
                + "\nCircle size: " + Prefs.getBlockerSizeDp(this) + " dp";
        statusText.setText(status);
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName expected = new ComponentName(this, GuardAccessibilityService.class);
        String enabledServices = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName enabled = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(enabled)) {
                return true;
            }
        }
        return false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

package com.company.kiosk;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 4102;
    private static final int REQUEST_CAMERA_STORAGE = 4103;
    private static final int REQUEST_TORCH_CAMERA = 4104;

    private TextView statusView;
    private Button torchButton;
    private Uri pendingCameraUri;
    private CameraManager cameraManager;
    private String torchCameraId;
    private boolean torchOn;

    private final CameraManager.TorchCallback torchCallback =
            new CameraManager.TorchCallback() {
                @Override
                public void onTorchModeChanged(String cameraId, boolean enabled) {
                    if (cameraId.equals(torchCameraId)) {
                        torchOn = enabled;
                        runOnUiThread(() -> updateTorchButton());
                    }
                }

                @Override
                public void onTorchModeUnavailable(String cameraId) {
                    if (cameraId.equals(torchCameraId)) {
                        torchOn = false;
                        runOnUiThread(() -> updateTorchButton());
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.txtStatus);
        Button whatsapp = findViewById(R.id.btnWhatsApp);
        Button whatsappBusiness = findViewById(R.id.btnWhatsAppBusiness);
        Button odoo = findViewById(R.id.btnOdoo);
        Button phone = findViewById(R.id.btnPhone);
        Button guard = findViewById(R.id.btnWaGuard);
        Button camera = findViewById(R.id.btnCamera);
        torchButton = findViewById(R.id.btnTorch);
        Button gallery = findViewById(R.id.btnGallery);
        Button calculator = findViewById(R.id.btnCalculator);
        Button portal = findViewById(R.id.btnPortal);
        Button admin = findViewById(R.id.btnAdmin);

        whatsapp.setOnClickListener(v -> openPackage(
                KioskPolicyManager.WHATSAPP,
                "WhatsApp Normal"
        ));
        whatsappBusiness.setOnClickListener(v -> openPackage(
                KioskPolicyManager.WHATSAPP_BUSINESS,
                "WhatsApp Business"
        ));
        odoo.setOnClickListener(v -> openPackage(AppPrefs.getOdooPackage(this), "Odoo"));
        phone.setOnClickListener(v -> openPhone());
        guard.setOnClickListener(v -> openPackage(
                KioskPolicyManager.GUARD_PACKAGE,
                "WA View Once Guard"
        ));
        camera.setOnClickListener(v -> requestCameraOrOpen());
        torchButton.setOnClickListener(v -> toggleTorch());
        gallery.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        calculator.setOnClickListener(v -> startActivity(new Intent(this, CalculatorActivity.class)));
        portal.setOnClickListener(v -> openCompanyUrl());
        admin.setOnClickListener(v -> showAdminPinDialog());
        initializeTorch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (AppPrefs.isKioskEnabled(this) && KioskPolicyManager.isDeviceOwner(this)) {
            KioskPolicyManager.applyPolicies(this);
            KioskPolicyManager.startLockTaskIfAllowed(this);
            showStatusInfoAndHideNavigation();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && AppPrefs.isKioskEnabled(this)) {
            showStatusInfoAndHideNavigation();
        }
    }

    private void refreshStatus() {
        if (KioskPolicyManager.isDeviceOwner(this)) {
            statusView.setText(AppPrefs.isKioskEnabled(this)
                    ? "Kiosk ON • Battery/Wi-Fi visible • Settings locked"
                    : "Device Owner ready • Kiosk currently OFF");
        } else {
            statusView.setText("Setup mode • Device Owner required for full lock");
        }
    }

    private void openPhone() {
        String dialerPackage = KioskPolicyManager.resolveDialerPackage(this);
        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"));
        if (!dialerPackage.isEmpty()) {
            dial.setPackage(dialerPackage);
        }
        dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        try {
            startActivity(dial);
        } catch (SecurityException exception) {
            Toast.makeText(this, "Phone allowlist re-apply karein", Toast.LENGTH_LONG).show();
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Phone/Dialer app nahi mili", Toast.LENGTH_LONG).show();
        }
    }

    private void openPackage(String packageName, String label) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            Toast.makeText(this, label + " install nahi hai", Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(launch);
        } catch (SecurityException exception) {
            Toast.makeText(this, label + " allowlist refresh karein", Toast.LENGTH_LONG).show();
        }
    }

    private void openCompanyUrl() {
        String url = AppPrefs.getUrl(this);
        if (!URLUtil.isHttpUrl(url) && !URLUtil.isHttpsUrl(url)) {
            Toast.makeText(this, "Admin panel mein valid URL save karein", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, WebActivity.class);
        intent.putExtra(WebActivity.EXTRA_URL, url);
        startActivity(intent);
    }

    private void requestCameraOrOpen() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CAMERA_STORAGE
            );
            return;
        }
        openCamera();
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        ResolveInfo info = getPackageManager().resolveActivity(
                cameraIntent,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        if (info == null || info.activityInfo == null) {
            Toast.makeText(this, "Camera app nahi mili", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            pendingCameraUri = createCameraOutputUri();
            if (pendingCameraUri == null) {
                Toast.makeText(this, "Camera file create nahi hui", Toast.LENGTH_LONG).show();
                return;
            }
            cameraIntent.setPackage(info.activityInfo.packageName);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            cameraIntent.setClipData(ClipData.newRawUri("Thramart photo", pendingCameraUri));
            cameraIntent.addFlags(
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            startActivityForResult(cameraIntent, REQUEST_CAPTURE);
        } catch (SecurityException exception) {
            cleanupPendingCameraUri();
            Toast.makeText(this, "Camera allowlist re-apply karein", Toast.LENGTH_LONG).show();
        }
    }

    private Uri createCameraOutputUri() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_" + timestamp + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ThramartKiosk");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }
        return getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE || pendingCameraUri == null) {
            return;
        }

        if (resultCode == RESULT_OK) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(pendingCameraUri, values, null, null);
            }
            Toast.makeText(this, "Photo Gallery mein save ho gayi", Toast.LENGTH_SHORT).show();
            pendingCameraUri = null;
        } else {
            cleanupPendingCameraUri();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera storage permission required", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_TORCH_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleTorch();
            } else {
                Toast.makeText(this, "Torch ke liye camera permission required", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initializeTorch() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        torchCameraId = findTorchCameraId();
        if (cameraManager != null) {
            try {
                cameraManager.registerTorchCallback(torchCallback, null);
            } catch (RuntimeException ignored) {
            }
        }
        updateTorchButton();
    }

    private String findTorchCameraId() {
        if (cameraManager == null) {
            return null;
        }
        String fallback = null;
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics =
                        cameraManager.getCameraCharacteristics(cameraId);
                Boolean flashAvailable = characteristics.get(
                        CameraCharacteristics.FLASH_INFO_AVAILABLE
                );
                if (!Boolean.TRUE.equals(flashAvailable)) {
                    continue;
                }
                if (fallback == null) {
                    fallback = cameraId;
                }
                Integer lensFacing = characteristics.get(
                        CameraCharacteristics.LENS_FACING
                );
                if (lensFacing != null
                        && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException | RuntimeException ignored) {
        }
        return fallback;
    }

    private void toggleTorch() {
        if (cameraManager == null || torchCameraId == null) {
            Toast.makeText(this, "Is phone mein torch available nahi", Toast.LENGTH_LONG).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_TORCH_CAMERA
            );
            return;
        }
        try {
            cameraManager.setTorchMode(torchCameraId, !torchOn);
        } catch (CameraAccessException | SecurityException | IllegalArgumentException exception) {
            Toast.makeText(
                    this,
                    "Torch start nahi hui: " + exception.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void updateTorchButton() {
        if (torchButton == null) {
            return;
        }
        if (torchCameraId == null) {
            torchButton.setText("TORCH: N/A");
            torchButton.setEnabled(false);
        } else {
            torchButton.setEnabled(true);
            torchButton.setText(torchOn ? "TORCH: ON" : "TORCH: OFF");
        }
    }

    @Override
    protected void onDestroy() {
        if (cameraManager != null) {
            try {
                cameraManager.unregisterTorchCallback(torchCallback);
            } catch (RuntimeException ignored) {
            }
        }
        super.onDestroy();
    }

    private void cleanupPendingCameraUri() {
        if (pendingCameraUri != null) {
            try {
                getContentResolver().delete(pendingCameraUri, null, null);
            } catch (RuntimeException ignored) {
            }
            pendingCameraUri = null;
        }
    }

    private void showAdminPinDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("PIN");
        input.setSingleLine(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Thramart Admin PIN")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (AppPrefs.getPin(this).equals(input.getText().toString())) {
                        dialog.dismiss();
                        startActivity(new Intent(this, AdminActivity.class));
                    } else {
                        input.setError("Wrong PIN");
                    }
                }));
        dialog.show();
    }

    private void showStatusInfoAndHideNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars());
                controller.hide(WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override
    public void onBackPressed() {
        // This activity is the controlled launcher. Back does not leave it.
    }
}

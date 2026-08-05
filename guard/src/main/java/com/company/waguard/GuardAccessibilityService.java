package com.company.waguard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class GuardAccessibilityService extends AccessibilityService {
    public static final String WHATSAPP_PACKAGE = "com.whatsapp";
    public static final String BUSINESS_PACKAGE = "com.whatsapp.w4b";

    private static final long SCAN_DEBOUNCE_MS = 0L;
    private static final int MAX_NODES = 900;

    private WindowManager windowManager;
    private SharedPreferences prefs;
    private TextView blockerView;
    private WindowManager.LayoutParams blockerParams;
    private FrameLayout calibrationOverlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String activePackage;
    private long lastScanAt;
    private long lastBlockedToastAt;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = Prefs.get(this);

        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_SCROLLED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        // Receive package-change events too, so a persistent zero-gap overlay is
        // removed immediately when the user leaves WhatsApp. Content is only
        // inspected after the explicit package check in onAccessibilityEvent().
        info.packageNames = null;
        info.notificationTimeout = 0;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        String eventPackage = event.getPackageName().toString();
        String packageName = resolveForegroundTargetPackage(eventPackage);
        if (packageName == null || !Prefs.isTargetEnabled(prefs, packageName)) {
            activePackage = null;
            hideBlocker();
            hideCalibrationOverlay();
            return;
        }

        activePackage = packageName;

        boolean calibrationRequested = prefs.getBoolean(Prefs.CALIBRATION_REQUESTED, false);
        boolean zeroGap = prefs.getBoolean(Prefs.ZERO_GAP_ENABLED, true);
        if (!calibrationRequested && zeroGap && Prefs.hasCalibration(this, packageName)) {
            // Pre-arm the calibrated touch blocker before scanning the UI. This
            // keeps the View Once location blocked across screen transitions,
            // eliminating the small reactive-overlay race.
            showCalibratedBlocker(packageName);
        }

        long now = SystemClock.uptimeMillis();
        if (SCAN_DEBOUNCE_MS > 0 && now - lastScanAt < SCAN_DEBOUNCE_MS) {
            handler.removeCallbacks(scanRunnable);
            handler.postDelayed(scanRunnable, SCAN_DEBOUNCE_MS);
            return;
        }
        lastScanAt = now;
        evaluateCurrentScreen();
    }


    /**
     * Accessibility overlays generate their own window events.  Treating the
     * event source package as the foreground app makes the blocker remove and
     * re-add itself repeatedly.  Resolve the actual active application window
     * instead, and only keep the overlay when that window belongs to WhatsApp.
     */
    private String resolveForegroundTargetPackage(String eventPackage) {
        if (isTargetPackage(eventPackage)) {
            return eventPackage;
        }

        AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
        if (activeRoot != null && activeRoot.getPackageName() != null) {
            String rootPackage = activeRoot.getPackageName().toString();
            if (isTargetPackage(rootPackage)) {
                return rootPackage;
            }
        }

        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) {
            return null;
        }
        for (AccessibilityWindowInfo window : windows) {
            if (window == null || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION
                    || (!window.isActive() && !window.isFocused())) {
                continue;
            }
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null || root.getPackageName() == null) {
                continue;
            }
            String windowPackage = root.getPackageName().toString();
            if (isTargetPackage(windowPackage)) {
                return windowPackage;
            }
        }
        return null;
    }

    private final Runnable scanRunnable = this::evaluateCurrentScreen;

    private void evaluateCurrentScreen() {
        if (activePackage == null || !Prefs.isTargetEnabled(prefs, activePackage)) {
            hideBlocker();
            hideCalibrationOverlay();
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            hideBlocker();
            return;
        }

        boolean calibrationRequested = prefs.getBoolean(Prefs.CALIBRATION_REQUESTED, false);
        String calibrationPackage = prefs.getString(Prefs.CALIBRATION_PACKAGE, "");
        boolean mediaPreview = looksLikeMediaPreview(root);

        if (calibrationRequested && activePackage.equals(calibrationPackage)) {
            hideBlocker();
            if (mediaPreview) {
                showCalibrationOverlay(activePackage);
            } else {
                hideCalibrationOverlay();
            }
            return;
        }

        hideCalibrationOverlay();

        if (prefs.getBoolean(Prefs.ZERO_GAP_ENABLED, true)
                && Prefs.hasCalibration(this, activePackage)) {
            // Strong mode: keep the calibrated coordinate blocked for the whole
            // time WhatsApp is foreground. This is the only overlay approach that
            // removes the click-before-overlay gap completely.
            showCalibratedBlocker(activePackage);
            return;
        }

        if (prefs.getBoolean(Prefs.AUTO_DETECT_ENABLED, true)) {
            Rect detected = findViewOnceControl(root);
            if (detected != null) {
                showBlockerAt(detected);
                return;
            }
        }

        if (mediaPreview && Prefs.hasCalibration(this, activePackage)) {
            showCalibratedBlocker(activePackage);
        } else {
            hideBlocker();
        }
    }

    private Rect findViewOnceControl(AccessibilityNodeInfo root) {
        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int checked = 0;

        while (!queue.isEmpty() && checked < MAX_NODES) {
            AccessibilityNodeInfo node = queue.removeFirst();
            checked++;

            String label = nodeLabel(node);
            if (matchesViewOnceLabel(label)) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (isReasonableButton(node, bounds, label)) {
                    return bounds;
                }
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.addLast(child);
                }
            }
        }
        return null;
    }

    private boolean matchesViewOnceLabel(String label) {
        if (label.isEmpty()) {
            return false;
        }
        String[] phrases = new String[]{
                "view once", "view-once", "view 1 time", "view one time",
                "one time photo", "one time video",
                "عرض مرة واحدة", "مرة واحدة",
                "एक बार देखें", "एक बार देखे",
                "ایک بار دیکھیں", "ایک بار دیکھے",
                "ver una vez", "voir une fois", "visualizar uma vez",
                "einmal ansehen", "visualizza una volta", "tek seferlik",
                "一回表示", "一度だけ表示",
                "view_once", "viewonce"
        };
        for (String phrase : phrases) {
            if (label.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReasonableButton(AccessibilityNodeInfo node, Rect bounds, String label) {
        if (bounds.isEmpty() || !node.isVisibleToUser()) {
            return false;
        }

        int min = dp(20);
        int max = dp(150);
        if (bounds.width() < min || bounds.height() < min
                || bounds.width() > max || bounds.height() > max) {
            return false;
        }

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        if (bounds.centerY() < screenHeight * 0.40f) {
            return false;
        }

        boolean resourceHint = label.contains("view_once") || label.contains("viewonce");
        AccessibilityNodeInfo parent = node.getParent();
        boolean actionable = node.isClickable() || (parent != null && parent.isClickable());
        return resourceHint || actionable;
    }

    private boolean looksLikeMediaPreview(AccessibilityNodeInfo root) {
        boolean hasSend = false;
        boolean hasCaption = false;
        boolean hasEditingControl = false;

        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int checked = 0;

        while (!queue.isEmpty() && checked < MAX_NODES) {
            AccessibilityNodeInfo node = queue.removeFirst();
            checked++;
            String label = nodeLabel(node);

            if (containsAny(label,
                    "send", "ارسال", "إرسال", "भेजें", "भेजे", "بھیجیں", "بھیجے",
                    "enviar", "envoyer", "senden", "invio", "gönder")) {
                hasSend = true;
            }
            if (containsAny(label,
                    "add a caption", "caption", "legenda", "leyenda", "légende",
                    "تعليق", "عنوان", "कैप्शन", "کیپشن")) {
                hasCaption = true;
            }
            if (containsAny(label,
                    "crop", "rotate", "draw", "sticker", "edit", "trim",
                    "recortar", "rognage", "drehen", "rotar", "قص", "تدوير",
                    "काटें", "घुमाएँ", "تراشیں", "گھمائیں")) {
                hasEditingControl = true;
            }

            if (hasSend && (hasCaption || hasEditingControl)) {
                return true;
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.addLast(child);
                }
            }
        }
        return false;
    }

    private String nodeLabel(AccessibilityNodeInfo node) {
        StringBuilder out = new StringBuilder();
        if (node.getText() != null) {
            out.append(node.getText()).append(' ');
        }
        if (node.getContentDescription() != null) {
            out.append(node.getContentDescription()).append(' ');
        }
        String id = node.getViewIdResourceName();
        if (id != null) {
            out.append(id);
        }
        return out.toString().toLowerCase(Locale.ROOT).trim();
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private void showBlockerAt(Rect bounds) {
        int padding = dp(8);
        int preferredSize = dp(Prefs.getBlockerSizeDp(this));
        int detectedSize = Math.max(bounds.width(), bounds.height()) + padding * 2;
        int size = Math.max(preferredSize, detectedSize);
        int x = Math.max(0, bounds.centerX() - size / 2);
        int y = Math.max(0, bounds.centerY() - size / 2);
        showBlocker(x, y, size, size);
    }

    private void showCalibratedBlocker(String packageName) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int size = dp(Prefs.getBlockerSizeDp(this));
        float nx = Prefs.getCalibrationX(this, packageName);
        float ny = Prefs.getCalibrationY(this, packageName);
        int x = Math.round(nx * screenWidth - size / 2f);
        int y = Math.round(ny * screenHeight - size / 2f);
        x = Math.max(0, Math.min(screenWidth - size, x));
        y = Math.max(0, Math.min(screenHeight - size, y));
        showBlocker(x, y, size, size);
    }

    private void showBlocker(int x, int y, int width, int height) {
        if (windowManager == null) {
            return;
        }

        if (blockerView == null) {
            blockerView = new TextView(this);
            blockerView.setGravity(Gravity.CENTER);
            blockerView.setText("1×\nOFF");
            blockerView.setTextColor(Color.WHITE);
            blockerView.setTextSize(12f);
            blockerView.setTypeface(null, android.graphics.Typeface.BOLD);
            blockerView.setBackground(circleBackground(0xE6C62828, Color.WHITE, dp(2)));
            blockerView.setElevation(dp(8));
            blockerView.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastBlockedToastAt > 1200L) {
                        lastBlockedToastAt = now;
                        Toast.makeText(this,
                                "View Once is disabled by company policy.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            });
        }

        if (blockerParams == null) {
            blockerParams = new WindowManager.LayoutParams(
                    width,
                    height,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            blockerParams.gravity = Gravity.TOP | Gravity.START;
            blockerParams.x = x;
            blockerParams.y = y;
            try {
                windowManager.addView(blockerView, blockerParams);
            } catch (RuntimeException ignored) {
                blockerView = null;
                blockerParams = null;
            }
        } else {
            blockerParams.width = width;
            blockerParams.height = height;
            blockerParams.x = x;
            blockerParams.y = y;
            try {
                windowManager.updateViewLayout(blockerView, blockerParams);
            } catch (RuntimeException ignored) {
                hideBlocker();
            }
        }
    }

    private void hideBlocker() {
        if (blockerView != null && windowManager != null) {
            try {
                windowManager.removeView(blockerView);
            } catch (RuntimeException ignored) {
                // Already removed by the system.
            }
        }
        blockerView = null;
        blockerParams = null;
    }

    private void showCalibrationOverlay(String packageName) {
        if (calibrationOverlay != null || windowManager == null) {
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x33000000);

        TextView instruction = new TextView(this);
        instruction.setText("Drag the red circle over WhatsApp's ① View Once button, then tap SAVE.");
        instruction.setTextColor(Color.WHITE);
        instruction.setTextSize(16f);
        instruction.setGravity(Gravity.CENTER);
        instruction.setPadding(dp(12), dp(8), dp(12), dp(8));
        instruction.setBackground(roundRectBackground(0xE61F2933, dp(10)));
        FrameLayout.LayoutParams instructionParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        instructionParams.gravity = Gravity.TOP;
        instructionParams.setMargins(dp(12), dp(28), dp(12), 0);
        root.addView(instruction, instructionParams);

        TextView dragHandle = new TextView(this);
        dragHandle.setText("DRAG\n1×");
        dragHandle.setTextColor(Color.WHITE);
        dragHandle.setTextSize(13f);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setTypeface(null, android.graphics.Typeface.BOLD);
        dragHandle.setBackground(circleBackground(0xF2C62828, Color.WHITE, dp(3)));
        dragHandle.setElevation(dp(10));
        int handleSize = dp(Prefs.getBlockerSizeDp(this));
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(handleSize, handleSize);
        root.addView(dragHandle, handleParams);

        Button save = new Button(this);
        save.setText("SAVE");
        Button cancel = new Button(this);
        cancel.setText("CANCEL");
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(8), dp(4), dp(8), dp(4));
        controls.setBackground(roundRectBackground(0xE6FFFFFF, dp(12)));
        controls.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1f));
        controls.addView(save, new LinearLayout.LayoutParams(0, dp(52), 1f));

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        controlsParams.gravity = Gravity.BOTTOM;
        controlsParams.setMargins(dp(16), 0, dp(16), dp(28));
        root.addView(controls, controlsParams);

        final float[] touchStart = new float[4];
        dragHandle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStart[0] = event.getRawX();
                    touchStart[1] = event.getRawY();
                    touchStart[2] = v.getX();
                    touchStart[3] = v.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float targetX = touchStart[2] + event.getRawX() - touchStart[0];
                    float targetY = touchStart[3] + event.getRawY() - touchStart[1];
                    float maxX = Math.max(0, root.getWidth() - v.getWidth());
                    float maxY = Math.max(0, root.getHeight() - v.getHeight());
                    v.setX(Math.max(0, Math.min(maxX, targetX)));
                    v.setY(Math.max(0, Math.min(maxY, targetY)));
                    return true;
                default:
                    return true;
            }
        });

        save.setOnClickListener(v -> {
            if (root.getWidth() <= 0 || root.getHeight() <= 0) {
                return;
            }
            float centerX = dragHandle.getX() + dragHandle.getWidth() / 2f;
            float centerY = dragHandle.getY() + dragHandle.getHeight() / 2f;
            Prefs.saveCalibration(this, packageName,
                    centerX / root.getWidth(), centerY / root.getHeight());
            prefs.edit()
                    .putBoolean(Prefs.CALIBRATION_REQUESTED, false)
                    .remove(Prefs.CALIBRATION_PACKAGE)
                    .apply();
            hideCalibrationOverlay();
            Toast.makeText(this, "Calibration saved.", Toast.LENGTH_SHORT).show();
            handler.postDelayed(this::evaluateCurrentScreen, 200L);
        });

        cancel.setOnClickListener(v -> {
            prefs.edit()
                    .putBoolean(Prefs.CALIBRATION_REQUESTED, false)
                    .remove(Prefs.CALIBRATION_PACKAGE)
                    .apply();
            hideCalibrationOverlay();
            Toast.makeText(this, "Calibration cancelled.", Toast.LENGTH_SHORT).show();
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        try {
            windowManager.addView(root, params);
            calibrationOverlay = root;
            root.post(() -> {
                int size = dragHandle.getWidth();
                int width = root.getWidth();
                int height = root.getHeight();
                float nx = Prefs.hasCalibration(this, packageName)
                        ? Prefs.getCalibrationX(this, packageName) : 0.20f;
                float ny = Prefs.hasCalibration(this, packageName)
                        ? Prefs.getCalibrationY(this, packageName) : 0.76f;
                dragHandle.setX(Math.max(0, Math.min(width - size, nx * width - size / 2f)));
                dragHandle.setY(Math.max(0, Math.min(height - size, ny * height - size / 2f)));
            });
        } catch (RuntimeException ignored) {
            calibrationOverlay = null;
        }
    }

    private void hideCalibrationOverlay() {
        if (calibrationOverlay != null && windowManager != null) {
            try {
                windowManager.removeView(calibrationOverlay);
            } catch (RuntimeException ignored) {
                // Already removed.
            }
        }
        calibrationOverlay = null;
    }

    private GradientDrawable circleBackground(int fillColor, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fillColor);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private GradientDrawable roundRectBackground(int fillColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isTargetPackage(String packageName) {
        return WHATSAPP_PACKAGE.equals(packageName) || BUSINESS_PACKAGE.equals(packageName);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideBlocker();
        hideCalibrationOverlay();
        handler.postDelayed(this::evaluateCurrentScreen, 250L);
    }

    @Override
    public void onInterrupt() {
        hideBlocker();
        hideCalibrationOverlay();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hideBlocker();
        hideCalibrationOverlay();
        super.onDestroy();
    }
}

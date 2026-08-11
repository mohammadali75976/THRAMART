package com.thramart.setup2

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var appsBox: LinearLayout
    private var titleTapCount = 0
    private var lastTapMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        startEnforcement()
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.applyPolicies(this)
        KioskPolicy.enterSelfLockTask(this)
        refreshApps()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(16))
            setBackgroundColor(Color.rgb(245, 245, 245))
        }

        val title = TextView(this).apply {
            text = "THRAMART • SETUP 2"
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(14))
            setOnClickListener { handleAdminTap() }
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(this).apply {
            text = "Only approved apps are available"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(subtitle)

        appsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(appsBox)
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        if (!KioskPolicy.isDeviceOwner(this)) {
            root.addView(TextView(this).apply {
                text = "SETUP REQUIRED: install APK, remove phone accounts/work profiles, then run the Device Owner ADB command from README.md."
                textSize = 14f
                setTextColor(Color.RED)
                setPadding(dp(8), dp(12), dp(8), dp(12))
            })
        }

        setContentView(root)
    }

    private fun refreshApps() {
        appsBox.removeAllViews()
        val now = KioskPolicy.nowMinutes()
        val rules = RuleStore.loadRules(this).filter { it.isAllowedNow(now) }
        if (rules.isEmpty()) {
            appsBox.addView(TextView(this).apply {
                text = "No app is open at this time."
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(60), dp(10), dp(10))
            })
            return
        }

        rules.forEach { rule ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(8), dp(6), dp(8))
            }

            val icon = ImageView(this).apply {
                adjustViewBounds = true
                try {
                    setImageDrawable(packageManager.getApplicationIcon(rule.packageName))
                } catch (_: Exception) {
                }
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(56), dp(56)))

            val button = Button(this).apply {
                text = rule.label
                textSize = 17f
                isAllCaps = false
                gravity = Gravity.CENTER
                minimumHeight = dp(56)
                minHeight = dp(56)
                maxLines = 3
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnClickListener {
                    when (KioskPolicy.launchAllowedApp(this@MainActivity, rule.packageName)) {
                        KioskPolicy.LaunchResult.OPENED -> Unit
                        KioskPolicy.LaunchResult.NOT_ALLOWED -> Toast.makeText(
                            this@MainActivity, "App is not allowed", Toast.LENGTH_SHORT
                        ).show()
                        KioskPolicy.LaunchResult.OUTSIDE_SCHEDULE -> Toast.makeText(
                            this@MainActivity, "App is closed by scheduled time", Toast.LENGTH_SHORT
                        ).show()
                        KioskPolicy.LaunchResult.NOT_INSTALLED -> Toast.makeText(
                            this@MainActivity, "App is not installed or has no launcher", Toast.LENGTH_SHORT
                        ).show()
                        KioskPolicy.LaunchResult.FAILED -> Toast.makeText(
                            this@MainActivity, "App could not be opened", Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            row.addView(
                button,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                }
            )
            appsBox.addView(row)
        }
    }

    private fun handleAdminTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapMs > 2500) titleTapCount = 0
        lastTapMs = now
        titleTapCount++
        if (titleTapCount >= 7) {
            titleTapCount = 0
            showPinDialog()
        }
    }

    private fun showPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Admin PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Deep Settings")
            .setView(input)
            .setPositiveButton("Open") { _, _ ->
                if (input.text.toString() == RuleStore.getPin(this)) {
                    startActivity(Intent(this, AdminActivity::class.java))
                } else {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startEnforcement() {
        if (!KioskPolicy.isDeviceOwner(this)) return
        val intent = Intent(this, EnforcementService::class.java)
        try {
            startForegroundService(intent)
        } catch (_: Exception) {
        }
    }
}

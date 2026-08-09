package com.thramart.setup2

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class AdminActivity : Activity() {
    private lateinit var listBox: LinearLayout
    private val rulesByPackage = linkedMapOf<String, AppRule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadInstalledApps()
        buildUi()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun loadInstalledApps() {
        val saved = RuleStore.loadRules(this).associateBy { it.packageName }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }

        infos.forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg == packageName) return@forEach
            val label = info.loadLabel(packageManager).toString()
            rulesByPackage[pkg] = saved[pkg] ?: AppRule(pkg, label)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "DEEP SETTINGS"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(0, dp(8), 0, dp(6))
        })
        root.addView(TextView(this).apply {
            text = "Select which apps can run and set their daily opening time. Everything else stays locked."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), dp(12))
        })

        val changePin = Button(this).apply {
            text = "Change Admin PIN"
            isAllCaps = false
            minimumHeight = dp(52)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { showChangePin() }
        }
        root.addView(
            changePin,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val exitKiosk = Button(this).apply {
            text = "EXIT KIOSK / REMOVE DEVICE OWNER"
            textSize = 16f
            isAllCaps = false
            minimumHeight = dp(56)
            maxLines = 2
            setTextColor(Color.rgb(170, 0, 0))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { showExitKioskDialog() }
        }
        root.addView(
            exitKiosk,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        )

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(listBox)
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val save = Button(this).apply {
            text = "SAVE & APPLY KIOSK"
            textSize = 17f
            isAllCaps = false
            minimumHeight = dp(56)
            maxLines = 2
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener {
                RuleStore.saveRules(this@AdminActivity, rulesByPackage.values.toList())
                KioskPolicy.applyPolicies(this@AdminActivity)
                Toast.makeText(this@AdminActivity, "Saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        root.addView(
            save,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(root)
        rebuildRows()
    }

    private fun rebuildRows() {
        listBox.removeAllViews()
        rulesByPackage.values.forEach { rule ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }

            val check = CheckBox(this).apply {
                text = rule.label
                textSize = 16f
                isChecked = rule.enabled
                minHeight = dp(48)
                maxLines = 3
                setPadding(dp(2), dp(4), dp(2), dp(4))
                setOnCheckedChangeListener { _, checked -> rule.enabled = checked }
            }
            card.addView(
                check,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            val timeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val start = Button(this).apply {
                text = "From ${fmt(rule.startMinutes)}"
                textSize = 14f
                isAllCaps = false
                minimumHeight = dp(50)
                maxLines = 2
                setPadding(dp(6), dp(8), dp(6), dp(8))
                setOnClickListener {
                    pickTime(rule.startMinutes) { mins ->
                        rule.startMinutes = mins
                        text = "From ${fmt(mins)}"
                    }
                }
            }

            val end = Button(this).apply {
                text = "To ${fmt(rule.endMinutes)}"
                textSize = 14f
                isAllCaps = false
                minimumHeight = dp(50)
                maxLines = 2
                setPadding(dp(6), dp(8), dp(6), dp(8))
                setOnClickListener {
                    pickTime(rule.endMinutes) { mins ->
                        rule.endMinutes = mins
                        text = "To ${fmt(mins)}"
                    }
                }
            }

            timeRow.addView(
                start,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                }
            )
            timeRow.addView(
                end,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(4)
                }
            )
            card.addView(timeRow)
            listBox.addView(card)
        }
    }

    private fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            current / 60,
            current % 60,
            true
        ).show()
    }

    private fun fmt(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    private fun showExitKioskDialog() {
        val input = EditText(this).apply {
            hint = "Current Admin PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Exit Kiosk")
            .setMessage("This removes Thramart Setup 2 as Device Owner and exits kiosk mode. Apps are not deleted. Enter the current admin PIN to continue.")
            .setView(input)
            .setPositiveButton("REMOVE & EXIT") { _, _ ->
                if (input.text.toString() != RuleStore.getPin(this)) {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (KioskPolicy.exitKioskAndRemoveDeviceOwner(this)) {
                    Toast.makeText(this, "Kiosk Device Owner removed", Toast.LENGTH_LONG).show()
                    try {
                        val home = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(home)
                    } catch (_: Exception) {
                    }
                    finishAffinity()
                } else {
                    Toast.makeText(this, "Could not remove Device Owner", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChangePin() {
        val input = EditText(this).apply {
            hint = "New 4-8 digit PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Change Admin PIN")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..8 && pin.all { it.isDigit() }) {
                    RuleStore.setPin(this, pin)
                    Toast.makeText(this, "PIN changed", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Use 4-8 digits", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

package com.callershield.pro

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("caller_shield", Context.MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private var selectedTab = "Home"
    private var dialNumber = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == CONTACTS_PERMISSION_REQUEST) {
            val granted = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
            prefs.edit().putBoolean("block_unknown", granted).apply()
            Toast.makeText(
                this,
                if (granted) "Contact screening enabled" else "Contacts permission denied; unknown callers will not be blocked",
                Toast.LENGTH_LONG
            ).show()
            render()
        }
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
        }
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(buildNavigation())
        setContentView(root)

        when (selectedTab) {
            "Home" -> showHome()
            "Dialer" -> showDialer()
            "Reports" -> showReports()
            else -> showSettings()
        }
    }

    private fun showHome() {
        content.addView(label("CALLER SHIELD", 12f, MUTED, true))
        content.addView(label("Your calls. Your control.", 27f, WHITE, true), spaced(4, 0))
        content.addView(label("Private, on-device spam protection", 14f, MUTED), spaced(4, 20))

        val enabled = isScreeningEnabled()
        val protectionCard = card()
        protectionCard.addView(label(if (enabled) "PROTECTION ACTIVE" else "PROTECTION NOT ENABLED", 12f, if (enabled) MINT else AMBER, true))
        protectionCard.addView(label(
            if (enabled) "Your local spam list is screening incoming calls."
            else "Enable call screening to let Android check incoming calls against your local spam list.",
            15f, WHITE
        ), spaced(8, 0))
        val enableButton = button(if (enabled) "Screening is enabled" else "Enable call screening")
        enableButton.isEnabled = !enabled
        enableButton.setOnClickListener { requestScreeningRole() }
        protectionCard.addView(enableButton, spaced(14, 0))
        content.addView(protectionCard, spaced(18, 0))

        val reportCount = SpamReportStore.getReports(this).size
        val spamCount = SpamStore.getNumbers(this).size
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard(spamCount.toString(), "Blocked numbers"), LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(space(12), LinearLayout.LayoutParams(dp(12), 1))
        stats.addView(statCard(reportCount.toString(), "Your reports"), LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(stats, spaced(14, 0))

        val shortcutCard = card()
        shortcutCard.addView(label("Quick actions", 19f, WHITE, true))
        shortcutCard.addView(button("Report a spam caller").apply { setOnClickListener { selectedTab = "Reports"; render() } }, spaced(12, 0))
        shortcutCard.addView(button("Open dialer").apply { setOnClickListener { selectedTab = "Dialer"; render() } }, spaced(8, 0))
        content.addView(shortcutCard, spaced(14, 0))

        val tip = card()
        tip.addView(label("Stay safer", 18f, WHITE, true))
        tip.addView(label("Never share verification codes, passwords, or banking PINs with callers. Caller Shield cannot prevent phishing messages, account takeovers, or every scam.", 14f, MUTED), spaced(8, 0))
        content.addView(tip, spaced(14, 0))
    }

    private fun showDialer() {
        content.addView(label("DIALER", 12f, MUTED, true))
        content.addView(label("Make a call", 27f, WHITE, true), spaced(4, 20))
        content.addView(label("Calls are placed through your phone's system dialer.", 14f, MUTED), spaced(4, 18))

        val number = label(dialNumber.ifEmpty { "Enter a number" }, 27f, if (dialNumber.isEmpty()) MUTED else WHITE)
        number.gravity = Gravity.CENTER
        number.setPadding(0, dp(18), 0, dp(18))
        content.addView(number)

        val keypad = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            useDefaultMargins = false
        }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        keys.forEach { key ->
            val keyButton = button(key).apply {
                textSize = 22f
                setOnClickListener {
                    dialNumber += key
                    number.text = dialNumber
                }
            }
            keypad.addView(keyButton, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(58)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        content.addView(keypad, spaced(4, 0))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("Call").apply {
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(MINT))
            setTextColor(BACKGROUND)
            setOnClickListener {
                val value = dialNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                if (value.isBlank()) {
                    toast("Enter a phone number first")
                } else {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", value, null)))
                }
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        actions.addView(button("Delete").apply {
            setOnClickListener {
                dialNumber = dialNumber.dropLast(1)
                number.text = dialNumber.ifEmpty { "Enter a number" }
                number.setTextColor(if (dialNumber.isEmpty()) MUTED else WHITE)
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(10) })
        content.addView(actions, spaced(8, 0))
        content.addView(label("Emergency calls should always be made using the system phone app.", 12f, MUTED), spaced(14, 0))
    }

    private fun showReports() {
        content.addView(label("ON THIS DEVICE", 12f, MUTED, true))
        content.addView(label("Report a caller", 27f, WHITE, true), spaced(4, 18))
        content.addView(label("Reports are saved only on this device. No caller details are uploaded.", 14f, MUTED), spaced(4, 18))

        val form = card()
        val phoneInput = editText("Phone number")
        form.addView(phoneInput, spaced(2, 0))
        val categories = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Spam or sales", "Fraud or impersonation", "Robocall", "Other")
            )
        }
        form.addView(categories, spaced(8, 0))
        val noteInput = editText("Optional note (do not include personal or financial details)", false)
        form.addView(noteInput, spaced(8, 0))
        form.addView(button("Save report and block number").apply {
            setOnClickListener {
                val number = phoneInput.text.toString().trim()
                val digitCount = number.count { it.isDigit() }
                if (digitCount !in 7..15) {
                    toast("Enter a phone number with 7 to 15 digits")
                    return@setOnClickListener
                }
                val category = categories.selectedItem.toString()
                val note = noteInput.text.toString().trim().take(200)
                SpamStore.add(this@MainActivity, number)
                SpamReportStore.add(this@MainActivity, number, category, note)
                phoneInput.text.clear()
                noteInput.text.clear()
                toast("Report saved on this device; number added to your block list")
                render()
            }
        }, spaced(12, 0))
        content.addView(form, spaced(14, 0))

        content.addView(label("Your reports", 19f, WHITE, true), spaced(8, 16))
        val reports = SpamReportStore.getReports(this)
        if (reports.isEmpty()) {
            content.addView(label("No reports yet. Reports you save will appear here.", 14f, MUTED), spaced(8, 0))
        } else {
            reports.asReversed().forEach { report ->
                val item = card()
                item.addView(label(report.number, 17f, WHITE, true))
                item.addView(label("${report.category}  •  ${report.date}", 13f, MINT), spaced(5, 0))
                if (report.note.isNotBlank()) item.addView(label(report.note, 14f, MUTED), spaced(6, 0))
                content.addView(item, spaced(10, 0))
            }
        }
    }

    private fun showSettings() {
        content.addView(label("PRIVACY & SAFETY", 12f, MUTED, true))
        content.addView(label("Protection settings", 27f, WHITE, true), spaced(4, 20))
        val screeningCard = card()
        screeningCard.addView(label(
            if (isScreeningEnabled()) "Call screening is enabled" else "Call screening is off",
            16f, if (isScreeningEnabled()) MINT else AMBER, true
        ))
        screeningCard.addView(button("Manage Android call-screening access").apply {
            setOnClickListener { requestScreeningRole() }
        }, spaced(10, 0))
        content.addView(screeningCard, spaced(18, 0))

        val switches = card()
        switches.addView(label("Incoming calls", 18f, WHITE, true))
        addSwitch(switches, "Block numbers in my spam list", "block_spam", true)
        addSwitch(switches, "Block hidden / private numbers", "block_private", false)
        addSwitch(switches, "Block numbers not in contacts", "block_unknown", false)
        switches.addView(label(
            "Contact screening requires Contacts permission. Caller Shield checks a number locally only when a call is screened; it does not upload your address book.",
            12f, MUTED
        ), spaced(4, 0))
        content.addView(switches, spaced(14, 0))

        val list = card()
        list.addView(label("My block list", 18f, WHITE, true))
        val numbers = SpamStore.getNumbers(this)
        if (numbers.isEmpty()) {
            list.addView(label("No blocked numbers yet.", 14f, MUTED), spaced(8, 0))
        } else {
            numbers.sorted().forEach { number ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(label(number, 15f, WHITE), LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(button("Remove").apply {
                    setOnClickListener {
                        SpamStore.remove(this@MainActivity, number)
                        render()
                    }
                })
                list.addView(row, spaced(8, 0))
            }
        }
        content.addView(list, spaced(14, 0))

        val security = card()
        security.addView(label("Device security", 18f, WHITE, true))
        security.addView(label(
            "Keep Android and your apps updated, use a screen lock, install apps from trusted sources, and enable Google Play Protect. Caller Shield screens calls; it is not antivirus and cannot secure accounts or stop every form of hacking.",
            14f, MUTED
        ), spaced(8, 0))
        content.addView(security, spaced(14, 0))

        content.addView(label(
            "Privacy: block settings, block list, and reports are stored locally on this device. This app has no network permission and does not send reports to a shared community database.",
            12f, MUTED
        ), spaced(12, 0))
    }

    private fun buildNavigation(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(CARD)
        }
        listOf("Home", "Dialer", "Reports", "Settings").forEach { tab ->
            val item = TextView(this).apply {
                text = tab
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (selectedTab == tab) MINT else MUTED)
                setPadding(dp(2), dp(10), dp(2), dp(10))
                setOnClickListener { selectedTab = tab; render() }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return nav
    }

    private fun addSwitch(parent: LinearLayout, title: String, key: String, default: Boolean) {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val text = label(title, 14f, WHITE)
        line.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = android.widget.Switch(this).apply {
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, checked ->
                if (key == "block_unknown" && checked &&
                    checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
                ) {
                    isChecked = false
                    requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), CONTACTS_PERMISSION_REQUEST)
                    return@setOnCheckedChangeListener
                }
                prefs.edit().putBoolean(key, checked).apply()
            }
        }
        line.addView(toggle)
        parent.addView(line, spaced(10, 0))
    }

    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast("Call screening role requires Android 10 or newer")
            return
        }
        val manager = getSystemService(RoleManager::class.java)
        if (manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            if (manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                toast("Caller Shield is already enabled")
            } else {
                startActivity(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            }
        } else {
            toast("Call screening is not available on this device")
        }
    }

    private fun isScreeningEnabled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    private fun statCard(value: String, caption: String): LinearLayout {
        val card = card()
        card.addView(label(value, 27f, MINT, true))
        card.addView(label(caption, 12f, MUTED), spaced(4, 0))
        return card
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(CARD, 18)
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun button(title: String): Button = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 14f
        setTextColor(WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(BUTTON))
        minHeight = dp(46)
    }

    private fun editText(hintValue: String, singleLine: Boolean = true): EditText =
        EditText(this).apply {
            hint = hintValue
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            textSize = 15f
            isSingleLine = singleLine
            if (!singleLine) minLines = 2
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(BACKGROUND, 10)
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun spaced(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun space(height: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val CONTACTS_PERMISSION_REQUEST = 41
        const val BACKGROUND = 0xFF0B1218.toInt()
        const val CARD = 0xFF14212A.toInt()
        const val BUTTON = 0xFF223640.toInt()
        const val WHITE = 0xFFF4F7F9.toInt()
        const val MUTED = 0xFF9AADB8.toInt()
        const val MINT = 0xFF62E6B5.toInt()
        const val AMBER = 0xFFFFC46B.toInt()
    }
}

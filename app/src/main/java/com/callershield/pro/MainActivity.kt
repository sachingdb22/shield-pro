package com.callershield.pro

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
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
import java.io.IOException
import java.util.Date

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("caller_shield", Context.MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private var updateStatusView: TextView? = null
    private var selectedTab = "Home"
    private var dialNumber = ""
    private var updateStatus = "Check GitHub for the latest version."
    private var downloadId: Long? = null
    private var installPromptId: Long? = null
    private var activityResumed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val downloadPoller = object : Runnable {
        override fun run() {
            val id = downloadId ?: return
            try {
                val progress = AppUpdateManager.getDownloadProgress(this@MainActivity, id)
                when (progress?.status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> {
                        updateStatus = if (progress.percent > 0) {
                            "Downloading update… ${progress.percent}%"
                        } else {
                            "Downloading update…"
                        }
                        updateStatusView?.text = updateStatus
                        mainHandler.postDelayed(this, DOWNLOAD_POLL_INTERVAL)
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        downloadId = null
                        prefs.edit().remove(KEY_ACTIVE_DOWNLOAD).putLong(KEY_READY_DOWNLOAD, id).apply()
                        updateStatus = "Update downloaded. Android will ask you to confirm installation."
                        render()
                        if (activityResumed) promptInstall(id)
                    }
                    DownloadManager.STATUS_FAILED, null -> {
                        downloadId = null
                        prefs.edit().remove(KEY_ACTIVE_DOWNLOAD).apply()
                        updateStatus = "Download failed. Check your connection and try again."
                        render()
                    }
                }
            } catch (error: IOException) {
                Log.e(TAG, "Could not check update download status", error)
                downloadId = null
                prefs.edit().remove(KEY_ACTIVE_DOWNLOAD).apply()
                updateStatus = "Could not check the download. Please try again."
                render()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (::content.isInitialized) {
            installPromptId = null
            render()
            resumePendingInstall()
            resumeUpdateDownload()
        }
    }

    override fun onPause() {
        activityResumed = false
        mainHandler.removeCallbacks(downloadPoller)
        super.onPause()
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
        } else if (requestCode == CALL_LOG_PERMISSION_REQUEST) {
            val granted = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
            if (!granted) toast("Call log access denied; recent calls are hidden")
            render()
        }
    }

    private fun render() {
        updateStatusView = null
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

        showRecentCalls()

        val shortcutCard = card()
        shortcutCard.addView(label("Quick actions", 19f, WHITE, true))
        shortcutCard.addView(button("⚑   Report a spam caller").apply { setOnClickListener { selectedTab = "Reports"; render() } }, spaced(12, 0))
        shortcutCard.addView(button("☎   Open dialer").apply { setOnClickListener { selectedTab = "Dialer"; render() } }, spaced(8, 0))
        content.addView(shortcutCard, spaced(14, 0))

        val tip = card()
        tip.addView(label("Stay safer", 18f, WHITE, true))
        tip.addView(label("Never share verification codes, passwords, or banking PINs with callers. Caller Shield cannot prevent phishing messages, account takeovers, or every scam.", 14f, MUTED), spaced(8, 0))
        content.addView(tip, spaced(14, 0))
    }

    private fun showRecentCalls() {
        val history = card()
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label("Recent calls", 19f, WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(label("CALL LOG", 10f, MUTED, true))
        history.addView(heading)

        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            history.addView(label(
                "View your latest calls here. Call history stays on this device.",
                14f, MUTED
            ), spaced(8, 0))
            history.addView(button("◷   Allow call log access").apply {
                setOnClickListener {
                    requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), CALL_LOG_PERMISSION_REQUEST)
                }
            }, spaced(12, 0))
        } else {
            val result = loadRecentCalls()
            when {
                result.error != null -> history.addView(
                    label(result.error, 14f, AMBER), spaced(8, 0)
                )
                result.calls.isEmpty() -> history.addView(
                    label("No recent calls to show.", 14f, MUTED), spaced(8, 0)
                )
                else -> result.calls.forEachIndexed { index, call ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(12), 0, dp(12))
                    }
                    val callIcon = label(call.icon, 20f, call.iconColor, true).apply {
                        gravity = Gravity.CENTER
                        background = rounded(BUTTON, 14)
                        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                            rightMargin = dp(12)
                        }
                    }
                    row.addView(callIcon)

                    val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    details.addView(label(call.name.ifBlank { call.number.ifBlank { "Private number" } }, 15f, WHITE, true))
                    details.addView(label("${call.type}  ·  ${call.date}", 12f, MUTED), spaced(4, 0))
                    row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                    history.addView(row)
                    if (index < result.calls.lastIndex) {
                        history.addView(View(this).apply {
                            setBackgroundColor(BUTTON)
                            layoutParams = LinearLayout.LayoutParams(-1, dp(1))
                        })
                    }
                }
            }
        }
        content.addView(history, spaced(14, 0))
    }

    private fun loadRecentCalls(): CallHistoryResult {
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE
        )
        return try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return CallHistoryResult(error = "Could not read recent calls. Try again later.")
            cursor.use { readRecentCalls(it) }
        } catch (error: SecurityException) {
            Log.e(TAG, "Call log access was denied", error)
            CallHistoryResult(error = "Call log access is unavailable. Check app permissions in Settings.")
        }
    }

    private fun readRecentCalls(cursor: Cursor): CallHistoryResult {
        val calls = mutableListOf<RecentCall>()
        val numberColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val nameColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val typeColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
        while (cursor.moveToNext() && calls.size < RECENT_CALL_LIMIT) {
            val type = cursor.getInt(typeColumn)
            val callType = when (type) {
                CallLog.Calls.INCOMING_TYPE -> RecentCall("Incoming", "↙", MINT)
                CallLog.Calls.OUTGOING_TYPE -> RecentCall("Outgoing", "↗", MINT)
                CallLog.Calls.MISSED_TYPE -> RecentCall("Missed", "↙", AMBER)
                CallLog.Calls.REJECTED_TYPE -> RecentCall("Declined", "↙", AMBER)
                CallLog.Calls.BLOCKED_TYPE -> RecentCall("Blocked", "⊘", AMBER)
                else -> RecentCall("Call", "☎", MINT)
            }
            calls.add(callType.copy(
                number = cursor.getString(numberColumn).orEmpty(),
                name = cursor.getString(nameColumn).orEmpty(),
                date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(cursor.getLong(dateColumn)))
            ))
        }
        return CallHistoryResult(calls = calls)
    }

    private fun showDialer() {
        content.addView(label("DIALER", 12f, MUTED, true))
        content.addView(label("Make a call", 27f, WHITE, true), spaced(4, 6))
        content.addView(label("Calls are placed through your phone's system dialer.", 14f, MUTED), spaced(4, 16))

        val numberCard = card()
        numberCard.orientation = LinearLayout.HORIZONTAL
        numberCard.gravity = Gravity.CENTER_VERTICAL
        val number = label(dialNumber.ifEmpty { "Enter a number" }, 27f, if (dialNumber.isEmpty()) MUTED else WHITE, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.START
        }
        numberCard.addView(number, LinearLayout.LayoutParams(0, dp(64), 1f))
        val deleteButton = label("⌫", 22f, MUTED, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Delete last digit; long press to clear"
            background = rounded(BUTTON, 16)
            setOnClickListener {
                dialNumber = dialNumber.dropLast(1)
                updateDialedNumber(number)
            }
            setOnLongClickListener {
                dialNumber = ""
                updateDialedNumber(number)
                true
            }
        }
        numberCard.addView(deleteButton, LinearLayout.LayoutParams(dp(54), dp(48)).apply {
            leftMargin = dp(8)
        })
        content.addView(numberCard, spaced(0, 14))

        val keypad = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            useDefaultMargins = false
        }
        val keys = listOf(
            "1" to "", "2" to "ABC", "3" to "DEF",
            "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
            "*" to "", "0" to "+", "#" to ""
        )
        keys.forEach { key ->
            val keyButton = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(CARD, 24)
                isClickable = true
                isFocusable = true
                foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x3371F2C2),
                    null,
                    rounded(WHITE, 24)
                )
                setOnClickListener {
                    dialNumber += key.first
                    updateDialedNumber(number)
                }
            }
            keyButton.addView(label(key.first, 24f, WHITE, true).apply { gravity = Gravity.CENTER })
            if (key.second.isNotEmpty()) {
                keyButton.addView(label(key.second, 9f, MUTED, true).apply {
                    gravity = Gravity.CENTER
                    letterSpacing = 0.12f
                })
            } else {
                keyButton.addView(label(" ", 9f, MUTED).apply { gravity = Gravity.CENTER })
            }
            keypad.addView(keyButton, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(68)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            })
        }
        content.addView(keypad, spaced(4, 0))

        val callButton = button("☎     Call").apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
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
        }
        content.addView(callButton, spaced(10, 0).apply { height = dp(58) })
        content.addView(label("Emergency calls should always be made using the system phone app.", 12f, MUTED), spaced(12, 0))
    }

    private fun updateDialedNumber(number: TextView) {
        number.text = dialNumber.ifEmpty { "Enter a number" }
        number.setTextColor(if (dialNumber.isEmpty()) MUTED else WHITE)
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

        val updates = card()
        updates.addView(label("App updates", 18f, WHITE, true))
        updates.addView(label("Current version ${BuildConfig.VERSION_NAME}", 13f, MUTED), spaced(5, 0))
        val statusView = label(updateStatus, 13f, MUTED)
        updateStatusView = statusView
        updates.addView(statusView, spaced(8, 0))
        updates.addView(button("↻   Check for updates").apply {
            setOnClickListener { checkForUpdates() }
        }, spaced(12, 0))
        val readyDownload = prefs.getLong(KEY_READY_DOWNLOAD, INVALID_DOWNLOAD_ID)
        if (readyDownload != INVALID_DOWNLOAD_ID) {
            updates.addView(button("Install downloaded update").apply {
                setOnClickListener { promptInstall(readyDownload) }
            }, spaced(8, 0))
        }
        content.addView(updates, spaced(14, 0))

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
            "Privacy: block settings, block list, and reports are stored locally on this device. Network access is used only to check for and download app updates; reports and contacts are never uploaded.",
            12f, MUTED
        ), spaced(12, 0))
    }

    private fun checkForUpdates() {
        updateStatus = "Checking GitHub Releases…"
        render()
        Thread {
            try {
                val release = AppUpdateManager.findUpdate(BuildConfig.VERSION_NAME)
                runOnUiThread {
                    if (release == null) {
                        updateStatus = "You're using the latest version."
                        render()
                    } else {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Update ${release.tag} available")
                            .setMessage("Download the update from GitHub? Android will ask you to confirm installation.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Download") { _, _ -> downloadUpdate(release) }
                            .show()
                    }
                }
            } catch (error: AppUpdateManager.NoPublishedReleaseException) {
                runOnUiThread {
                    updateStatus = error.message ?: "No GitHub release is available yet."
                    render()
                }
            } catch (error: IOException) {
                Log.e(TAG, "Could not check GitHub for updates", error)
                runOnUiThread {
                    updateStatus = error.message ?: "Could not check for updates. Try again."
                    render()
                }
            }
        }.start()
    }

    private fun downloadUpdate(release: AppRelease) {
        try {
            val id = AppUpdateManager.enqueueDownload(this, release)
            downloadId = id
            prefs.edit()
                .putLong(KEY_ACTIVE_DOWNLOAD, id)
                .remove(KEY_READY_DOWNLOAD)
                .apply()
            updateStatus = "Downloading update…"
            render()
            mainHandler.removeCallbacks(downloadPoller)
            mainHandler.post(downloadPoller)
        } catch (error: IOException) {
            Log.e(TAG, "Could not start update download", error)
            updateStatus = error.message ?: "Could not start the update download."
            render()
        } catch (error: SecurityException) {
            Log.e(TAG, "Update download was not allowed", error)
            updateStatus = "Android could not start the download. Check device settings."
            render()
        }
    }

    private fun resumeUpdateDownload() {
        val id = prefs.getLong(KEY_ACTIVE_DOWNLOAD, INVALID_DOWNLOAD_ID)
        if (id == INVALID_DOWNLOAD_ID) return
        downloadId = id
        mainHandler.removeCallbacks(downloadPoller)
        mainHandler.post(downloadPoller)
    }

    private fun resumePendingInstall() {
        val id = prefs.getLong(KEY_PENDING_INSTALL, INVALID_DOWNLOAD_ID)
        if (id == INVALID_DOWNLOAD_ID) return
        prefs.edit().remove(KEY_PENDING_INSTALL).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
            installDownloadedUpdate(id)
        } else {
            installPromptId = null
            updateStatus = "Allow installs from Caller Shield in Android Settings, then tap Install downloaded update."
            render()
        }
    }

    private fun promptInstall(id: Long) {
        if (installPromptId == id) return
        installPromptId = id
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Allow app updates")
                .setMessage("Allow Caller Shield to install updates from this source in Android Settings. Android will still ask you to confirm the installation.")
                .setNegativeButton("Not now") { _, _ -> installPromptId = null }
                .setPositiveButton("Open settings") { _, _ ->
                    prefs.edit().putLong(KEY_PENDING_INSTALL, id).apply()
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }
                .show()
            return
        }
        installDownloadedUpdate(id)
    }

    private fun installDownloadedUpdate(id: Long) {
        try {
            val apkFile = AppUpdateManager.getDownloadedApkFile(this)
            val archiveInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                ?: throw IOException("The downloaded APK is invalid. Download the update again.")
            if (archiveInfo.packageName != packageName) {
                throw IOException("The downloaded APK is not Caller Shield Pro.")
            }
            val installedInfo = packageManager.getPackageInfo(packageName, 0)
            val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installedInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                installedInfo.versionCode.toLong()
            }
            val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }
            if (archiveVersionCode <= installedVersionCode) {
                throw IOException("The update APK must have a higher version code.")
            }
            val apkUri = AppUpdateManager.getDownloadedUri(this, id)
                ?: throw IOException("Downloaded APK is unavailable. Download the update again.")
            val installIntent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(installIntent)
        } catch (error: IOException) {
            Log.e(TAG, "Could not open downloaded update", error)
            installPromptId = null
            updateStatus = error.message ?: "Could not open the downloaded update."
            render()
        } catch (error: SecurityException) {
            Log.e(TAG, "Android denied opening the update installer", error)
            installPromptId = null
            updateStatus = "Android blocked the installer. Check install permission in Settings."
            render()
        } catch (error: android.content.ActivityNotFoundException) {
            Log.e(TAG, "No Android package installer is available", error)
            installPromptId = null
            updateStatus = "No package installer is available on this device."
            render()
        }
    }

    private fun buildNavigation(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(CARD)
        }
        listOf(
            "Home" to "⌂",
            "Dialer" to "☎",
            "Reports" to "⚑",
            "Settings" to "⚙"
        ).forEach { (tab, icon) ->
            val selected = selectedTab == tab
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(6), dp(2), dp(6))
                if (selected) background = rounded(0x263DE8B0, 14)
                setOnClickListener { selectedTab = tab; render() }
            }
            item.addView(label(icon, 19f, if (selected) MINT else MUTED, true).apply {
                gravity = Gravity.CENTER
            })
            item.addView(label(tab, 11f, if (selected) MINT else MUTED, selected).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
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

    private data class RecentCall(
        val type: String,
        val icon: String,
        val iconColor: Int,
        val number: String = "",
        val name: String = "",
        val date: String = ""
    )

    private data class CallHistoryResult(
        val calls: List<RecentCall> = emptyList(),
        val error: String? = null
    )

    private companion object {
        const val CONTACTS_PERMISSION_REQUEST = 41
        const val CALL_LOG_PERMISSION_REQUEST = 42
        const val RECENT_CALL_LIMIT = 8
        const val DOWNLOAD_POLL_INTERVAL = 1_000L
        const val INVALID_DOWNLOAD_ID = -1L
        const val KEY_ACTIVE_DOWNLOAD = "active_update_download"
        const val KEY_READY_DOWNLOAD = "ready_update_download"
        const val KEY_PENDING_INSTALL = "pending_update_install"
        const val TAG = "CallerShield"
        const val BACKGROUND = 0xFF0B1218.toInt()
        const val CARD = 0xFF14212A.toInt()
        const val BUTTON = 0xFF223640.toInt()
        const val WHITE = 0xFFF4F7F9.toInt()
        const val MUTED = 0xFF9AADB8.toInt()
        const val MINT = 0xFF62E6B5.toInt()
        const val AMBER = 0xFFFFC46B.toInt()
    }
}

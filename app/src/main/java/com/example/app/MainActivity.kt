package com.example.app

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    companion object {
        private const val PREFS_NAME = "IMEGuardianPrefs"
        private const val KEY_SELECTED_IME = "selected_ime_id"
        private const val KEY_TUTORIAL_READ = "tutorial_read"
        private const val KEY_NOTIFICATION_PERMISSION_PREV = "notification_permission_prev"

        val PREDEFINED_IME_LIST = listOf(
            Triple("搜狗输入法", "com.sohu.inputmethod.sogou", false),
            Triple("百度输入法", "com.baidu.input", false),
            Triple("讯飞输入法", "com.iflytek.inputmethod", false),
            Triple("谷歌拼音输入法（旧版谷歌输入法）", "com.google.android.inputmethod.latin", false),
            Triple("Gboard（新版谷歌输入法）", "com.google.android.apps.inputmethod.latin", false),
            Triple("系统输入法", "com.monet.inputmethod.hyer", true)
        )
    }

    private lateinit var tvCurrentIme: TextView
    private lateinit var tvSelectedIme: TextView
    private lateinit var tvNotificationStatus: TextView
    private lateinit var btnSelectIme: Button
    private lateinit var btnSwitchIme: Button
    private lateinit var btnEnableIme: Button
    private lateinit var btnNotificationPermission: Button
    private lateinit var btnViewTutorial: Button
    private lateinit var btnRecognizableIme: Button
    private val mainHandler = Handler(Looper.getMainLooper())

    private val inputMethodReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.intent.action.INPUT_METHOD_CHANGED") {
                runOnUiThread {
                    updateCurrentImeDisplay()
                    updateNotificationStatus()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvCurrentIme = findViewById(R.id.tv_current_ime)
        tvSelectedIme = findViewById(R.id.tv_selected_ime)
        tvNotificationStatus = findViewById(R.id.tv_notification_status)
        btnSelectIme = findViewById(R.id.btn_select_ime)
        btnSwitchIme = findViewById(R.id.btn_switch_ime)
        btnEnableIme = findViewById(R.id.btn_enable_ime)
        btnNotificationPermission = findViewById(R.id.btn_notification_permission)
        btnViewTutorial = findViewById(R.id.btn_view_tutorial)
        btnRecognizableIme = findViewById(R.id.btn_recognizable_ime)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentGranted = isNotificationPermissionGranted()
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_PREV, currentGranted).apply()

        updateCurrentImeDisplay()
        updateSelectedImeDisplay()

        val tutorialRead = prefs.getBoolean(KEY_TUTORIAL_READ, false)
        if (!tutorialRead) {
            showFirstTutorialDialog()
        } else {
            if (prefs.getString(KEY_SELECTED_IME, null) == null) {
                autoShowInputMethodPicker()
            }
        }

        btnSelectIme.setOnClickListener { showInputMethodPickerDialog() }
        btnSwitchIme.setOnClickListener { showInputMethodPickerForSwitch() }
        btnEnableIme.setOnClickListener { showEnableImeDialog() }  // 使用自定义启用对话框
        btnNotificationPermission.setOnClickListener {
            if (isNotificationPermissionGranted()) {
                tvNotificationStatus.text = "✅ 通知权限已开启"
            } else {
                jumpToAppSettings()
            }
        }
        btnViewTutorial.setOnClickListener { showDetailTutorialDialog() }
        btnRecognizableIme.setOnClickListener { showRecognizableInputMethodsDialog() }

        updateNotificationStatus()

        val filter = IntentFilter("android.intent.action.INPUT_METHOD_CHANGED")
        registerReceiver(inputMethodReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(inputMethodReceiver)
        } catch (e: Exception) { /* ignore */ }
    }

    override fun onResume() {
        super.onResume()
        updateCurrentImeDisplay()
        updateNotificationStatus()
        updateSelectedImeDisplay()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_TUTORIAL_READ, false)) {
            if (prefs.getString(KEY_SELECTED_IME, null) == null && isNotificationPermissionGranted()) {
                autoShowInputMethodPicker()
            }
        }

        val prevGranted = prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_PREV, false)
        val currentGranted = isNotificationPermissionGranted()
        if (currentGranted && !prevGranted) {
            prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_PREV, true).apply()
            showNotificationPermissionGrantedDialog()
        }
        if (prevGranted != currentGranted) {
            prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_PREV, currentGranted).apply()
        }
    }

    // ========== 通知权限开启后的引导对话框 ==========
    private fun showNotificationPermissionGrantedDialog() {
        AlertDialog.Builder(this)
            .setTitle("通知权限已开启")
            .setMessage("通知权限已开启，为了确保本工具能正常通知您，我们将启用一项功能。")
            .setPositiveButton("下一步") { _, _ ->
                checkOverlayPermission()
            }
            .setNegativeButton("稍后", null)
            .setCancelable(false)
            .show()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("权限已开启")
                    .setMessage("「允许显示在其他应用的上层」权限已开启，无需操作。")
                    .setPositiveButton("确定", null)
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("允许后台弹出")
                    .setMessage("请开启「允许显示在其他应用的上层」权限，以确保本工具能及时通知您。")
                    .setPositiveButton("去开启") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton("稍后", null)
                    .setCancelable(false)
                    .show()
            }
        } else {
            AlertDialog.Builder(this)
                .setTitle("权限已开启")
                .setMessage("您的系统版本无需额外设置。")
                .setPositiveButton("确定", null)
                .show()
        }
    }

    // ========== 首次强制教程 ==========
    private fun showFirstTutorialDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tutorial, null)
        val tvContent = dialogView.findViewById<TextView>(R.id.tv_tutorial_content)
        val btnBack = dialogView.findViewById<Button>(R.id.btn_tutorial_back)
        val btnTabNotification = dialogView.findViewById<Button>(R.id.btn_tab_notification)
        val btnTabSwitch = dialogView.findViewById<Button>(R.id.btn_tab_switch)
        btnTabNotification.visibility = View.GONE
        btnTabSwitch.visibility = View.GONE

        tvContent.text = "📖 首次使用必读\n\n为了在输入法被切换时能及时通知您，请务必开启通知权限。\n\n操作步骤：\n1. 点击下方「我已阅读并理解」\n2. 在弹窗中选择「去开启」\n3. 在应用信息界面点击「通知」\n4. 打开「允许通知」开关\n5. 勾选所有提醒方式（锁屏、横幅、悬浮等）\n\n请仔细阅读，确认后点击下方按钮。"

        val builder = AlertDialog.Builder(this).setView(dialogView).setCancelable(false)
        val dialog = builder.create()

        btnBack.isEnabled = false
        btnBack.text = "请等待..."
        val waitTime = (1500 + (Math.random() * 2550)).toLong()
        mainHandler.postDelayed({
            btnBack.isEnabled = true
            btnBack.text = "我已阅读并理解"
        }, waitTime)

        btnBack.setOnClickListener {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_TUTORIAL_READ, true).apply()
            dialog.dismiss()
            showTransitionDialog()
        }
        dialog.show()
    }

    // ========== 过渡窗口 ==========
    private fun showTransitionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_transition, null)
        val builder = AlertDialog.Builder(this)
            .setTitle("📌 准备开启通知权限")
            .setView(dialogView)
            .setPositiveButton("去开启", null)
            .setNegativeButton("稍后") { _, _ ->
                autoShowInputMethodPicker()
            }
            .setCancelable(false)

        val dialog = builder.create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        dialog.show()
    }

    // ========== 自动弹出输入法选择 ==========
    private fun autoShowInputMethodPicker() {
        findViewById<View>(android.R.id.content).postDelayed({
            showInputMethodPickerDialog()
        }, 300)
    }

    // ========== 仅保存选择的输入法（保护） ==========
    private fun showInputMethodPickerDialog() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imeList = imm.inputMethodList
        if (imeList.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("未检测到任何输入法，请在系统设置中添加。")
                .setPositiveButton("确定", null)
                .show()
            return
        }
        val imeNames = Array(imeList.size) { i ->
            "${imeList[i].loadLabel(packageManager)} (包名: ${imeList[i].id})"
        }
        val imeIds = Array(imeList.size) { i -> imeList[i].id }
        AlertDialog.Builder(this)
            .setTitle("请选择要保护的输入法")
            .setItems(imeNames) { _, which ->
                val selectedId = imeIds[which]
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_SELECTED_IME, selectedId).apply()
                updateSelectedImeDisplay()
                AlertDialog.Builder(this)
                    .setTitle("已保存")
                    .setMessage("已选择输入法：${getImeLabel(selectedId)}\n包名：$selectedId\n\n当检测到输入法被切换时，App会通知您手动切换回来。")
                    .setPositiveButton("知道了", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ========== 切换输入法 ==========
    private fun showInputMethodPickerForSwitch() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
            Toast.makeText(this, "请在弹出窗口中选择要切换的输入法", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("无法弹出输入法选择器，请前往系统设置手动切换。")
                .setPositiveButton("去设置") { _, _ ->
                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    // ========== 自定义启用输入法对话框（使用系统 API 获取启用状态） ==========
    private fun showEnableImeDialog() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imeList = imm.inputMethodList
        if (imeList.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("未检测到任何输入法。")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        // 使用 InputMethodManager.getEnabledInputMethodList() 获取已启用列表（与系统设置一致）
        val enabledList = try {
            imm.enabledInputMethodList.map { it.id }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }

        // 创建自定义视图
        val scrollView = ScrollView(this)
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        for (info in imeList) {
            val label = info.loadLabel(packageManager).toString()
            val id = info.id
            val currentlyEnabled = enabledList.contains(id)

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
            }

            // 包名显示格式：包名：com...（自动换行，最多两行）
            val textView = TextView(this).apply {
                text = "$label\n包名：$id"
                textSize = 16f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val enableButton = Button(this).apply {
                text = if (currentlyEnabled) "已启用" else "启用"
                isEnabled = !currentlyEnabled
                setOnClickListener {
                    if (!currentlyEnabled) {
                        val token = currentFocus?.windowToken
                        if (token != null) {
                            try {
                                imm.setInputMethod(token, id)
                                Toast.makeText(this@MainActivity, "✅ 已启用 $label", Toast.LENGTH_SHORT).show()
                                showEnableImeDialog()  // 刷新对话框
                            } catch (e: SecurityException) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("启用失败")
                                    .setMessage("无法直接启用，请前往系统设置手动启用。")
                                    .setPositiveButton("我知道了", null)
                                    .show()
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "启用失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "无法获取当前窗口，请重试", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            itemLayout.addView(textView)
            itemLayout.addView(enableButton)
            linearLayout.addView(itemLayout)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(0xFFE0E0E0.toInt())
            }
            linearLayout.addView(divider)
        }

        scrollView.addView(linearLayout)

        AlertDialog.Builder(this)
            .setTitle("启用输入法")
            .setView(scrollView)
            .setPositiveButton("关闭") { _, _ -> }
            .show()
    }

    // ========== 显示预定义的输入法列表（可识别守护） ==========
    private fun showRecognizableInputMethodsDialog() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val installedImeIds = imm.inputMethodList.map { it.id }.toSet()

        val enabledList = try {
            imm.enabledInputMethodList.map { it.id }.toList()
        } catch (e: Exception) {
            emptyList()
        }

        val sb = StringBuilder()
        for ((name, pkg, _) in PREDEFINED_IME_LIST) {
            val installed = installedImeIds.contains(pkg)
            val enabled = installed && enabledList.contains(pkg)
            sb.append("• $name\n")
            sb.append("  包名：$pkg\n")  // 包名和冒号同行
            sb.append("  状态: ${if (installed) "已安装" else "未安装"}")
            if (installed) {
                sb.append("，${if (enabled) "已启用" else "未启用"}")
            }
            sb.append("\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("可识别守护的输入法")
            .setMessage(sb.toString().trimEnd())
            .setPositiveButton("确定", null)
            .show()
    }

    // ========== 详细教程对话框 ==========
    private fun showDetailTutorialDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tutorial, null)
        val tvContent = dialogView.findViewById<TextView>(R.id.tv_tutorial_content)
        val btnBack = dialogView.findViewById<Button>(R.id.btn_tutorial_back)
        val btnTabNotification = dialogView.findViewById<Button>(R.id.btn_tab_notification)
        val btnTabSwitch = dialogView.findViewById<Button>(R.id.btn_tab_switch)

        btnBack.isEnabled = true
        btnBack.text = "返回"

        val notificationTutorial = """
            1. 点击主页「打开通知权限」按钮，进入应用信息界面。
               📱 系统会打开设置页面。

            2. 在应用信息界面，找到并点击「通知」选项。
               ⚙️ 通常位于页面中部。

            3. 打开「允许通知」开关（开启后变为蓝色）。
               ✅ 确保开关处于开启状态。

            4. 展开「提醒方式」或「通知类别」，勾选所有选项：
               🔔 锁屏通知（显示在锁定屏幕）
               📲 横幅通知（屏幕顶部弹出）
               💬 悬浮通知（覆盖在其他应用上）
               （如果有其他选项，也一并开启）

            5. 返回 App，此时通知权限已完全开启。
               🎉 您将能正常收到输入法切换提醒。

            💡 提示：不同手机界面略有差异，请根据实际情况操作。
        """.trimIndent()

        val switchTutorial = """
            📘 如何切换输入法

            1. 点击主页的「切换输入法」按钮，系统会弹出输入法选择器。
            2. 在弹出的列表中，选择您想要使用的输入法。
            3. 系统将自动切换到所选输入法。

            💡 提示：如果无法弹出选择器，请前往系统设置手动切换。
        """.trimIndent()

        tvContent.text = notificationTutorial
        btnTabNotification.setOnClickListener {
            tvContent.text = notificationTutorial
            btnBack.isEnabled = true
            btnBack.text = "返回"
        }
        btnTabSwitch.setOnClickListener {
            tvContent.text = switchTutorial
            btnBack.isEnabled = true
            btnBack.text = "返回"
        }

        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(true)

        btnBack.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun jumpToAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun updateNotificationStatus() {
        if (isNotificationPermissionGranted()) {
            tvNotificationStatus.text = "✅ 通知权限已开启"
        } else {
            tvNotificationStatus.text = "❌ 通知权限未开启"
        }
    }

    private fun getCurrentImeId(): String {
        return Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: "未知"
    }

    private fun getImeLabel(imeId: String): String {
        if (imeId == "未知") return imeId
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        for (info in imm.inputMethodList) {
            if (info.id == imeId) return info.loadLabel(packageManager).toString()
        }
        return imeId
    }

    // 当前输入法显示：包名与冒号同行，自动换行
    private fun updateCurrentImeDisplay() {
        val currentId = getCurrentImeId()
        val label = getImeLabel(currentId)
        tvCurrentIme.text = if (currentId == "未知") {
            "当前输入法：未检测"
        } else {
            "当前输入法：$label\n包名：$currentId"
        }
        tvCurrentIme.maxLines = 3 // 允许三行（名称+包名）
        tvCurrentIme.ellipsize = android.text.TextUtils.TruncateAt.END
    }

    // 已选择输入法显示：包名与冒号同行，自动换行
    private fun updateSelectedImeDisplay() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedId = prefs.getString(KEY_SELECTED_IME, null)
        tvSelectedIme.text = if (selectedId != null) {
            val label = getImeLabel(selectedId)
            "已选择的输入法：$label\n包名：$selectedId"
        } else {
            "已选择的输入法：无"
        }
        tvSelectedIme.maxLines = 3
        tvSelectedIme.ellipsize = android.text.TextUtils.TruncateAt.END
    }
}
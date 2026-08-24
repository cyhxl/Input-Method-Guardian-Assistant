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
    }

    private lateinit var tvCurrentIme: TextView
    private lateinit var tvSelectedIme: TextView
    private lateinit var tvNotificationStatus: TextView
    private lateinit var btnSelectIme: Button
    private lateinit var btnSwitchIme: Button
    private lateinit var btnEnableIme: Button
    private lateinit var btnPermission: Button
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
        btnPermission = findViewById(R.id.btn_permission)
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
        btnEnableIme.setOnClickListener { showInputMethodEnableStatusDialog() }
        btnPermission.setOnClickListener { showPermissionDialog() }
        btnViewTutorial.setOnClickListener { showDetailTutorialDialog() }
        btnRecognizableIme.setOnClickListener { showAllInputMethodsDialog() }

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

    // ========== 权限对话框 ==========
    private fun showPermissionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_permission, null)
        val btnReturn = dialogView.findViewById<Button>(R.id.btn_permission_return)
        val btnNotification = dialogView.findViewById<Button>(R.id.btn_permission_notification)
        val btnOverlay = dialogView.findViewById<Button>(R.id.btn_permission_overlay)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnReturn.setOnClickListener { dialog.dismiss() }
        btnNotification.setOnClickListener {
            dialog.dismiss()
            jumpToAppSettings()
        }
        btnOverlay.setOnClickListener {
            dialog.dismiss()
            openOverlaySettings()
        }

        dialog.show()
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            Toast.makeText(this, "您的系统版本无需手动开启后台弹出权限", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== 通知权限首次开启后的引导 ==========
    private fun showNotificationPermissionGrantedDialog() {
        AlertDialog.Builder(this)
            .setTitle("通知权限已开启")
            .setMessage("通知权限已开启，为了确保本工具能正常通知您，请继续开启「允许后台弹出」权限。")
            .setPositiveButton("去开启后台弹出") { _, _ ->
                openOverlaySettings()
            }
            .setNegativeButton("稍后", null)
            .setCancelable(false)
            .show()
    }

    // ========== 首次强制教程 ==========
    private fun showFirstTutorialDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tutorial, null)
        val tvContent = dialogView.findViewById<TextView>(R.id.tv_tutorial_content)
        val btnBack = dialogView.findViewById<Button>(R.id.btn_tutorial_back)
        val btnTabNotification = dialogView.findViewById<Button>(R.id.btn_tab_notification)
        val btnTabOverlay = dialogView.findViewById<Button>(R.id.btn_tab_overlay)
        val btnTabSwitch = dialogView.findViewById<Button>(R.id.btn_tab_switch)
        // 首次强制只显示通知教程，隐藏标签
        btnTabNotification.visibility = View.GONE
        btnTabOverlay.visibility = View.GONE
        btnTabSwitch.visibility = View.GONE
        btnBack.isEnabled = false
        btnBack.text = "请等待..."

        tvContent.text = "📖 首次使用必读\n\n为了在输入法被切换时能及时通知您，请务必开启通知权限。\n\n操作步骤：\n1. 点击下方「我已阅读并理解」\n2. 在弹窗中选择「打开通知」\n3. 在应用信息界面点击「通知」\n4. 打开「允许通知」开关\n5. 勾选所有提醒方式（锁屏、横幅、悬浮等）\n\n请仔细阅读，确认后点击下方按钮。"

        val builder = AlertDialog.Builder(this).setView(dialogView).setCancelable(false)
        val dialog = builder.create()
        val waitTime = (1500 + (Math.random() * 2550)).toLong()
        mainHandler.postDelayed({
            btnBack.isEnabled = true
            btnBack.text = "我已阅读并理解"
        }, waitTime)

        btnBack.setOnClickListener {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_TUTORIAL_READ, true).apply()
            dialog.dismiss()
            // 显示过渡窗口（原来的“准备开启通知权限”）
            showTransitionDialog()
        }
        dialog.show()
    }

    // ========== 过渡窗口（准备开启通知权限） ==========
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

    // ========== 仅保存选择的输入法 ==========
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

    // ========== 启用输入法状态页面（改回最初版逻辑，保留现在样式） ==========
    private fun showInputMethodEnableStatusDialog() {
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

        // 获取启用状态（可能失败，Android 13+ 限制）
        var enabledList = emptyList<String>()
        var statusHint = ""
        try {
            val enabledStr = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS
            ) ?: ""
            if (enabledStr.isNotEmpty()) {
                enabledList = enabledStr.split(":").filter { it.isNotEmpty() }
                statusHint = "（已启用/未启用状态）"
            } else {
                statusHint = "（无法获取启用状态，请查看系统设置）"
            }
        } catch (e: SecurityException) {
            statusHint = "（无法获取启用状态，请查看系统设置）"
        } catch (e: Exception) {
            statusHint = "（无法获取启用状态，请查看系统设置）"
        }

        // 构建显示文本
        val sb = StringBuilder("输入法列表$statusHint：\n\n")
        for (info in imeList) {
            val label = info.loadLabel(packageManager).toString()
            val id = info.id
            val isEnabled = enabledList.contains(id)
            sb.append("• $label\n")
            sb.append("  包名：$id\n")
            sb.append("  状态：${if (enabledList.isNotEmpty()) (if (isEnabled) "已启用" else "未启用") else "未知"}\n\n")
        }

        // 创建对话框，底部放一个“启用输入法”按钮
        val builder = AlertDialog.Builder(this)
            .setTitle("输入法状态")
            .setMessage(sb.toString().trimEnd())
            .setPositiveButton("启用输入法") { _, _ ->
                try {
                    imm.showInputMethodPicker()
                    Toast.makeText(this, "请在弹出窗口中选择要启用并切换的输入法", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    AlertDialog.Builder(this)
                        .setTitle("提示")
                        .setMessage("无法弹出输入法选择器，请前往系统设置手动启用。")
                        .setPositiveButton("去设置") { _, _ ->
                            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                            startActivity(intent)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            .setNegativeButton("关闭", null)

        builder.show()
    }

    // ========== 显示所有已安装输入法（不再使用预定义列表） ==========
    private fun showAllInputMethodsDialog() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imeList = imm.inputMethodList
        if (imeList.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("可识别守护的输入法")
                .setMessage("未检测到任何输入法。")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val enabledList = try {
            imm.enabledInputMethodList.map { it.id }.toList()
        } catch (e: Exception) {
            emptyList()
        }

        val sb = StringBuilder()
        for (info in imeList) {
            val label = info.loadLabel(packageManager).toString()
            val id = info.id
            val isEnabled = enabledList.contains(id)
            sb.append("• $label\n")
            sb.append("  包名：$id\n")
            sb.append("  状态：${if (isEnabled) "已启用" else "未启用"}\n\n")
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
        val btnTabOverlay = dialogView.findViewById<Button>(R.id.btn_tab_overlay)
        val btnTabSwitch = dialogView.findViewById<Button>(R.id.btn_tab_switch)

        btnBack.isEnabled = true
        btnBack.text = "返回"

        val notificationTutorial = """
            1. 点击「打开权限」→「打开通知」进入应用信息界面。
            2. 点击「通知」选项。
            3. 打开「允许通知」开关。
            4. 展开「提醒方式」，勾选所有选项（锁屏、横幅、悬浮等）。
            5. 返回 App。
        """.trimIndent()

        val overlayTutorial = """
            1. 点击「打开权限」→「打开后台弹出」。
            2. 在权限页面，开启「允许显示在其他应用上层」。
            3. 返回 App。
        """.trimIndent()

        val switchTutorial = """
            1. 点击主页「切换输入法」按钮，系统弹出输入法选择器。
            2. 选择您想要使用的输入法。
            3. 系统将自动切换。
        """.trimIndent()

        tvContent.text = notificationTutorial
        btnTabNotification.setOnClickListener {
            tvContent.text = notificationTutorial
            btnBack.isEnabled = true
            btnBack.text = "返回"
        }
        btnTabOverlay.setOnClickListener {
            tvContent.text = overlayTutorial
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

        btnBack.setOnClickListener { dialog.dismiss() }
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
        tvNotificationStatus.text = if (isNotificationPermissionGranted()) {
            "通知权限已开启"
        } else {
            "通知权限未开启"
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

    private fun updateCurrentImeDisplay() {
        val currentId = getCurrentImeId()
        val label = getImeLabel(currentId)
        tvCurrentIme.text = if (currentId == "未知") {
            "当前输入法：未检测"
        } else {
            "当前输入法：$label\n包名：$currentId"
        }
    }

    private fun updateSelectedImeDisplay() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedId = prefs.getString(KEY_SELECTED_IME, null)
        tvSelectedIme.text = if (selectedId != null) {
            val label = getImeLabel(selectedId)
            "已选择的输入法：$label\n包名：$selectedId"
        } else {
            "已选择的输入法：无"
        }
    }
}
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
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    companion object {
        private const val PREFS_NAME = "IMEGuardianPrefs"
        private const val KEY_SELECTED_IME = "selected_ime_id"
        private const val KEY_TUTORIAL_READ = "tutorial_read"
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

    // 动态注册广播接收器
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

        updateCurrentImeDisplay()
        updateSelectedImeDisplay()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

        btnNotificationPermission.setOnClickListener {
            if (isNotificationPermissionGranted()) {
                tvNotificationStatus.text = "✅ 通知权限已开启"
            } else {
                jumpToAppSettings()
            }
        }

        btnViewTutorial.setOnClickListener { showDetailTutorialDialog() }

        // 新增按钮点击事件：显示所有可识别的输入法
        btnRecognizableIme.setOnClickListener { showRecognizableInputMethodsDialog() }

        updateNotificationStatus()

        // 动态注册广播
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
        val waitTime = (1000 + (Math.random() * 2500)).toLong()
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
            "${imeList[i].loadLabel(packageManager)} (${imeList[i].id})"
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
                    .setMessage("已选择输入法：${getImeLabel(selectedId)}\n\n当检测到输入法被切换时，App会通知您手动切换回来。")
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

    // ========== 显示所有可识别的输入法（新按钮） ==========
    private fun showRecognizableInputMethodsDialog() {
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

        val sb = StringBuilder()
        for (info in imeList) {
            val label = info.loadLabel(packageManager).toString()
            val id = info.id
            sb.append("• $label\n")
            sb.append("  ($id)\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("可识别守护的输入法")
            .setMessage(sb.toString().trimEnd())
            .setPositiveButton("确定", null)
            .show()
    }

    // ========== 启用输入法状态对话框（修改启用按钮为调用系统选择器） ==========
    private fun showInputMethodEnableStatusDialog() {
        try {
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

            var enabledList: List<String> = emptyList()
            var statusHint = ""
            try {
                val enabledIdsStr = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_INPUT_METHODS
                ) ?: ""
                enabledList = if (enabledIdsStr.isNotEmpty()) {
                    enabledIdsStr.split(":").filter { it.isNotEmpty() }
                } else emptyList()
                statusHint = "（已启用/未启用状态）"
            } catch (e: SecurityException) {
                try {
                    val enabledMethods = imm.enabledInputMethodList
                    enabledList = enabledMethods.map { it.id }
                    statusHint = "（尝试获取启用状态）"
                } catch (e2: Exception) {
                    statusHint = "（无法获取启用状态，请查看系统设置）"
                }
            } catch (e: Exception) {
                statusHint = "（无法获取启用状态，请查看系统设置）"
            }

            val sb = StringBuilder("输入法列表$statusHint：\n\n")
            for (info in imeList) {
                val label = info.loadLabel(packageManager).toString()
                val isEnabled = enabledList.contains(info.id)
                sb.append("• $label")
                if (enabledList.isNotEmpty()) {
                    sb.append(if (isEnabled) " ✅ 已启用" else " ❌ 未启用")
                }
                sb.append("\n")
            }
            sb.append("\n点击「启用输入法」将弹出系统选择器，选择后即可启用并切换。")

            val builder = AlertDialog.Builder(this)
                .setTitle("输入法状态")
                .setMessage(sb.toString())
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
                .setNeutralButton("刷新") { _, _ ->
                    showInputMethodEnableStatusDialog()
                }
                .setNegativeButton("关闭", null)

            builder.show()
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("错误")
                .setMessage("读取输入法状态失败，请稍后重试。\n\n${e.message}")
                .setPositiveButton("确定", null)
                .show()
        }
    }

    // ========== 详细教程对话框（支持侧滑返回） ==========
    private fun showDetailTutorialDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tutorial, null)
        val tvContent = dialogView.findViewById<TextView>(R.id.tv_tutorial_content)
        val btnBack = dialogView.findViewById<Button>(R.id.btn_tutorial_back)
        val btnTabNotification = dialogView.findViewById<Button>(R.id.btn_tab_notification)
        val btnTabSwitch = dialogView.findViewById<Button>(R.id.btn_tab_switch)

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
        btnTabNotification.setOnClickListener { tvContent.text = notificationTutorial }
        btnTabSwitch.setOnClickListener { tvContent.text = switchTutorial }

        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(true)

        btnBack.isEnabled = false
        btnBack.text = "请等待..."
        val waitTime = (1000 + (Math.random() * 2500)).toLong()
        mainHandler.postDelayed({
            btnBack.isEnabled = true
            btnBack.text = "返回"
        }, waitTime)

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

    private fun updateCurrentImeDisplay() {
        val currentId = getCurrentImeId()
        tvCurrentIme.text = "当前输入法：${getImeLabel(currentId)} ($currentId)"
    }

    private fun updateSelectedImeDisplay() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedId = prefs.getString(KEY_SELECTED_IME, null)
        tvSelectedIme.text = if (selectedId != null) {
            "已选择的输入法：${getImeLabel(selectedId)} ($selectedId)"
        } else "已选择的输入法：无"
    }
}
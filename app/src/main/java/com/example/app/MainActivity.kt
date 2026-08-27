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
import android.view.Gravity
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
        private const val REQUEST_CODE_OVERLAY = 1001
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
        btnEnableIme.setOnClickListener { showEnableImeDialog() }
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
        val tutorialRead = prefs.getBoolean(KEY_TUTORIAL_READ, false)

        // 控制“可能无法弹出通知”的显示
        if (tutorialRead) {
            if (isNotificationPermissionGranted()) {
                // 检查是否已经显示过任何引导
                val overlayGuideShown = prefs.getBoolean("overlay_guide_shown", false)
                if (!overlayGuideShown) {
                    // 如果权限未开启，弹出“可能无法弹出通知”
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                        showOverlayPermissionPrompt()
                        prefs.edit().putBoolean("overlay_guide_shown", true).apply()
                    }
                }
            }
        }

        // 通知权限状态变化引导（首次开启通知）
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
        val btnQuery = dialogView.findViewById<Button>(R.id.btn_permission_query_packages)

        val dialog = AlertDialog.Builder(this)
            .setTitle("打开或关闭权限")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnReturn.setOnClickListener { dialog.dismiss() }
        btnNotification.setOnClickListener {
            dialog.dismiss()
            jumpToNotificationSettings()
        }
        btnOverlay.setOnClickListener {
            dialog.dismiss()
            requestOverlayPermission()  // 调用权限授权对话框
        }
        btnQuery.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "已声明获取输入法列表权限", Toast.LENGTH_LONG).show()
        }

        dialog.show()
    }

    // ========== 请求“允许后台弹出”权限（权限授权对话框） ==========
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "权限已开启", Toast.LENGTH_SHORT).show()
                return
            }
            // 请求悬浮窗权限，系统会弹出权限授权对话框
            requestPermissions(
                arrayOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW),
                REQUEST_CODE_OVERLAY
            )
        } else {
            Toast.makeText(this, "您的系统版本无需此权限", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== 处理权限请求结果 ==========
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_OVERLAY) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ 已开启「允许后台弹出」权限", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ========== 跳转通知设置 ==========
    private fun jumpToNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(intent)
    }

    // ========== 后台弹出权限引导 ==========
    private fun showOverlayPermissionPrompt() {
        AlertDialog.Builder(this)
            .setTitle("可能无法弹出通知")
            .setMessage("为了确保本工具能在后台及时提醒您，需要开启「允许后台弹出」权限。\n\n是否前往开启？")
            .setPositiveButton("前往操作") { _, _ ->
                requestOverlayPermission()
            }
            .setNegativeButton("忽略", null)
            .setCancelable(false)
            .show()
    }

    // ========== 首次开启通知后的引导 ==========
    private fun showNotificationPermissionGrantedDialog() {
        // 标记已显示引导，避免重复弹窗
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("overlay_guide_shown", true).apply()

        AlertDialog.Builder(this)
            .setTitle("通知权限已开启")
            .setMessage("通知权限已开启，为了确保本工具能正常通知您，请继续开启「允许后台弹出」权限。")
            .setPositiveButton("去开启") { _, _ ->
                requestOverlayPermission()
            }
            .setNegativeButton("稍后", null)
            .setCancelable(false)
            .show()
    }

    // ========== 首次教程 ==========
    private fun showFirstTutorialDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tutorial, null)
        val tvContent = dialogView.findViewById<TextView>(R.id.tv_tutorial_content)
        val btnBack = dialogView.findViewById<Button>(R.id.btn_tutorial_back)
        val btnTabNotification = dialogView.findViewById<Button>(R.id.btn_tab_notification)
        val btnTabOverlay = dialogView.findViewById<Button>(R.id.btn_tab_overlay)
        val btnTabSwitch = dialogView.findViewById<Button>(R.id.btn_tab_switch)
        btnTabNotification.visibility = View.GONE
        btnTabOverlay.visibility = View.GONE
        btnTabSwitch.visibility = View.GONE
        btnBack.isEnabled = false
        btnBack.text = "请等待..."

        tvContent.text = "📖 首次使用必读\n\n为了在输入法被切换时能及时通知您，请务必开启通知权限。\n\n操作步骤：\n1. 点击下方「我已阅读并理解」\n2. 在弹窗中选择「打开或关闭通知」\n3. 在通知设置中开启「允许通知」\n4. 勾选所有提醒方式（锁屏、横幅、悬浮等）\n\n请仔细阅读，确认后点击下方按钮。"

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
                jumpToNotificationSettings()
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

    // ========== 保存选择的输入法 ==========
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

    // ========== 启用输入法界面（表格样式） ==========
    private fun showEnableImeDialog() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imeList = imm.inputMethodList
        if (imeList.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("启用输入法")
                .setMessage("未检测到任何输入法。")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val enabledList = try {
            imm.enabledInputMethodList.map { it.id }.toSet()
        } catch (e: Exception) {
            emptySet()
        }

        val scrollView = ScrollView(this)
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        for (info in imeList) {
            val label = info.loadLabel(packageManager).toString()
            val id = info.id
            val currentlyEnabled = enabledList.contains(id)

            val statusText = if (currentlyEnabled) "启用成功 ✓" else "启用失败 ✗"
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
                gravity = Gravity.CENTER_VERTICAL
            }

            val textView = TextView(this).apply {
                text = "$label ($id) ———— $statusText"
                textSize = 16f
                gravity = Gravity.START
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val actionButton = Button(this).apply {
                text = if (currentlyEnabled) "已启用" else "启用"
                isEnabled = !currentlyEnabled
                setOnClickListener {
                    if (!currentlyEnabled) {
                        try {
                            imm.showInputMethodPicker()
                            Toast.makeText(this@MainActivity, "请在弹出窗口中选择要启用并切换的输入法", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("提示")
                                .setMessage("无法弹出输入法选择器，请前往系统设置手动启用。")
                                .setPositiveButton("我知道了", null)
                                .show()
                        }
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(24, 12, 24, 12)
                if (currentlyEnabled) {
                    backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, android.R.color.holo_green_light)
                } else {
                    backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, android.R.color.holo_blue_dark)
                }
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            }

            itemLayout.addView(textView)
            itemLayout.addView(actionButton)
            linearLayout.addView(itemLayout)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(0xFFE0E0E0.toInt())
            }
            linearLayout.addView(divider)
        }

        val refreshButton = Button(this).apply {
            text = "刷新"
            setOnClickListener {
                showEnableImeDialog()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 16, 32, 16)
            backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, android.R.color.holo_green_light)
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
        }
        val refreshLayout = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(refreshButton)
        }
        linearLayout.addView(refreshLayout)

        scrollView.addView(linearLayout)

        AlertDialog.Builder(this)
            .setTitle("启用输入法")
            .setView(scrollView)
            .setPositiveButton("关闭") { _, _ -> }
            .show()
    }

    // ========== 显示所有已安装输入法（完整列表） ==========
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
            imm.enabledInputMethodList.map { it.id }.toSet()
        } catch (e: Exception) {
            emptySet()
        }

        val sb = StringBuilder()
        for (info in imeList) {
            val label = info.loadLabel(packageManager).toString()
            val id = info.id
            val isEnabled = enabledList.contains(id)
            sb.append("• $label\n")
            sb.append("  包名：$id\n")
            sb.append("  状态：已安装，${if (isEnabled) "已启用" else "未启用"}\n\n")
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
            1. 点击「打开或关闭权限」→「打开或关闭通知」进入通知设置。
            2. 打开「允许通知」开关。
            3. 展开「提醒方式」，勾选所有选项。
            4. 返回 App。
        """.trimIndent()

        val overlayTutorial = """
            1. 点击「打开或关闭权限」→「打开或关闭后台弹出」。
            2. 在系统弹出的权限对话框中，点击「允许」。
            3. 返回 App。
        """.trimIndent()

        val switchTutorial = """
            1. 点击主页「切换输入法」按钮，弹出输入法选择器。
            2. 选择您想用的输入法。
            3. 系统自动切换。
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
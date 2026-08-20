package com.example.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class InputMethodChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "ime_guardian_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.INPUT_METHOD_CHANGED") return

        val currentIme = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return

        val prefs = context.getSharedPreferences("IMEGuardianPrefs", Context.MODE_PRIVATE)
        val selectedIme = prefs.getString("selected_ime_id", null)

        var enabledList = emptyList<String>()
        try {
            val enabledStr = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS
            ) ?: ""
            enabledList = if (enabledStr.isNotEmpty()) {
                enabledStr.split(":").filter { it.isNotEmpty() }
            } else emptyList()
        } catch (e: SecurityException) {
            // Android 13+ 无法读取，忽略
        }

        var shouldNotify = false
        var reason = ""

        if (selectedIme != null && selectedIme != currentIme) {
            shouldNotify = true
            reason = "🔄 默认输入法已被切换"
        } else if (selectedIme != null && enabledList.isNotEmpty() && !enabledList.contains(selectedIme)) {
            shouldNotify = true
            reason = "🚫 您选择的输入法已被禁用"
        }

        if (shouldNotify) {
            sendNotification(context, reason)
        }
    }

    private fun sendNotification(context: Context, reason: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "输入法守护",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⚠️ 输入法状态异常")
            .setContentText("$reason，点击此处返回 App 处理。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
package com.example.pengo.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pengo.MainActivity
import com.example.pengo.R

object Notifier {
    private const val CHANNEL_ORDERS = "orders"
    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ORDERS, "Orders & exit passes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Payment confirmations and exit pass updates"
            }
        )
    }

    fun orderUpdate(title: String, text: String) {
        if (!::app.isInitialized || !Prefs.settings.value.orderNotifications) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ORDERS)
            .setSmallIcon(R.drawable.ic_stat_pengo)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(System.currentTimeMillis().toInt(), notification) }
    }
}

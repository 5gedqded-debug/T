package com.example.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager

/** Показывает виджет батареи поверх всех окон. Виджет можно перетаскивать. */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var view: BatteryView
    private lateinit var params: WindowManager.LayoutParams

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = update(intent)
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        view = BatteryView(this)

        val d = resources.displayMetrics.density
        params = WindowManager.LayoutParams(
            (72 * d).toInt(),
            (36 * d).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * d).toInt()
            y = (48 * d).toInt()
        }

        // Перетаскивание пальцем
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    // gravity END: x отсчитывается от правого края
                    params.x = startX - (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(view, params)
                }
            }
            true
        }

        wm.addView(view, params)

        val sticky = registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { update(it) }
    }

    private fun update(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val charging = plugged ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        view.setState(if (level >= 0) level * 100 / scale else 0, charging)
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("widget", "Widget", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, "widget")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Widget работает")
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        wm.removeView(view)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

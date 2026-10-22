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
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager

/**
 * Показывает виджет батареи поверх всех окон. Виджет можно перетаскивать.
 * Позиция хранится в долях экрана (0..1), поэтому при повороте экрана
 * виджет остаётся на том же относительном месте и не уходит за край.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var view: BatteryView
    private lateinit var params: WindowManager.LayoutParams

    private var fx = 1f   // 0 = левый край, 1 = правый край
    private var fy = 0.06f // 0 = верх, 1 = низ

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = update(intent)
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        view = BatteryView(this)

        val prefs = getSharedPreferences("widget", MODE_PRIVATE)
        fx = prefs.getFloat("fx", fx)
        fy = prefs.getFloat("fy", fy)

        val d = resources.displayMetrics.density
        params = WindowManager.LayoutParams(
            (72 * d).toInt(),
            (36 * d).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        applyPosition()

        // Перетаскивание пальцем
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val (sw, sh) = screenSize()
                    val maxX = (sw - params.width).coerceAtLeast(1)
                    val maxY = (sh - params.height).coerceAtLeast(1)
                    params.x = (startX + (e.rawX - touchX).toInt()).coerceIn(0, maxX)
                    params.y = (startY + (e.rawY - touchY).toInt()).coerceIn(0, maxY)
                    fx = params.x.toFloat() / maxX
                    fy = params.y.toFloat() / maxY
                    wm.updateViewLayout(view, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.edit().putFloat("fx", fx).putFloat("fy", fy).apply()
                }
            }
            true
        }

        wm.addView(view, params)

        val sticky = registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { update(it) }
    }

    /** Размер экрана в пикселях в текущей ориентации. */
    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val p = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(p)
            p.x to p.y
        }
    }

    /** Переводит доли экрана в пиксели для текущей ориентации. */
    private fun applyPosition() {
        val (sw, sh) = screenSize()
        val maxX = (sw - params.width).coerceAtLeast(0)
        val maxY = (sh - params.height).coerceAtLeast(0)
        params.x = (fx * maxX).toInt().coerceIn(0, maxX)
        params.y = (fy * maxY).toInt().coerceIn(0, maxY)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Поворот экрана или смена темы: пересчитать позицию и перерисовать
        view.refreshTheme()
        applyPosition()
        wm.updateViewLayout(view, params)
        // Некоторые устройства отдают новые размеры экрана с задержкой
        view.post {
            applyPosition()
            if (view.isAttachedToWindow) wm.updateViewLayout(view, params)
        }
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

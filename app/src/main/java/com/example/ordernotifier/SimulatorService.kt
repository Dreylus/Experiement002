package com.example.ordernotifier

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.random.Random

/** Foreground service that posts orders at random intervals, with optional random bursts. */
class SimulatorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: Settings

    private val tick = object : Runnable {
        override fun run() {
            var delayMs = nextGapMs()
            OrderNotifications.postOrder(this@SimulatorService, settings)

            if (Random.nextInt(100) < settings.burstChance && settings.burstMax > 1) {
                val extra = Random.nextInt(1, settings.burstMax) // 1..burstMax-1 more
                var t = 0L
                repeat(extra) {
                    t += Random.nextLong(1000, 5000)
                    handler.postDelayed(
                        { OrderNotifications.postOrder(this@SimulatorService, settings) }, t
                    )
                }
                delayMs += t
            }
            handler.postDelayed(this, delayMs)
        }
    }

    private fun nextGapMs(): Long {
        val lo = minOf(settings.minGapSec, settings.maxGapSec).coerceAtLeast(1)
        val hi = maxOf(settings.minGapSec, settings.maxGapSec).coerceAtLeast(1)
        return Random.nextLong(lo * 1000L, hi * 1000L + 1)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        settings = Settings(this)
        OrderNotifications.createChannels(this)
        val note = NotificationCompat.Builder(this, OrderNotifications.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Order simulator running")
            .setContentText("Open the app to stop it")
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, note, type)

        handler.removeCallbacks(tick)
        handler.postDelayed(tick, nextGapMs())
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) =
            ctx.startForegroundService(Intent(ctx, SimulatorService::class.java))

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, SimulatorService::class.java))
        }
    }
}

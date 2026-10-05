package com.example.ordernotifier

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.random.Random

object OrderNotifications {
    const val CHANNEL_ORDERS = "orders"
    const val CHANNEL_SERVICE = "service"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ORDERS, "Orders", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Running indicator", NotificationManager.IMPORTANCE_MIN)
        )
    }

    fun canPost(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Posts one fake order using the current settings. */
    fun postOrder(ctx: Context, s: Settings) {
        if (!canPost(ctx)) return
        val number = s.nextOrder
        s.nextOrder = number + 1

        val hi = maxOf(s.minPrice, s.maxPrice)
        val lo = minOf(s.minPrice, s.maxPrice)
        val price = lo + Random.nextDouble() * (hi - lo)
        val items = Random.nextInt(1, maxOf(1, s.maxItems) + 1)
        val itemWord = if (items == 1) "item" else "items"
        val priceText = String.format(Locale.US, "%s%.2f", s.currency, price)

        val n = NotificationCompat.Builder(ctx, CHANNEL_ORDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(IconUtil.load(s))
            .setContentTitle("Order #$number")
            .setContentText("$priceText, $items $itemWord from ${s.storeName}")
            .setSubText(s.storeName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setGroup("orders")
            .build()
        NotificationManagerCompat.from(ctx).notify(number, n)
    }
}

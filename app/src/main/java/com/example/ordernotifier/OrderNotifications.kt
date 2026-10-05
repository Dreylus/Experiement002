package com.example.ordernotifier

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import java.util.Locale
import kotlin.random.Random

object OrderNotifications {
    private const val CHANNEL_ALERT = "orders_alert"
    private const val CHANNEL_QUIET = "orders_quiet"
    const val CHANNEL_SERVICE = "service"

    /** Both order channels are HIGH importance so Android pops them up as a banner. */
    fun orderChannelId(s: Settings) = if (s.sound) CHANNEL_ALERT else CHANNEL_QUIET

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("orders") // old v1 channel

        val alert = NotificationChannel(CHANNEL_ALERT, "Orders (sound + vibration)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Pop-up order notifications with sound"
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        val quiet = NotificationChannel(CHANNEL_QUIET, "Orders (silent banner)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Pop-up order notifications without sound"
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
        }
        val service = NotificationChannel(CHANNEL_SERVICE, "Running indicator", NotificationManager.IMPORTANCE_MIN)
        nm.createNotificationChannels(listOf(alert, quiet, service))
    }

    fun canPost(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun formatText(s: Settings, price: Double, items: Int): String {
        val itemWord = if (items == 1) "item" else "items"
        return String.format(Locale.US, "%s%.2f, %d %s from %s", s.currency, price, items, itemWord, s.storeName)
    }

    /** Posts one fake order using the current settings. */
    fun postOrder(ctx: Context, s: Settings) {
        if (!canPost(ctx)) return
        val number = s.nextOrder
        s.nextOrder = number + 1

        val lo = minOf(s.minPrice, s.maxPrice)
        val hi = maxOf(s.minPrice, s.maxPrice)
        val price = lo + Random.nextDouble() * (hi - lo)
        val items = Random.nextInt(1, maxOf(1, s.maxItems) + 1)
        val text = formatText(s, price, items)
        val title = "Order #$number"
        val icon = IconUtil.load(s)

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(ctx, orderChannelId(s))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(s.storeName)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)

        if (s.singlePicture) {
            // Chat-style: Android shows the sender's picture once, big, on the left.
            val me = Person.Builder().setName("Me").build()
            val sender = Person.Builder()
                .setName(title)
                .setIcon(IconCompat.createWithBitmap(icon))
                .build()
            b.setStyle(
                NotificationCompat.MessagingStyle(me)
                    .addMessage(text, System.currentTimeMillis(), sender)
            )
        } else {
            b.setLargeIcon(icon)
        }

        NotificationManagerCompat.from(ctx).notify("order", number, b.build())
    }
}

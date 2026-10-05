package com.example.ordernotifier

import java.util.Locale

/** Text formatting shared by the screens. */
object Format {
    /** "$1,250.00" */
    fun money(currency: String, cents: Long): String =
        String.format(Locale.US, "%s%,.2f", currency, cents / 100.0)

    /** Compact money for stats: "$843", "$12.3K", "$1.2M". */
    fun moneyShort(currency: String, cents: Long): String {
        val v = cents / 100.0
        return when {
            v >= 1_000_000 -> String.format(Locale.US, "%s%.1fM", currency, v / 1_000_000)
            v >= 10_000 -> String.format(Locale.US, "%s%.1fK", currency, v / 1_000)
            else -> String.format(Locale.US, "%s%,.0f", currency, v)
        }
    }

    /** "1,204" or "12.3K" */
    fun count(n: Int): String =
        if (n >= 10_000) String.format(Locale.US, "%.1fK", n / 1000.0) else String.format(Locale.US, "%,d", n)

    /** "45s", "2m", "2m 30s", "1h", "1h 30m" */
    fun seconds(sec: Int): String = when {
        sec < 60 -> "${sec}s"
        sec < 3600 -> if (sec % 60 == 0) "${sec / 60}m" else "${sec / 60}m ${sec % 60}s"
        else -> if (sec % 3600 == 0) "${sec / 3600}h" else "${sec / 3600}h ${(sec % 3600) / 60}m"
    }

    /** Words for a time window: "10 min", "hour", "8 hours", "day". */
    fun window(sec: Int): String = when {
        sec < 3600 -> "${sec / 60} min"
        sec == 3600 -> "hour"
        sec < 86_400 -> "${sec / 3600} hours"
        else -> "day"
    }

    /** Social-feed style age: "now", "5m", "3h", "2d". */
    fun ago(time: Long, now: Long): String {
        val s = ((now - time) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "now"
            s < 3600 -> "${s / 60}m"
            s < 86_400 -> "${s / 3600}h"
            else -> "${s / 86_400}d"
        }
    }

    fun items(n: Int) = if (n == 1) "1 item" else "$n items"
}

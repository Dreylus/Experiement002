package com.example.ordernotifier

import android.content.Context
import java.io.File

/** All user-tweakable values live here. Change DEFAULT_STORE_NAME to rename the blank slate. */
class Settings(private val ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", false)
        set(v) = p.edit().putBoolean("enabled", v).apply()

    /** Name shown in the notification (customizable in the app). */
    var storeName: String
        get() = p.getString("storeName", DEFAULT_STORE_NAME) ?: DEFAULT_STORE_NAME
        set(v) = p.edit().putString("storeName", v.ifBlank { DEFAULT_STORE_NAME }).apply()

    var currency: String
        get() = p.getString("currency", "$") ?: "$"
        set(v) = p.edit().putString("currency", v).apply()

    // Gap between notifications, in seconds (picked uniformly at random in [min, max]).
    var minGapSec: Int
        get() = p.getInt("minGapSec", 20)
        set(v) = p.edit().putInt("minGapSec", v).apply()
    var maxGapSec: Int
        get() = p.getInt("maxGapSec", 120)
        set(v) = p.edit().putInt("maxGapSec", v).apply()

    // Chance (0-100) that a notification is followed by a quick burst, and max burst size.
    var burstChance: Int
        get() = p.getInt("burstChance", 25)
        set(v) = p.edit().putInt("burstChance", v).apply()
    var burstMax: Int
        get() = p.getInt("burstMax", 4)
        set(v) = p.edit().putInt("burstMax", v).apply()

    var minPrice: Double
        get() = p.getFloat("minPrice", 15f).toDouble()
        set(v) = p.edit().putFloat("minPrice", v.toFloat()).apply()
    var maxPrice: Double
        get() = p.getFloat("maxPrice", 120f).toDouble()
        set(v) = p.edit().putFloat("maxPrice", v.toFloat()).apply()
    var maxItems: Int
        get() = p.getInt("maxItems", 3)
        set(v) = p.edit().putInt("maxItems", v).apply()

    var nextOrder: Int
        get() = p.getInt("nextOrder", 1001)
        set(v) = p.edit().putInt("nextOrder", v).apply()

    val iconFile: File get() = File(ctx.filesDir, "store_icon.png")

    companion object {
        const val DEFAULT_STORE_NAME = "Store"
    }
}

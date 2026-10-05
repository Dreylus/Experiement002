package com.example.ordernotifier

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** All user-tweakable values live here. Change the DEFAULT_* constants to rename the blank slate. */
class Settings(private val ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun addListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        p.registerOnSharedPreferenceChangeListener(l)

    fun removeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        p.unregisterOnSharedPreferenceChangeListener(l)

    var enabled: Boolean
        get() = p.getBoolean(KEY_ENABLED, false)
        set(v) = p.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Name shown in the notification header (customizable in the app). */
    var storeName: String
        get() = p.getString("storeName", DEFAULT_STORE_NAME) ?: DEFAULT_STORE_NAME
        set(v) = p.edit().putString("storeName", v.ifBlank { DEFAULT_STORE_NAME }).apply()

    /** The text after "from" in each order ("…2 items from Online Store"). Blank hides that part. */
    var fromText: String
        get() = p.getString("fromText", DEFAULT_FROM_TEXT) ?: DEFAULT_FROM_TEXT
        set(v) = p.edit().putString("fromText", v).apply()

    /** Goes right before the order number in the title ("Order #1001"). */
    var titlePrefix: String
        get() = p.getString("titlePrefix", DEFAULT_TITLE_PREFIX) ?: DEFAULT_TITLE_PREFIX
        set(v) = p.edit().putString("titlePrefix", v.ifBlank { DEFAULT_TITLE_PREFIX }).apply()

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
        get() = p.getInt(KEY_NEXT_ORDER, 1001)
        set(v) = p.edit().putInt(KEY_NEXT_ORDER, v).apply()

    /** Chat-style notification: the picture shows once, on the left. */
    var singlePicture: Boolean
        get() = p.getBoolean("singlePicture", true)
        set(v) = p.edit().putBoolean("singlePicture", v).apply()

    var sound: Boolean
        get() = p.getBoolean("sound", true)
        set(v) = p.edit().putBoolean("sound", v).apply()

    var keepAwake: Boolean
        get() = p.getBoolean("keepAwake", false)
        set(v) = p.edit().putBoolean("keepAwake", v).apply()

    /** Play the user's own audio file for each order notification. */
    var customSoundOn: Boolean
        get() = p.getBoolean("customSoundOn", false)
        set(v) = p.edit().putBoolean("customSoundOn", v).apply()

    var soundName: String
        get() = p.getString("soundName", "") ?: ""
        set(v) = p.edit().putString("soundName", v).apply()

    val soundFile: File get() = File(ctx.filesDir, "custom_sound")

    val iconFile: File get() = File(ctx.filesDir, "store_icon.png")

    // ---- Order log: the stats and recent orders shown on the Home tab ----

    private val isToday get() = p.getLong("statsDay", -1) == today()

    val todayOrders: Int get() = if (isToday) p.getInt("todayOrders", 0) else 0
    val todayCents: Long get() = if (isToday) p.getLong("todayCents", 0) else 0
    val totalOrders: Int get() = p.getInt("totalOrders", 0)
    val totalCents: Long get() = p.getLong("totalCents", 0)

    fun recentOrders(): List<LoggedOrder> = try {
        val arr = JSONArray(p.getString(KEY_RECENT, "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LoggedOrder(o.getInt("n"), o.getString("t"), o.getLong("c"), o.getInt("i"), o.getLong("at"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun logOrder(order: LoggedOrder) {
        val sameDay = isToday
        val recent = (listOf(order) + recentOrders()).take(MAX_RECENT)
        val arr = JSONArray()
        recent.forEach {
            arr.put(JSONObject().put("n", it.number).put("t", it.title).put("c", it.cents).put("i", it.items).put("at", it.time))
        }
        p.edit()
            .putLong("statsDay", today())
            .putInt("todayOrders", (if (sameDay) p.getInt("todayOrders", 0) else 0) + 1)
            .putLong("todayCents", (if (sameDay) p.getLong("todayCents", 0) else 0) + order.cents)
            .putInt("totalOrders", totalOrders + 1)
            .putLong("totalCents", totalCents + order.cents)
            .putString(KEY_RECENT, arr.toString())
            .apply()
    }

    fun resetStats() {
        p.edit()
            .remove("statsDay").remove("todayOrders").remove("todayCents")
            .remove("totalOrders").remove("totalCents")
            .putString(KEY_RECENT, "[]")
            .apply()
    }

    companion object {
        const val DEFAULT_STORE_NAME = "Store"
        const val DEFAULT_FROM_TEXT = "Online Store"
        const val DEFAULT_TITLE_PREFIX = "Order #"

        const val KEY_ENABLED = "enabled"
        const val KEY_NEXT_ORDER = "nextOrder"
        const val KEY_RECENT = "recentOrders"

        private const val MAX_RECENT = 25

        private fun today() = LocalDate.now().toEpochDay()
    }
}

/** One fake order that was shown, for the Home tab's stats and feed. */
data class LoggedOrder(val number: Int, val title: String, val cents: Long, val items: Int, val time: Long)

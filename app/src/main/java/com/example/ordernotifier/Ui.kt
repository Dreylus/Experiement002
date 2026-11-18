package com.example.ordernotifier

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MR

/** Fills a view_notification_preview card so it matches what Android really shows. */
object Preview {
    fun sampleCents(s: Settings): Long = Math.round((s.minPrice + s.maxPrice) / 2.0 * 100)
    fun sampleItems(s: Settings) = s.maxItems.coerceIn(1, 2)

    /**
     * [chatStyle]: picture on the left and "Store • App • now" on top (conversation style).
     * Otherwise the classic layout: app icon on the left, "App • Store • now", picture on the right.
     */
    fun bind(
        card: View, picture: Bitmap, appName: String, storeName: String,
        title: String, body: String, chatStyle: Boolean,
    ) {
        card.findViewById<ImageView>(R.id.pvIcon).apply {
            setImageBitmap(picture)
            isVisible = chatStyle
        }
        card.findViewById<View>(R.id.pvAppIcon).isVisible = !chatStyle
        card.findViewById<ImageView>(R.id.pvLarge).apply {
            setImageBitmap(picture)
            isVisible = !chatStyle
        }
        card.findViewById<TextView>(R.id.pvHeader).text =
            if (chatStyle) "$storeName \u2022 $appName \u2022 now" else "$appName \u2022 $storeName \u2022 now"
        card.findViewById<TextView>(R.id.pvTitle).text = title
        card.findViewById<TextView>(R.id.pvBody).text = body
    }
}

object UiStyle {
    /** Status and navigation bars that blend with the app instead of the default coloured bar. */
    fun systemBars(a: Activity, root: View) {
        a.window.statusBarColor = MaterialColors.getColor(root, MR.attr.colorSurface)
        a.window.navigationBarColor = MaterialColors.getColor(root, MR.attr.colorSurfaceContainer)
        val night = (a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(a.window, a.window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }
}

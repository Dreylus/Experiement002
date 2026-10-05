package com.example.ordernotifier

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MR

/** Fills a view_notification_preview card. */
object Preview {
    fun sampleCents(s: Settings): Long = Math.round((s.minPrice + s.maxPrice) / 2.0 * 100)
    fun sampleItems(s: Settings) = s.maxItems.coerceIn(1, 2)

    fun bind(card: View, icon: Bitmap, header: String, title: String, body: String) {
        card.findViewById<ImageView>(R.id.pvIcon).setImageBitmap(icon)
        card.findViewById<TextView>(R.id.pvHeader).text = header
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

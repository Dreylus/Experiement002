package com.example.ordernotifier

import android.content.Context
import android.graphics.*
import android.net.Uri

object IconUtil {
    private const val SIZE = 256

    /** Square center-crop of [src] (flat grey if null). The system/preview rounds it as needed. */
    fun square(src: Bitmap?): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (src == null) {
            c.drawColor(Color.rgb(0x9A, 0x9F, 0xA6))
            return out
        }
        val side = minOf(src.width, src.height)
        val crop = Rect((src.width - side) / 2, (src.height - side) / 2,
            (src.width + side) / 2, (src.height + side) / 2)
        c.drawBitmap(src, crop, Rect(0, 0, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun load(settings: Settings): Bitmap {
        val f = settings.iconFile
        val src = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        return square(src)
    }

    /** Copies the picked image into app storage. Returns false if it couldn't be read. */
    fun import(ctx: Context, settings: Settings, uri: Uri): Boolean {
        val resolver = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return false
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return false
        settings.iconFile.outputStream().use { square(src).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return true
    }
}

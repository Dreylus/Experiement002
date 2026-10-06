package com.example.ordernotifier

import android.content.Context
import android.graphics.*
import android.net.Uri

object IconUtil {
    private const val SIZE = 256

    /** Corner radius as a fraction of the size: an app-icon style "squircle". Matches @style/SquircleImage. */
    const val CORNER = 0.28f

    /** Square center-crop of [src] (flat grey if null). */
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

    /** The same picture with transparent rounded corners, so it looks like an app icon everywhere. */
    fun rounded(square: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val r = SIZE * CORNER
        c.drawRoundRect(RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(square, null, Rect(0, 0, SIZE, SIZE), paint)
        return out
    }

    /** The notification picture, as a squircle. */
    fun load(settings: Settings): Bitmap {
        val f = settings.iconFile
        val src = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        return rounded(square(src))
    }

    /** Copies the picked image into app storage. Returns false if it couldn't be read. */
    fun import(ctx: Context, settings: Settings, uri: Uri): Boolean = try {
        val resolver = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) {
            false
        } else {
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (src == null) {
                false
            } else {
                settings.iconFile.outputStream().use { square(src).compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            }
        }
    } catch (e: Exception) {
        false
    }
}

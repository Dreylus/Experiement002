package com.example.ordernotifier

import android.content.Context
import android.graphics.*
import android.net.Uri

object IconUtil {
    private const val SIZE = 256

    /** Squircle-ish rounded square, center-cropped from [src] (or flat grey if null). */
    fun squircle(src: Bitmap?): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = SIZE * 0.28f
        val rect = RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
        if (src == null) {
            paint.color = Color.rgb(0x9A, 0x9F, 0xA6)
            c.drawRoundRect(rect, r, r, paint)
            return out
        }
        c.drawRoundRect(rect, r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val side = minOf(src.width, src.height)
        val crop = Rect((src.width - side) / 2, (src.height - side) / 2,
            (src.width + side) / 2, (src.height + side) / 2)
        c.drawBitmap(src, crop, rect, paint)
        return out
    }

    fun load(settings: Settings): Bitmap {
        val f = settings.iconFile
        val src = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        return squircle(src)
    }

    fun import(ctx: Context, settings: Settings, uri: Uri) {
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
        settings.iconFile.outputStream().use { squircle(src).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

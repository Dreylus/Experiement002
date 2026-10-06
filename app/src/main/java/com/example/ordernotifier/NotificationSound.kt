package com.example.ordernotifier

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File

/** Plays the user's own audio file for this app's order notifications (and nothing else). */
object NotificationSound {
    private const val MAX_BYTES = 8L * 1024 * 1024
    private const val MAX_PLAY_MS = 10_000L

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private val cutoff = Runnable { stop() }

    fun hasFile(s: Settings) = s.soundFile.exists() && s.soundFile.length() > 0

    /** Custom sound is switched on and a file has been chosen. */
    fun isActive(s: Settings) = s.customSoundOn && hasFile(s)

    /** Any Do Not Disturb mode (priority only, alarms only, total silence, schedules...). */
    fun dndActive(ctx: Context): Boolean {
        val f = ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL &&
            f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /**
     * Plays the sound. Returns false if nothing played (no file, switched off, or Do Not Disturb on).
     * [ignoreToggle] is for the in-app preview button.
     */
    fun play(ctx: Context, s: Settings, ignoreToggle: Boolean = false): Boolean {
        if (!hasFile(s) || (!ignoreToggle && !s.customSoundOn)) return false
        if (dndActive(ctx)) return false

        stop()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(s.soundFile.path)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { release(it) }
            mp.setOnErrorListener { p, _, _ -> release(p); true }
            mp.prepareAsync()
        } catch (e: Exception) {
            mp.release()
            return false
        }
        player = mp
        main.postDelayed(cutoff, MAX_PLAY_MS)
        return true
    }

    fun stop() {
        main.removeCallbacks(cutoff)
        val p = player ?: return
        player = null
        try { p.stop() } catch (_: IllegalStateException) {}
        p.release()
    }

    private fun release(p: MediaPlayer) {
        if (player === p) {
            player = null
            main.removeCallbacks(cutoff)
        }
        p.release()
    }

    /** Copies the picked audio into app storage. Returns an error message, or null on success. */
    fun import(ctx: Context, s: Settings, uri: Uri): String? {
        val tmp = File(ctx.cacheDir, "sound_import")
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return "Couldn't read that file"
            input.use { src ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return "That file is too big (max 8 MB)"
                        out.write(buf, 0, n)
                    }
                }
            }

            val check = MediaPlayer()
            try {
                check.setDataSource(tmp.path)
                check.prepare()
                if (check.duration <= 0) return "Android can't play that file. Try MP3, OGG or WAV."
            } catch (e: Exception) {
                return "Android can't play that file. Try MP3, OGG or WAV."
            } finally {
                check.release()
            }

            stop()
            tmp.copyTo(s.soundFile, overwrite = true)
            s.soundName = displayName(ctx, uri) ?: "Custom sound"
            return null
        } catch (e: Exception) {
            return "Couldn't read that file"
        } finally {
            tmp.delete()
        }
    }

    fun remove(s: Settings) {
        stop()
        s.soundFile.delete()
        s.soundName = ""
        s.customSoundOn = false
    }

    private fun displayName(ctx: Context, uri: Uri): String? =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
}

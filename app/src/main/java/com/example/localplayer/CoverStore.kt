package com.example.localplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Дисковый кэш обложек. Сделан экономным по месту:
 *  - хранятся только уменьшенные копии в WebP (192 px для списков, 640 px для плеера и экрана альбома);
 *  - файл называется по хэшу картинки, поэтому у всех треков одного альбома одна общая копия;
 *  - треки без обложки запоминаются в индексе, файл повторно не открывается;
 *  - удалённые из библиотеки треки и их картинки вычищаются после сканирования;
 *  - лежит в cacheDir: систему можно очистить, и Android сам освободит место при нехватке.
 */
object CoverStore {
    private const val SMALL_PX = 192
    private const val LARGE_PX = 640
    private const val QUALITY = 80
    private const val NONE = "-"

    private var dir: File? = null
    private val index = HashMap<String, String>() // ключ трека -> хэш картинки или NONE
    private val lock = Any()
    private var dirty = 0

    /** В каком из двух размеров хранить обложку, которой нужно [targetPx] пикселей. */
    fun tierPx(targetPx: Int): Int = if (targetPx <= 200) SMALL_PX else LARGE_PX

    private fun ensureDir(ctx: Context): File = synchronized(lock) {
        dir ?: File(ctx.applicationContext.cacheDir, "covers").also { d ->
            d.mkdirs()
            runCatching {
                File(d, "index.txt").forEachLine { l ->
                    val i = l.indexOf(' ')
                    if (i > 0) index[l.substring(0, i)] = l.substring(i + 1)
                }
            }
            dir = d
        }
    }

    /** Блокирующий вызов: только из фонового потока. */
    fun load(ctx: Context, uri: String, targetPx: Int): ImageBitmap? {
        val d = ensureDir(ctx)
        val tier = tierPx(targetPx)
        val uk = sha(uri.toByteArray())
        val known = synchronized(lock) { index[uk] }
        if (known == NONE) return null
        if (known != null) {
            val f = File(d, "${known}_$tier.webp")
            if (f.exists()) {
                val bmp = runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
                if (bmp != null) {
                    f.setLastModified(System.currentTimeMillis())
                    return bmp.asImageBitmap()
                }
                f.delete()
            }
        }
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, Uri.parse(uri))
            val data = r.embeddedPicture
            if (data == null) {
                remember(uk, NONE)
                return null
            }
            val hash = sha(data)
            remember(uk, hash)
            val bmp = decodeScaled(data, tier) ?: return null
            val f = File(d, "${hash}_$tier.webp")
            if (!f.exists()) write(d, f, bmp)
            return bmp.asImageBitmap()
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun decodeScaled(data: ByteArray, tier: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxSide <= 0) return null
        var sample = 1
        while (maxSide / (sample * 2) >= tier) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val cur = maxOf(bmp.width, bmp.height)
        if (cur <= tier) return bmp
        val s = tier.toFloat() / cur
        val scaled = Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * s).roundToInt().coerceAtLeast(1),
            (bmp.height * s).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    @Suppress("DEPRECATION")
    private fun write(d: File, f: File, bmp: Bitmap) {
        try {
            val tmp = File(d, f.name + ".tmp")
            val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            FileOutputStream(tmp).use { bmp.compress(fmt, QUALITY, it) }
            if (!tmp.renameTo(f)) tmp.delete()
        } catch (_: Exception) {
        }
    }

    private fun remember(uk: String, v: String) {
        synchronized(lock) {
            if (index[uk] == v) return
            index[uk] = v
            if (++dirty >= 25) saveLocked()
        }
    }

    private fun saveLocked() {
        val d = dir ?: return
        try {
            val sb = StringBuilder()
            for ((k, v) in index) sb.append(k).append(' ').append(v).append('\n')
            val tmp = File(d, "index.tmp")
            tmp.writeText(sb.toString())
            val dst = File(d, "index.txt")
            if (!tmp.renameTo(dst)) {
                dst.writeText(sb.toString())
                tmp.delete()
            }
            dirty = 0
        } catch (_: Exception) {
        }
    }

    /** Записывает индекс на диск (вызывается, когда приложение уходит в фон). */
    fun flush() {
        synchronized(lock) { if (dirty > 0) saveLocked() }
    }

    /** Сколько места занимает кэш обложек на диске (байты). */
    fun sizeBytes(ctx: Context): Long = ensureDir(ctx).listFiles()?.sumOf { it.length() } ?: 0L

    /** Полная очистка (при смене папки и «Пересканировать»): так подхватываются изменённые обложки. */
    fun clear(ctx: Context) {
        val d = ensureDir(ctx)
        synchronized(lock) {
            index.clear()
            d.listFiles()?.forEach { it.delete() }
            dirty = 0
        }
    }

    /** Убирает записи и картинки треков, которых больше нет в библиотеке, чтобы кэш не копил мусор. */
    fun prune(ctx: Context, validUris: Collection<String>) {
        val d = ensureDir(ctx)
        val valid = HashSet<String>(validUris.size * 2)
        for (u in validUris) valid.add(sha(u.toByteArray()))
        synchronized(lock) {
            index.keys.retainAll(valid)
            val used = index.values.filter { it != NONE }.toHashSet()
            d.listFiles()?.forEach { f ->
                if (f.name != "index.txt" && f.name.substringBefore('_') !in used) f.delete()
            }
            saveLocked()
        }
    }

    private fun sha(b: ByteArray): String {
        val h = MessageDigest.getInstance("SHA-1").digest(b)
        val sb = StringBuilder(16)
        for (i in 0 until 8) sb.append("%02x".format(h[i]))
        return sb.toString()
    }
}

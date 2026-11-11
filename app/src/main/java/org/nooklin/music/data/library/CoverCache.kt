package org.nooklin.music.data.library

import android.graphics.Bitmap
import android.util.LruCache

/** Covers kept in memory (about an eighth of what the app may use). The saved copies are in [CoverDisk]. */
object CoverCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(16L * 1024 * 1024, 64L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun key(id: String, sizePx: Int) = "$id@$sizePx"
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}

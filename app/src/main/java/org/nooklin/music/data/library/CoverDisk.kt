package org.nooklin.music.data.library

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * The covers as the server sent them (the raw image bytes), kept in the cache folder of the app so that a cover is
 * fetched once. The oldest ones go when the folder gets over [MAX_BYTES]. The system may also empty the cache folder;
 * that only means the covers are fetched again.
 */
class CoverDisk private constructor(context: Context) {
    private val folder = File(context.cacheDir, "covers")
    private var puts = 0

    /** The saved bytes of this cover, or null. 0.0.148: not under the lock; put() writes a .part file and renames it, so a read never sees half a file. */
    fun get(id: String, sizePx: Int): ByteArray? {
        return try {
            val file = fileOf(id, sizePx)
            if (!file.isFile) return null
            val bytes = file.readBytes()
            // Used now: it stays longest
            file.setLastModified(System.currentTimeMillis())
            if (bytes.isEmpty()) null else bytes
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun put(id: String, sizePx: Int, bytes: ByteArray) {
        try {
            folder.mkdirs()
            val file = fileOf(id, sizePx)
            val part = File(folder, file.name + ".part")
            part.writeBytes(bytes)
            if (!part.renameTo(file)) part.delete()
            puts++
            if (puts % TRIM_EVERY == 1) trim()
        } catch (e: Exception) {
            // Not saving a cover is not a problem
        }
    }

    /** The size of the saved covers in bytes. */
    @Synchronized
    fun size(): Long = folder.listFiles()?.sumOf { it.length() } ?: 0L

    @Synchronized
    fun clear() {
        folder.listFiles()?.forEach { it.delete() }
    }

    private fun trim() {
        val files = folder.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= KEEP_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    private fun fileOf(id: String, sizePx: Int): File {
        val digest = MessageDigest.getInstance("SHA-1").digest("$id@$sizePx".toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(folder, "$name.img")
    }

    companion object {
        private const val MAX_BYTES = 150L * 1024 * 1024
        private const val KEEP_BYTES = 120L * 1024 * 1024
        private const val TRIM_EVERY = 25

        @Volatile
        private var instance: CoverDisk? = null

        fun get(context: Context): CoverDisk =
            instance ?: synchronized(this) {
                instance ?: CoverDisk(context.applicationContext).also { instance = it }
            }
    }
}

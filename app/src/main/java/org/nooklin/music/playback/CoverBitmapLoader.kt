@file:OptIn(UnstableApi::class)

package org.nooklin.music.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.nooklin.music.data.library.CoverCache
import org.nooklin.music.data.server.ServerSession

/** Gives the notification and lock screen the cover from the server (`nooklin://cover/ID`). */
class CoverBitmapLoader(context: Context) : BitmapLoader {
    private val session = ServerSession.get(context)
    private val executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return executor.submit(Callable {
            BitmapFactory.decodeByteArray(data, 0, data.size) ?: throw IOException("Could not read the image.")
        })
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return executor.submit(Callable {
            val id = uri.lastPathSegment ?: throw IOException("No cover id.")
            val key = CoverCache.key(id, SIZE_PX)
            CoverCache.get(key)
                ?: runBlocking { session.cover(id, SIZE_PX) }?.also { CoverCache.put(key, it) }
                ?: throw IOException("No cover available.")
        })
    }

    private companion object {
        const val SIZE_PX = 512
    }
}

package org.nooklin.music

import android.app.Application
import org.nooklin.music.data.download.DownloadManager
import org.nooklin.music.playback.taste.HomeMixes
import org.nooklin.music.playback.taste.TasteStore
import kotlin.concurrent.thread

/**
 * 0.0.148: the three stores that read a file when they are first used (the download list, the taste list, the home mixes)
 * are opened here on a background thread, so the first screen no longer waits for them on the main thread.
 * The stores are singletons with a synchronized get(), so a screen that asks earlier simply waits for the same instance.
 */
class NooklinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        thread(name = "nooklin-warmup", isDaemon = true) {
            try { DownloadManager.get(this) } catch (_: Exception) {}
            try { TasteStore.get(this) } catch (_: Exception) {}
            try { HomeMixes.get(this) } catch (_: Exception) {}
        }
    }
}

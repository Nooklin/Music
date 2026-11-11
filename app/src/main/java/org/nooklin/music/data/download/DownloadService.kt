package org.nooklin.music.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.nooklin.music.MainActivity

/**
 * Keeps the app alive while there are downloads to do, with a notification that shows the progress. It does not
 * download anything itself: [DownloadManager] does, this service only asks Android not to stop the process. It stops
 * itself when the queue is empty or paused.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watching: Job? = null
    private var lastShown = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go to the foreground at once, before the first update of the queue arrives
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            build("Downloading", "Preparing", 0, true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (watching == null) watching = scope.launch { watch() }
        return START_NOT_STICKY
    }

    private suspend fun watch() {
        val downloads = DownloadManager.get(this)
        combine(downloads.jobs, downloads.paused) { jobs, paused -> jobs to paused }.collect { (jobs, paused) ->
            val open = jobs.filter { it.state != JobState.FAILED }
            if (paused || open.isEmpty()) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@collect
            }
            val active = open.firstOrNull { it.state == JobState.ACTIVE }
            val first = active ?: open.first()
            val title = if (open.size == 1) "Downloading 1 song" else "Downloading ${open.size} songs"
            val text = when (first.state) {
                JobState.WAITING_WIFI -> "Waiting for Wi-Fi"
                JobState.WAITING_NETWORK -> "Waiting for a connection"
                else -> first.song.title
            }
            val percent = if (active != null && active.total > 0) ((active.received * 100) / active.total).toInt().coerceIn(0, 100) else 0
            val indeterminate = active == null || active.total <= 0
            val key = "$title|$text|$percent|$indeterminate"
            if (key != lastShown) {
                lastShown = key
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(title, text, percent, indeterminate))
            }
        }
    }

    private fun build(title: String, text: String, percent: Int, indeterminate: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    // Android 15 limits this kind of service to a few hours a day. The downloads then go on only while the app lives.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 21

        /** Starts the service. Android refuses this when the app is not allowed to start it now; the downloads then still run. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            } catch (e: IllegalStateException) {
                // Not allowed from the background
            } catch (e: SecurityException) {
                // The permission or service type was refused
            }
        }
    }
}

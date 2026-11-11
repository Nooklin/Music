package org.nooklin.music

import android.util.Log

/**
 * 0.0.145: diagnostics only. Every 2 seconds the stacks of the threads that are running (not waiting) are written to the log under
 * the tag NooklinProf, so that a log of a slow moment shows which functions use the processor. It changes nothing else.
 * Read it with: adb logcat -s NooklinProf
 */
object StackSampler {
    private const val TAG = "NooklinProf"
    private const val INTERVAL_MS = 2_000L
    private const val FRAMES = 12
    private const val MAX_THREADS = 8

    @Volatile
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        val sampler = Thread({
            while (true) {
                try {
                    Thread.sleep(INTERVAL_MS)
                    sample()
                } catch (e: InterruptedException) {
                    return@Thread
                } catch (e: Exception) {
                    Log.w(TAG, "sample failed: ${e.message}")
                }
            }
        }, "nooklin-prof")
        sampler.isDaemon = true
        sampler.start()
    }

    private fun sample() {
        val self = Thread.currentThread()
        var logged = 0
        for ((thread, stack) in Thread.getAllStackTraces()) {
            if (thread === self || logged >= MAX_THREADS) continue
            if (thread.state != Thread.State.RUNNABLE || stack.isEmpty()) continue
            // A thread that waits for work (the looper of the main thread, a network poll) is not using the processor
            val top = stack[0].methodName
            if (top == "nativePollOnce" || top == "epollWait" || top == "poll" || top == "read" && stack[0].className.contains("Os")) continue
            logged++
            Log.i(TAG, "${thread.name} | " + stack.take(FRAMES).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" })
        }
    }
}

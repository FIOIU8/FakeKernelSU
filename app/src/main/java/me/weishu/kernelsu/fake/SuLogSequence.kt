package me.weishu.kernelsu.fake

import java.io.File

/**
 * Hands out the `seq=` numbers of the fake su log.
 *
 * Upstream's `sulog` numbers the lines of a daily log file starting at 1. The
 * fork used to recompute that number on every append by counting the lines of
 * the whole file, which is O(n) per line and - because the count and the write
 * were not one step - could hand the same number to two concurrent requests.
 *
 * The counter therefore lives in memory, is seeded once from the file that is
 * already there, and is handed out under a lock. Getting a number and writing
 * the line that carries it still has to happen under the caller's lock;
 * [FakeSu] does exactly that.
 */
class SuLogSequence {

    private val lock = Any()
    private val counters = mutableMapOf<String, Int>()

    /** The next sequence number for [file]; the first line of a new file is 1. */
    fun next(file: File): Int = synchronized(lock) {
        val key = file.absolutePath
        val current = counters.getOrPut(key) { seed(file) }
        val next = current + 1
        counters[key] = next
        next
    }

    /** Forget every counter, e.g. after the demo data was wiped. */
    fun reset() = synchronized(lock) { counters.clear() }

    private fun seed(file: File): Int {
        if (!file.exists()) return 0
        // A read failure must not make the counter restart at 1 on a file that
        // already has lines; the caller only uses seq for display, but a
        // duplicate number is exactly what this class exists to avoid.
        return runCatching { file.readLines().count { it.isNotBlank() } }.getOrDefault(0)
    }
}

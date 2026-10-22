package me.weishu.kernelsu.fake

import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A small JSON document store for the demo's persistent state.
 *
 * Upstream keeps this data inside the kernel module / ksud; the demo keeps it
 * in the app's private directory. Two properties matter here:
 *
 *  - **Atomic writes** - the document is written to a temporary file which is
 *    then renamed over the target. A process death in the middle of a write
 *    therefore leaves the previous, complete document behind instead of a
 *    half-written one. (The old code used `File.writeText()`, which truncates
 *    first: a crash there corrupted the file and the silent `runCatching`
 *    fallback made every saved setting and every granted root profile vanish
 *    without a trace.)
 *  - **No lost updates** - read-modify-write cycles run under a lock, so two
 *    concurrent writers cannot overwrite each other.
 *
 * The parsed document is cached in memory and invalidated on every write, so
 * reading a single entry does not touch the disk.
 */
class JsonStore<T>(
    private val fileProvider: () -> File,
    private val decode: (String) -> T,
    private val encode: (T) -> String,
    private val empty: () -> T,
) {
    private val lock = ReentrantLock()
    private var cached: T? = null

    /** The current document, read from disk only when the cache is cold. */
    fun read(): T = lock.withLock { load() }

    /** Replaces the document. */
    fun write(value: T) = lock.withLock {
        writeAtomically(encode(value))
        cached = value
    }

    /**
     * Read-modify-write in one atomic step.
     *
     * The block receives the current document and returns the new one; the
     * update is persisted before this function returns.
     */
    fun update(block: (T) -> T): T = lock.withLock {
        val updated = block(load())
        writeAtomically(encode(updated))
        cached = updated
        updated
    }

    /** Drops the in-memory copy; the next read re-parses the file. */
    fun invalidate() = lock.withLock { cached = null }

    private fun load(): T {
        cached?.let { return it }
        val file = fileProvider()
        if (!file.isFile) {
            return empty().also { cached = it }
        }
        val text = runCatching { file.readText() }.getOrElse {
            Log.w(TAG, "cannot read $file", it)
            return empty().also { cached = it }
        }
        return runCatching { decode(text) }.getOrElse {
            // Do not silently throw the data away: keep a copy so it can be
            // recovered, and continue with an empty document.
            Log.w(TAG, "corrupted document $file, starting over", it)
            runCatching {
                File(file.parentFile, "${file.name}.corrupt").writeText(text)
            }
            empty().also { cached = it }
        }
    }

    private fun writeAtomically(text: String) {
        val file = fileProvider()
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        // Move the finished document over the target. REPLACE_EXISTING is
        // required: File.renameTo refuses to overwrite an existing file on
        // Windows, which would turn every second write into a failure.
        // ATOMIC_MOVE is a hint - not every filesystem supports it.
        try {
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            tmp.delete()
            throw IOException("cannot replace $file", e)
        }
    }

    companion object {
        private const val TAG = "JsonStore"
    }
}

package me.weishu.kernelsu.fake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Unit tests for the fork's JSON document store.
 *
 * The store is the persistence layer behind the fake profiles, the fake
 * feature flags and the su log, so its two promises matter: a write never
 * leaves a half-written document behind, and a read-modify-write cycle never
 * loses an update.
 */
class JsonStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A trivial "k=v per line" codec - the real one uses org.json. */
    private fun store(file: File) = JsonStore<Map<String, Int>>(
        fileProvider = { file },
        decode = { text ->
            text.lineSequence()
                .filter { it.isNotBlank() }
                .associate { line ->
                    val (key, value) = line.split('=', limit = 2)
                    key to value.trim().toInt()
                }
        },
        encode = { map -> map.entries.joinToString("\n") { "${it.key}=${it.value}" } },
        empty = { emptyMap() },
    )

    @Test
    fun missingFileReadsAsAnEmptyDocument() {
        val file = File(temp.root, "absent.json")
        assertEquals(emptyMap<String, Int>(), store(file).read())
    }

    @Test
    fun writeThenReadReturnsTheWrittenDocument() {
        val file = temp.newFile("state.json")
        val store = store(file)
        store.write(mapOf("a" to 1))
        assertEquals(mapOf("a" to 1), store.read())
    }

    @Test
    fun writeReplacesAnExistingDocument() {
        val file = temp.newFile("state.json")
        val store = store(file)
        store.write(mapOf("a" to 1))
        store.write(mapOf("b" to 2))
        assertEquals(mapOf("b" to 2), store.read())
    }

    @Test
    fun updateIsPersistedImmediately() {
        val file = temp.newFile("state.json")
        val store = store(file)
        store.write(mapOf("count" to 1))
        val updated = store.update { doc -> doc + ("count" to (doc["count"] ?: 0) + 1) }
        assertEquals(mapOf("count" to 2), updated)
        // a second store over the same file must see the persisted result
        assertEquals(mapOf("count" to 2), store(file).read())
    }

    @Test
    fun aCorruptedDocumentIsKeptAsACorruptCopy() {
        val file = temp.newFile("state.json")
        file.writeText("this is not a valid document")
        val store = store(file)
        assertEquals(emptyMap<String, Int>(), store.read())
        val corrupt = File(file.parentFile, "${file.name}.corrupt")
        assertTrue("the unreadable document must be kept for recovery", corrupt.isFile)
        assertEquals("this is not a valid document", corrupt.readText())
    }

    @Test
    fun invalidateForcesTheDocumentToBeReadAgain() {
        val file = temp.newFile("state.json")
        val store = store(file)
        store.write(mapOf("a" to 1))
        file.writeText("b=2")
        // the cached copy is still the old one
        assertEquals(mapOf("a" to 1), store.read())
        store.invalidate()
        assertEquals(mapOf("b" to 2), store.read())
    }

    @Test
    fun concurrentUpdatesDoNotLoseWrites() {
        val file = temp.newFile("state.json")
        val store = store(file)
        store.write(mapOf("count" to 0))

        val threads = 8
        val perThread = 50
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            pool.submit {
                start.await()
                repeat(perThread) {
                    store.update { doc -> doc + ("count" to (doc["count"] ?: 0) + 1) }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))
        assertEquals(threads * perThread, store.read()["count"])
    }
}

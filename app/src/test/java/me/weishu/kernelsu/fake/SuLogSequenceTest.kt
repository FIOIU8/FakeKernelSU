package me.weishu.kernelsu.fake

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Unit tests for the su-log sequence numbers.
 *
 * `seq` is what makes the in-app su log look like ksud's: it must start at 1,
 * must not repeat inside one file, and must not restart at 1 when a log that
 * already has lines is opened again.
 */
class SuLogSequenceTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun aNewLogStartsAtOne() {
        val sequence = SuLogSequence()
        val file = File(temp.root, "sulog-new.log")

        assertEquals(1, sequence.next(file))
        assertEquals(2, sequence.next(file))
        assertEquals(3, sequence.next(file))
    }

    @Test
    fun anExistingLogContinuesAfterItsLastLine() {
        val file = temp.newFile("sulog-existing.log")
        file.writeText("seq=1 type=a\nseq=2 type=b\nseq=3 type=c\n")

        val sequence = SuLogSequence()
        assertEquals(4, sequence.next(file))
        assertEquals(5, sequence.next(file))
    }

    @Test
    fun blankLinesInAnExistingLogAreNotCounted() {
        val file = temp.newFile("sulog-blank.log")
        file.writeText("seq=1 type=a\n\n   \nseq=2 type=b\n")

        assertEquals(3, SuLogSequence().next(file))
    }

    @Test
    fun eachFileIsCountedOnItsOwn() {
        val sequence = SuLogSequence()
        val first = temp.newFile("sulog-a.log").apply { writeText("x\ny\n") }
        val second = temp.newFile("sulog-b.log")

        assertEquals(3, sequence.next(first))
        assertEquals(1, sequence.next(second))
        assertEquals(4, sequence.next(first))
    }

    @Test
    fun resetMakesTheNextNumberOneAgain() {
        val file = File(temp.root, "sulog-reset.log")
        val sequence = SuLogSequence()
        sequence.next(file)
        sequence.next(file)

        sequence.reset()

        assertEquals(1, sequence.next(file))
    }

    @Test
    fun concurrentCallsNeverHandOutTheSameNumber() {
        val file = File(temp.root, "sulog-concurrent.log")
        val sequence = SuLogSequence()
        val threads = 8
        val perThread = 50

        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val numbers = java.util.Collections.synchronizedList(mutableListOf<Int>())
        try {
            repeat(threads) {
                pool.submit {
                    start.await()
                    repeat(perThread) { numbers += sequence.next(file) }
                }
            }
            start.countDown()
            pool.shutdown()
            // 10s is generous; the work is in-memory.
            pool.awaitTermination(10, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        val expected = (1..threads * perThread).toList()
        assertEquals("every number must be handed out exactly once", expected, numbers.sorted())
    }
}

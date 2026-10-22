package me.weishu.kernelsu.fake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Unit tests for the module-zip rules.
 *
 * A module zip is untrusted input that ends up as files in the app's private
 * storage, so the three things that must never break are: the id never escapes
 * the module directory, no entry is written outside the target directory, and
 * a zip cannot expand past the size limits.
 */
class ModuleZipTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun zip(name: String, entries: Map<String, ByteArray>): ZipFile {
        val file = File(temp.root, name)
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (entryName, bytes) ->
                out.putNextEntry(ZipEntry(entryName))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return ZipFile(file)
    }

    private fun bytes(size: Int) = ByteArray(size) { 'x'.code.toByte() }

    // ------------------------------------------------------------- module ids

    @Test
    fun moduleIdsThatWouldEscapeTheModuleDirectoryAreRejected() {
        listOf(
            "",
            "a",            // too short for the [letter][letter..]+ rule
            "1module",      // no leading digit
            "-module",
            ".module",
            "../escape",
            "a/b",
            "a\\b",
            "a..b/..",
            "module\n",
        ).forEach { assertFalse("'$it' must be rejected", ModuleZip.isValidId(it)) }
    }

    @Test
    fun ordinaryModuleIdsAreAccepted() {
        listOf("ab", "KsuMod", "my-module_1.2", "z9").forEach {
            assertTrue("'$it' must be accepted", ModuleZip.isValidId(it))
        }
    }

    // ------------------------------------------------------------ module.prop

    @Test
    fun modulePropParsesKeyValueLinesAndSkipsComments() {
        val prop = ModuleZip.parseProp(
            """
            # a comment
            id=demo_module
            name = Demo Module
            version=v1.0
            description=has = inside
            broken line
            """.trimIndent()
        )
        assertEquals("demo_module", prop["id"])
        assertEquals("Demo Module", prop["name"])
        assertEquals("v1.0", prop["version"])
        // only the first '=' splits, so the rest of the value survives
        assertEquals("has = inside", prop["description"])
        assertNull(prop["# a comment"])
        assertEquals(4, prop.size)
    }

    @Test
    fun emptyModulePropYieldsNothing() {
        assertTrue(ModuleZip.parseProp("").isEmpty())
        assertTrue(ModuleZip.parseProp("\n\n#only a comment\n").isEmpty())
    }

    // ------------------------------------------------------------- extraction

    @Test
    fun extractionWritesEveryEntry() {
        val target = File(temp.root, "module")
        zip(
            "ok.zip",
            mapOf(
                "module.prop" to "id=ab\n".toByteArray(),
                "system/bin/tool" to bytes(16),
                "webroot/index.html" to "<html/>".toByteArray(),
            ),
        ).use { archive ->
            assertEquals(ModuleZip.Result.Ok, ModuleZip.extract(archive, target))
        }
        assertEquals(16, File(target, "system/bin/tool").length())
        assertEquals("<html/>", File(target, "webroot/index.html").readText())
        assertEquals("id=ab\n", File(target, "module.prop").readText())
    }

    @Test
    fun modulePropIsReportedOnStdout() {
        val target = File(temp.root, "module")
        val stdout = mutableListOf<String>()
        zip("prop.zip", mapOf("module.prop" to "id=ab\n".toByteArray())).use { archive ->
            ModuleZip.extract(archive, target, onStdout = { stdout += it })
        }
        assertTrue(stdout.any { it.contains("module.prop") })
    }

    @Test
    fun entriesThatEscapeTheTargetAreSkipped() {
        val target = File(temp.root, "module")
        val stderr = mutableListOf<String>()
        zip(
            "escape.zip",
            mapOf(
                "module.prop" to "id=ab\n".toByteArray(),
                "../escaped.txt" to bytes(4),
                "../../escaped-two.txt" to bytes(4),
            ),
        ).use { archive ->
            ModuleZip.extract(archive, target, onStderr = { stderr += it })
        }

        // Nothing outside the module directory, the safe entry still landed.
        assertFalse(File(temp.root, "escaped.txt").exists())
        assertFalse(File(temp.root.parentFile, "escaped-two.txt").exists())
        assertTrue(File(target, "module.prop").exists())
        assertTrue(stderr.any { it.contains("unsafe entry name") })
    }

    @Test
    fun aTraversalThatStaysInsideIsAllowed() {
        val target = File(temp.root, "module")
        zip("inside.zip", mapOf("webroot/../module.prop" to "id=ab\n".toByteArray())).use { archive ->
            assertEquals(ModuleZip.Result.Ok, ModuleZip.extract(archive, target))
        }
        assertTrue(File(target, "module.prop").exists())
    }

    @Test
    fun anEntryLargerThanTheLimitRejectsTheArchive() {
        val target = File(temp.root, "module")
        val stderr = mutableListOf<String>()
        zip("big-entry.zip", mapOf("payload.bin" to bytes(2048))).use { archive ->
            val result = ModuleZip.extract(
                archive,
                target,
                ModuleZip.Limits(entrySize = 1024),
                onStderr = { stderr += it },
            )
            assertTrue(result is ModuleZip.Result.Rejected)
            assertTrue(stderr.any { it.contains("larger than") })
        }
    }

    @Test
    fun anArchiveThatExpandsTooMuchRejectsTheArchive() {
        val target = File(temp.root, "module")
        val stderr = mutableListOf<String>()
        // Each entry fits, the two together do not.
        zip(
            "big-total.zip",
            mapOf(
                "a.bin" to bytes(600),
                "b.bin" to bytes(600),
            ),
        ).use { archive ->
            val result = ModuleZip.extract(
                archive,
                target,
                ModuleZip.Limits(entrySize = 1024, totalSize = 1000),
                onStderr = { stderr += it },
            )
            assertTrue(result is ModuleZip.Result.Rejected)
            assertTrue(stderr.any { it.contains("expands to more than") })
        }
    }

    @Test
    fun tooManyEntriesRejectsTheArchive() {
        val target = File(temp.root, "module")
        zip(
            "many.zip",
            mapOf(
                "a.txt" to bytes(1),
                "b.txt" to bytes(1),
                "c.txt" to bytes(1),
            ),
        ).use { archive ->
            val result = ModuleZip.extract(archive, target, ModuleZip.Limits(entryCount = 2))
            assertTrue(result is ModuleZip.Result.Rejected)
        }
    }

    @Test
    fun directoryEntriesAreIgnored() {
        val target = File(temp.root, "module")
        val file = File(temp.root, "dirs.zip")
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("system/"))
            out.closeEntry()
            out.putNextEntry(ZipEntry("module.prop"))
            out.write("id=ab\n".toByteArray())
            out.closeEntry()
        }
        ZipFile(file).use { archive ->
            assertEquals(ModuleZip.Result.Ok, ModuleZip.extract(archive, target))
        }
        assertTrue(File(target, "module.prop").exists())
    }
}

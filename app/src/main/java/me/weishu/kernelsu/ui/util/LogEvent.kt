package me.weishu.kernelsu.ui.util

import android.content.Context
import android.os.Build
import android.system.Os
import com.topjohnwu.superuser.ShellUtils
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.fake.FakeState
import me.weishu.kernelsu.ui.screen.home.getManagerVersion
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream

/**
 * Collects the bugreport.
 *
 * FakeKernelSU only ever touches its own sandbox. Upstream - which runs with
 * root - additionally collected `/data/adb`, `/data/system/dropbox`,
 * `/data/tombstones`, `/data/vendor/diag`, several `/proc` files and even
 * rewrote `/proc/sys/kernel/kptr_restrict` to dump the kernel symbols.
 *
 * A demo that promises to leave the system alone must not do any of that, so
 * the report contains the app's own state (the fake `/data/adb` tree, the
 * module list) plus a handful of unprivileged system properties.
 *
 * The archive is written in pure Java: no shell, and therefore no chance of
 * the collector ending up with more privileges than the app itself.
 */
fun getBugreportFile(context: Context): File {

    val bugreportDir = File(context.cacheDir, "bugreport")
    bugreportDir.mkdirs()

    // ---------------------------------------------------------- own state
    File(bugreportDir, "modules.json").writeText(listModules())
    copyTree(FakeState.adbRoot, File(bugreportDir, "fake_data"))

    // ------------------------------------------- unprivileged system info
    val shell = runCatching { getRootShell(true) }.getOrNull()
    val selinux = shell?.let { runCatching { ShellUtils.fastCmd(it, "getenforce") }.getOrNull() }
        ?: ""
    val props = shell?.let { runCatching { ShellUtils.fastCmd(it, "getprop") }.getOrNull() }
        ?: ""
    File(bugreportDir, "props.txt").writeText(props)

    // basic information
    val buildInfo = File(bugreportDir, "basic.txt")
    PrintWriter(FileWriter(buildInfo)).use { pw ->
        pw.println("Kernel: ${System.getProperty("os.version")}")
        pw.println("BRAND: " + Build.BRAND)
        pw.println("MODEL: " + Build.MODEL)
        pw.println("PRODUCT: " + Build.PRODUCT)
        pw.println("MANUFACTURER: " + Build.MANUFACTURER)
        pw.println("SDK: " + Build.VERSION.SDK_INT)
        pw.println("PREVIEW_SDK: " + Build.VERSION.PREVIEW_SDK_INT)
        pw.println("FINGERPRINT: " + Build.FINGERPRINT)
        pw.println("DEVICE: " + Build.DEVICE)
        pw.println("Manager: " + getManagerVersion(context))
        pw.println("SELinux: $selinux")
        pw.println("Note: FakeKernelSU collects only its own private data.")

        val uname = Os.uname()
        pw.println("KernelRelease: ${uname.release}")
        pw.println("KernelVersion: ${uname.version}")
        pw.println("Machine: ${uname.machine}")
        pw.println("Nodename: ${uname.nodename}")
        pw.println("Sysname: ${uname.sysname}")

        val ksuKernel = Natives.version
        pw.println("KernelSU: $ksuKernel")
        val safeMode = Natives.isSafeMode
        pw.println("SafeMode: $safeMode")
        val lkmMode = Natives.isLkmMode
        pw.println("LKM: $lkmMode")
    }

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
    val current = LocalDateTime.now().format(formatter)

    val targetFile = File(context.cacheDir, "KernelSU_bugreport_${current}.tar.gz")
    createTarGz(bugreportDir, targetFile)

    bugreportDir.deleteRecursively()

    return targetFile
}

/** Copies [source] into [target], keeping the relative layout. */
private fun copyTree(source: File, target: File) {
    if (!source.isDirectory) return
    source.walkTopDown().filter { it.isFile }.forEach { file ->
        val destination = File(target, file.relativeTo(source).path)
        destination.parentFile?.mkdirs()
        runCatching { file.copyTo(destination, overwrite = true) }
    }
}

/**
 * Packs [sourceDir] into a gzipped tar archive at [target].
 *
 * Done in Java on purpose: the demo does not need a shell to produce a
 * report, and using one would be the only remaining way for the collector to
 * end up running with more privileges than the app.
 */
private fun createTarGz(sourceDir: File, target: File) {
    target.outputStream().use { fileOut ->
        GZIPOutputStream(fileOut).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                sourceDir.walkTopDown().forEach { file ->
                    val relative = file.relativeTo(sourceDir).path
                    if (relative.isEmpty()) return@forEach
                    val entry = TarArchiveEntry(if (file.isDirectory) "$relative/" else relative)
                    if (file.isFile) {
                        entry.size = file.length()
                        tar.putArchiveEntry(entry)
                        file.inputStream().use { it.copyTo(tar) }
                    } else {
                        tar.putArchiveEntry(entry)
                    }
                    tar.closeArchiveEntry()
                }
                tar.finish()
            }
        }
    }
}

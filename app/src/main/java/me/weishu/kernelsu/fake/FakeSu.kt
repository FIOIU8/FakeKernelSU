package me.weishu.kernelsu.fake

import android.os.SystemClock
import android.util.Log
import me.weishu.kernelsu.Natives
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Simulates `su` requests that a real device would route through the kernel.
 *
 * A "request" does two things, exactly like the real stack:
 *  1. if the user grants and picks "remember", the app profile gets allowSu=true
 *     (persisted in profiles.json, the stand-in for the kernel allowlist);
 *  2. a line is appended to the ksud-format sulog so the in-app su log page
 *     shows the event.
 */
object FakeSu {

    private const val TAG = "FakeSu"

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun sulogDir(): File = File(FakeState.ksuDir, "log")

    private fun todaySulogFile(): File {
        val dir = sulogDir()
        dir.mkdirs()
        return File(dir, "sulog-${LocalDate.now().format(DATE_FORMAT)}.log")
    }

    private fun nextSeq(file: File): Int {
        if (!file.exists()) return 1
        return runCatching {
            file.readLines().count { it.isNotBlank() } + 1
        }.getOrDefault(1)
    }

    private fun appendLine(type: String, extra: Map<String, String>) {
        runCatching {
            val file = todaySulogFile()
            val seq = nextSeq(file)
            val fields = buildMap {
                put("ts_ns", SystemClock.elapsedRealtimeNanos().toString())
                put("type", type)
                put("seq", seq.toString())
                putAll(extra)
            }
            val line = fields.entries.joinToString(" ") { "${it.key}=${it.value}" }
            file.appendText(line + "\n")
        }.onFailure { Log.w(TAG, "failed to append sulog line", it) }
    }

    /** Written when the fake install completes — mimics ksud's boot event. */
    fun logDaemonStart() {
        appendLine(
            "daemon_start",
            mapOf("boot_id" to System.currentTimeMillis().toString(16)),
        )
    }

    /** Written when the user enters jailbreak (late-load) mode. */
    fun logLateLoad() {
        appendLine(
            "late_load",
            mapOf(
                "method" to "runtime",
                "uid" to Natives.ROOT_UID.toString(),
            ),
        )
    }

    /**
     * @param granted  whether the user tapped "allow" in the fake dialog
     * @param remember whether the grant should persist (allowSu profile)
     */
    fun recordRequest(
        packageName: String,
        appName: String,
        uid: Int,
        granted: Boolean,
        remember: Boolean,
    ) {
        appendLine(
            "root_execve",
            mapOf(
                "uid" to uid.toString(),
                "pkg" to packageName,
                "app" to appName,
                "path" to "/system/bin/su",
                "granted" to granted.toString(),
            ),
        )
        if (granted && remember) {
            val profile = Natives.getAppProfile(packageName, uid)
            Natives.setAppProfile(profile.copy(allowSu = true, currentUid = uid))
        }
    }

    /** Revoke a persisted grant (used by the superuser list UI). */
    fun revoke(packageName: String, uid: Int) {
        val profile = Natives.getAppProfile(packageName, uid)
        Natives.setAppProfile(profile.copy(allowSu = false, currentUid = uid))
    }

    /** Grant without going through the dialog (used by the superuser list UI). */
    fun grant(packageName: String, uid: Int) {
        val profile = Natives.getAppProfile(packageName, uid)
        Natives.setAppProfile(profile.copy(allowSu = true, currentUid = uid))
    }
}

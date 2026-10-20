package me.weishu.kernelsu.fake

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.weishu.kernelsu.Natives
import java.io.File

/**
 * Local stand-in for the kernel-side app profile table.
 *
 * Upstream keeps profiles inside the kernel module and only persists the
 * "rules" string via ksud; the fake manager keeps everything in
 * filesDir/adb/ksu/profiles.json.
 */
object FakeProfiles {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val lock = Any()

    private fun readAll(): MutableMap<String, Natives.Profile> = synchronized(lock) {
        if (!FakeState.profilesFile.exists()) return mutableMapOf()
        runCatching {
            json.decodeFromString<Map<String, Natives.Profile>>(FakeState.profilesFile.readText())
                .toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun writeAll(map: Map<String, Natives.Profile>) = synchronized(lock) {
        runCatching {
            FakeState.ensureDirs()
            FakeState.profilesFile.writeText(json.encodeToString(map))
        }
    }

    fun get(key: String, uid: Int): Natives.Profile =
        readAll()[key] ?: Natives.Profile(name = key, currentUid = uid)

    fun set(profile: Natives.Profile) {
        val map = readAll()
        map[profile.name] = profile
        writeAll(map)
    }

    fun all(): Map<String, Natives.Profile> = readAll()

    fun grantedCount(): Int = readAll().values.count { it.allowSu }

    fun clear() {
        runCatching { FakeState.profilesFile.delete() }
    }
}

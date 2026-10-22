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
 *
 * The document is read once and cached: the superuser list asks for a profile
 * per installed app, and re-reading and re-parsing the file for every single
 * app turned the list into hundreds of disk reads.
 */
object FakeProfiles {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val store = JsonStore<Map<String, Natives.Profile>>(
        fileProvider = { FakeState.profilesFile },
        decode = { text -> json.decodeFromString<Map<String, Natives.Profile>>(text) },
        encode = { map -> json.encodeToString(map) },
        empty = { emptyMap() },
    )

    fun get(key: String, uid: Int): Natives.Profile =
        store.read()[key] ?: Natives.Profile(name = key, currentUid = uid)

    fun set(profile: Natives.Profile) {
        store.update { map ->
            val next = LinkedHashMap(map)
            next[profile.name] = profile
            next
        }
    }

    fun all(): Map<String, Natives.Profile> = store.read()

    fun grantedCount(): Int = store.read().values.count { it.allowSu }

    /** Drops the document and the cached copy. */
    fun clear() {
        runCatching { FakeState.profilesFile.delete() }
        store.invalidate()
    }
}

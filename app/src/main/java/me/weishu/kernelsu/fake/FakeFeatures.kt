package me.weishu.kernelsu.fake

import org.json.JSONObject
import java.io.File

/**
 * Emulates the `features.json` store upstream ksud keeps in /data/adb/ksu.
 *
 * Values are `0` / `1` longs, exactly the semantics of `ksud feature set`.
 * Both [me.weishu.kernelsu.ui.util.execKsud] and [me.weishu.kernelsu.Natives]
 * read and write through here, so the settings toggles and the home page
 * never disagree.
 */
object FakeFeatures {
    private const val FILE_NAME = "features.json"

    private val store = JsonStore<JSONObject>(
        fileProvider = { File(FakeState.ksuDir, FILE_NAME) },
        decode = { text -> JSONObject(text) },
        encode = { features -> features.toString() },
        empty = { JSONObject() },
    )

    fun read(): JSONObject = store.read()

    fun write(features: JSONObject) {
        store.write(features)
    }

    /** Current value of [name], falling back to the per-feature default. */
    fun get(name: String): Long = store.read().optLong(name, defaultValue(name))

    /** Persist a single feature value. Returns false when the write failed. */
    fun set(name: String, value: Long): Boolean = runCatching {
        store.update { features ->
            // Work on a copy: the cached document must not change before the
            // write actually succeeded.
            val next = JSONObject(features.toString())
            next.put(name, value)
            next
        }
    }.isSuccess

    /** Register a boolean feature as `0` / `1`. */
    fun setEnabled(name: String, enabled: Boolean): Boolean = set(name, if (enabled) 1L else 0L)

    fun isEnabled(name: String): Boolean = get(name) == 1L

    /** Drops the document and the cached copy. */
    fun clear() {
        runCatching { File(FakeState.ksuDir, FILE_NAME).delete() }
        store.invalidate()
    }

    /** Same defaults upstream ships with. */
    fun defaultValue(feature: String): Long = when (feature) {
        "su_compat" -> 1L
        "kernel_umount" -> 1L
        "selinux_hide" -> 1L
        "sulog" -> 1L
        "adb_root" -> 0L
        "shell_su_adb_only" -> 0L
        else -> 0L
    }
}

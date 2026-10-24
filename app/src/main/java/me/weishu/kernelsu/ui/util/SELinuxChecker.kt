package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.Shell

/**
 * Returns the raw SELinux status string ("Enforcing", "Permissive", "Disabled", or "Unknown").
 * Safe to call from any thread (IO recommended).
 */
fun getSELinuxStatusRaw(): String {
    // FakeKernelSU: libsu's builder tries `su` before falling back to a plain
    // `sh`, so the default builder would hand this demo a **root** shell on a
    // rooted device just to run `getenforce`. FLAG_NON_ROOT_SHELL skips both
    // `su` attempts - the same rule createRootShell() applies in KsuCli.
    val shell = Shell.Builder.create()
        .setFlags(Shell.FLAG_NON_ROOT_SHELL)
        .build("sh")

    val stdoutList = ArrayList<String>()
    val stderrList = ArrayList<String>()
    val result = shell.use {
        it.newJob().add("getenforce").to(stdoutList, stderrList).exec()
    }
    val stdout = stdoutList.joinToString("\n").trim()
    val stderr = stderrList.joinToString("\n").trim()

    if (result.isSuccess) {
        return when (stdout) {
            "Enforcing", "Permissive", "Disabled" -> stdout
            else -> "Unknown"
        }
    }

    return if (stderr.endsWith("Permission denied")) {
        "Enforcing"
    } else {
        "Unknown"
    }
}

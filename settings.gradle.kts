@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// Upstream's manager/ module is called "KernelSU"; this repository is the
// fork, so the Gradle project follows the fork's name. The APK file name is
// built from `managerName` in app/build.gradle.kts and is not affected.
rootProject.name = "FakeKernelSU"
include(":app")

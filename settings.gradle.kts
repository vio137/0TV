pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Kadb ships Java 25 bytecode; tests that load it run on a downloaded JDK 25.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Kadb's SPAKE2 (Wireless debugging pairing) is only published on JitPack.
        maven("https://jitpack.io") { content { includeGroup("com.github.Flyfish233") } }
    }
}

rootProject.name = "tidytv"
include(":core", ":adb", ":app")

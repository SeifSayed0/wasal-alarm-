plugins {
    id("com.android.application") version "9.4.0" apply false
    // AGP 9 provides Kotlin for Android source sets; this is only the Compose compiler plugin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

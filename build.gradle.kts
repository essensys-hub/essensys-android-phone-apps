// Top-level build file. Versions : gradle/libs.versions.toml.
// AGP 9 intègre Kotlin (built-in Kotlin) : pas de plugin org.jetbrains.kotlin.android.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

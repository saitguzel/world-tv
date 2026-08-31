// AGP 9 supplies Kotlin itself, but it pins KGP to the version it was built against
// (2.2.10) — older than the stdlib the current AndroidX releases put on the compile
// classpath, which makes even kotlin.Unit unreadable. The classpath is raised to the
// catalog's Kotlin version here, the documented way to move built-in Kotlin forward.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

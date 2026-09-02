plugins {
    id("worldtv.android.library")
    id("worldtv.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.worldtv.core.network"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)

    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

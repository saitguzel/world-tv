plugins {
    id("worldtv.android.library")
    id("worldtv.android.compose")
    id("worldtv.android.hilt")
}

android {
    namespace = "com.worldtv.feature.player"
    testOptions {
        // Robolectric needs the merged resources to resolve stringResource() calls.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.designsystemTv)
    implementation(projects.core.designsystemMobile)
    implementation(projects.data.repository)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)

    implementation(projects.data.health)
    // PlayerFactory shares the health engine's OkHttp client via @MediaClient.
    implementation(projects.core.network)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.common)
    implementation(libs.okhttp)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Screen tests on the JVM. Robolectric hosts the Compose test rule; that rule is a
    // JUnit 4 Rule, and this module runs on the JUnit Platform, so the vintage engine
    // is what makes those tests execute at all — without it they are silently skipped.
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testRuntimeOnly(libs.junit.vintage.engine)
}

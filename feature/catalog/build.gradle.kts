plugins {
    id("worldtv.android.library")
    id("worldtv.android.compose")
    id("worldtv.android.hilt")
}

android {
    namespace = "com.worldtv.feature.catalog"
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

    // The focused-card preview needs a player of its own; the full player screen
    // lives in :feature:player and is not a dependency of browsing.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.common)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

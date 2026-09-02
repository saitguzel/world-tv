plugins {
    id("worldtv.android.library")
    id("worldtv.android.compose")
}

android {
    namespace = "com.worldtv.core.designsystem"
}

dependencies {
    // api, not implementation: ChannelCardState exposes Programme and
    // HealthBadge in its public signature.
    api(projects.core.model)
    // LocalFormFactor exposes FormFactor in its public type.
    api(projects.core.common)

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    // BackHandler, used by DoubleBackToExit.
    api(libs.androidx.activity.compose)
    api(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

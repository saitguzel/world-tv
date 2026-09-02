plugins {
    id("worldtv.android.library")
    id("worldtv.android.compose")
    id("worldtv.android.hilt")
}

android {
    namespace = "com.worldtv.feature.radio"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.designsystemTv)
    implementation(projects.core.designsystemMobile)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
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

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    // MediaController.buildAsync returns a Guava ListenableFuture.
    implementation(libs.guava)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.okhttp)
}

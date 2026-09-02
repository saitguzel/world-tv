// The phone half of the design system. Separate from :core:designsystem-tv so neither
// can reach the other's MaterialTheme — androidx.tv.material3 and
// androidx.compose.material3 are different CompositionLocal trees, and a component
// rendered under the wrong one compiles and runs while silently drawing wrong.
plugins {
    id("worldtv.android.library")
    id("worldtv.android.compose")
}

android {
    namespace = "com.worldtv.core.designsystem.mobile"
}

dependencies {
    // api: phone screens render these cards beside the neutral tokens and shared leaves.
    api(projects.core.designsystem)

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material3.window.size)
    api(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

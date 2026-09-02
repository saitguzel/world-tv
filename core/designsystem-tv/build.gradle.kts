// The TV half of the design system. Split from :core:designsystem so the phone half can
// depend on Material 3 without either being able to reach the other's MaterialTheme —
// those are separate CompositionLocal trees, and mixing them compiles silently and
// renders wrongly. A module boundary turns that class of bug into a compile error.
plugins {
    id("worldtv.android.library")
    id("worldtv.android.compose")
}

android {
    namespace = "com.worldtv.core.designsystem.tv"
}

dependencies {
    // api: consumers render TV cards next to the neutral tokens and shared leaves.
    api(projects.core.designsystem)

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.tv.material)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

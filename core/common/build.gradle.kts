plugins {
    id("worldtv.android.library")
    id("worldtv.android.hilt")
}

android {
    namespace = "com.worldtv.core.common"
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

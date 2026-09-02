plugins {
    id("worldtv.android.library")
    id("worldtv.android.hilt")
}

android {
    namespace = "com.worldtv.data.repository"
}

dependencies {
    api(projects.core.model)
    api(projects.data.health)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.network)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

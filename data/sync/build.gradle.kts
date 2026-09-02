plugins {
    id("worldtv.android.library")
    id("worldtv.android.hilt")
}

android {
    namespace = "com.worldtv.data.sync"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.data.repository)
    implementation(projects.data.health)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The XMLTV parser is plain JVM code (SAX, no Android), so its tests run here
// without an emulator — it is the part of the EPG pipeline most likely to be wrong.
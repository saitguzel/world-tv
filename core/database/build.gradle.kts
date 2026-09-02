plugins {
    id("worldtv.android.library")
    id("worldtv.android.hilt")
}

android {
    namespace = "com.worldtv.core.database"
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.generateKotlin", "true")
    }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)

    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    // StreamDaoSweepTest drives suspending DAO methods from `runBlocking`.
    androidTestImplementation(libs.kotlinx.coroutines.core)
}

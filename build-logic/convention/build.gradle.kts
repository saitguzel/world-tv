plugins {
    `kotlin-dsl`
}

group = "com.worldtv.buildlogic"

dependencies {
    // compileOnly, deliberately. The plugins are compiled against the AGP DSL but must
    // export nothing: the root build.gradle.kts raises the Kotlin Gradle plugin above
    // the version AGP pins, and anything this build put on a runtime classpath would
    // take precedence over that and silently undo it. AGP itself reaches the modules
    // through the root `plugins {}` block, exactly as before.
    //
    // No Kotlin Gradle plugin dependency at all: `kotlin-dsl` compiles with Gradle's
    // embedded Kotlin, which cannot read the newer plugin's class metadata. KSP, Hilt
    // and the Compose compiler are applied by id and need no types here.
    compileOnly(libs.android.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "worldtv.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "worldtv.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "worldtv.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "worldtv.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "worldtv.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}

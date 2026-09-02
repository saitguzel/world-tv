import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * What every Android library module used to repeat, minus `namespace`.
 *
 * Nothing Kotlin-specific is applied: AGP 9 supplies Kotlin itself, and the modules
 * never applied `org.jetbrains.kotlin.android`. Adding it here would be a behaviour
 * change dressed up as a refactor.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk = WorldTvSdk.COMPILE
            defaultConfig {
                minSdk = WorldTvSdk.MIN
                testInstrumentationRunner = WorldTvSdk.TEST_RUNNER
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }

        // Without this, JUnit 5 tests do not fail — they do not run.
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
    }
}

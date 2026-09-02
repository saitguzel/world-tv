import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * The application module's share of the common configuration.
 *
 * `applicationId`, versioning, build types, R8 and packaging stay in :app — there is
 * one application module, and burying its release configuration in a plugin would
 * only make it harder to read.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = WorldTvSdk.COMPILE
            defaultConfig {
                minSdk = WorldTvSdk.MIN
                targetSdk = WorldTvSdk.TARGET
                testInstrumentationRunner = WorldTvSdk.TEST_RUNNER
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }

        tasks.withType<Test>().configureEach { useJUnitPlatform() }
    }
}

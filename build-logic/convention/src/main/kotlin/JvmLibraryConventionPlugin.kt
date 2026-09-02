import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * A plain Kotlin/JVM module: :core:model and :data:health.
 *
 * The Kotlin `jvmTarget` is left to the module. Setting it here would need the Kotlin
 * Gradle plugin on this build's compile classpath, and that plugin's class metadata is
 * newer than what Gradle's embedded Kotlin can read.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<Test>().configureEach { useJUnitPlatform() }
    }
}

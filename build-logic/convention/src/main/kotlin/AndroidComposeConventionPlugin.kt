import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Turns on Compose for a library or application module.
 *
 * Only the compiler plugin and the build feature. The Compose BOM and the runtime
 * artifacts stay in each module's `dependencies {}`: the design-system modules expose
 * them as `api` and the feature modules as `implementation`, and that difference is a
 * documented ABI decision, not duplication.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true
        extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true
    }
}

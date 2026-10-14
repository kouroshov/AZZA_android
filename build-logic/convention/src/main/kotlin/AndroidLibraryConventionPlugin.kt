import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            compileSdk = AzzaSdk.COMPILE
            defaultConfig {
                minSdk = AzzaSdk.MIN
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
        configureKotlin()
        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("truth").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}

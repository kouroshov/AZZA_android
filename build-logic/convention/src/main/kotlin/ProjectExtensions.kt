import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal object AzzaSdk {
    const val COMPILE = 37
    const val TARGET = 36
    // API 24 (Android 7.0) still covers the long tail of phones in use in Iran.
    const val MIN = 24
}

/**
 * Kotlin compiler settings shared by every module. AGP 9 compiles Kotlin
 * itself (built-in Kotlin), so there is no `kotlin.android` plugin to
 * configure — the compile tasks are configured directly.
 */
internal fun Project.configureKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            )
        }
    }
}

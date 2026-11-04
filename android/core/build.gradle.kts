import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

/**
 * Copies the shared iOS resources into the Android assets at build time, so both apps always use the
 * same texts and logos: Shared/Localization/liftr-<lang>.json → assets/localization,
 * Shared/GymLogos/<domain>.png → assets/gymlogos.
 */
abstract class CopySharedAssets : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localization: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val logos: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copyFiles() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val l10n = File(out, "localization").apply { mkdirs() }
        localization.get().asFile.listFiles { f -> f.name.endsWith(".json") }?.forEach { it.copyTo(File(l10n, it.name), true) }
        val gym = File(out, "gymlogos").apply { mkdirs() }
        logos.get().asFile.listFiles { f -> f.name.endsWith(".png") }?.forEach { it.copyTo(File(gym, it.name), true) }
    }
}

val copySharedAssets = tasks.register<CopySharedAssets>("copySharedAssets") {
    localization.set(rootProject.layout.projectDirectory.dir("../Shared/Localization"))
    logos.set(rootProject.layout.projectDirectory.dir("../Shared/GymLogos"))
    outputDir.set(layout.buildDirectory.dir("generated/sharedAssets"))
}

android {
    namespace = "nl.lrvdlinden.liftr.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copySharedAssets, CopySharedAssets::outputDir)
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(libs.coroutines.android)
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    api(libs.zxing.core)
}

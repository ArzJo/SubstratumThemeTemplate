// Top-level build file where you can add configuration options common to all sub-projects/modules.
import EncryptionUtil.cleanEncryptedAssets
import org.gradle.kotlin.dsl.register

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

val cleanTaskProvider = tasks.register("clean", Delete::class) {
    delete(project.layout.buildDirectory)
}

val cleanEncryptedAssetsTaskProvider = tasks.register("cleanEncryptedAssets") {
    group = "custom" // It can be useful to give a group to custom tasks
    description = "Clean the directory where assets are encrypted"
    projectDir.cleanEncryptedAssets()
}

cleanTaskProvider.configure {
    dependsOn(cleanEncryptedAssetsTaskProvider)
}
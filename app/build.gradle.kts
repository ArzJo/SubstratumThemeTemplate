@file:Suppress("DEPRECATION")

import ThemerConstants.BASE_64_LICENSE_KEY
import ThemerConstants.APK_SIGNATURE_PRODUCTION
import ThemerConstants.ENFORCE_GOOGLE_PLAY_INSTALL
import ThemerConstants.SHOULD_ENCRYPT_ASSETS
import ThemerConstants.ALLOW_THIRD_PARTY_SUBSTRATUM_BUILDS
import ThemerConstants.SUPPORTS_THIRD_PARTY_SYSTEMS

import EncryptionUtil.generateRandomByteArray
import EncryptionUtil.shouldBeEncrypted
import EncryptionUtil.assets
import EncryptionUtil.tempAssets
import EncryptionUtil.copyEncryptedTo
import EncryptionUtil.twistAsset
import EncryptionUtil.cleanEncryptedAssets

import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Encryption Key: DO NOT MODIFY!
val secretKey = generateRandomByteArray()
val ivKey = generateRandomByteArray()

// App configurations
val sdkVersion = 36
val packageName = "substratum.theme.template"
val appVersionCode = 1
val appVersionName = "1.0"
val appName = rootProject.name // apk/bundle file name

android {
    namespace = packageName
    compileSdk = sdkVersion

    defaultConfig {
        applicationId = packageName
        minSdk = 29
        targetSdk = sdkVersion
        versionCode = appVersionCode
        versionName = appVersionName
        setProperty("archivesBaseName", "$appName-$versionName[$versionCode]")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // DO NOT MODIFY!
        buildConfigField("boolean", "SUPPORTS_THIRD_PARTY_SYSTEMS", "" + SUPPORTS_THIRD_PARTY_SYSTEMS)
        buildConfigField("boolean", "ALLOW_THIRD_PARTY_SUBSTRATUM_BUILDS", "" + ALLOW_THIRD_PARTY_SUBSTRATUM_BUILDS)
        buildConfigField("String", "IV_KEY", "\"" + ivKey + "\"")
        buildConfigField("byte[]", "DECRYPTION_KEY", secretKey.joinToString(prefix = "{", postfix = "}"))
        buildConfigField("byte[]", "IV_KEY", ivKey.joinToString(prefix = "{", postfix = "}"))
        resValue("string", "encryption_status", if (shouldEncrypt()) "onCompileVerify" else "false")
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"

            // DO NOT MODIFY!
            buildConfigField("boolean", "ENFORCE_GOOGLE_PLAY_INSTALL", "false")
            buildConfigField("String", "BASE_64_LICENSE_KEY", "\"\"")
            buildConfigField("String", "APK_SIGNATURE_PRODUCTION", "\"\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // DO NOT MODIFY!
            buildConfigField("boolean", "ENFORCE_GOOGLE_PLAY_INSTALL", "$ENFORCE_GOOGLE_PLAY_INSTALL")
            buildConfigField("String", "BASE_64_LICENSE_KEY", "\"$BASE_64_LICENSE_KEY\"")
            buildConfigField("String", "APK_SIGNATURE_PRODUCTION", "\"$APK_SIGNATURE_PRODUCTION\"")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.piracychecker)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// DO NOT MODIFY!
tasks.register("encryptAssets") {
    if (!shouldEncrypt()) {
        println("Skipping assets encryption...")
        return@register
    }

    // Check if temp assets exist
    @DisableCachingByDefault
    if (!projectDir.tempAssets.exists()) {
        println("Encrypting duplicated assets, don't worry, your original assets are safe...")

        val secretKeySpec = SecretKeySpec(secretKey, "AES")
        val ivParameterSpec = IvParameterSpec(ivKey)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            .apply {
                init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
            }

        // Encrypt every single file in the assets dir recursively
        projectDir.assets.walkTopDown().filter { it.isFile }.forEach { file ->
            file.twistAsset("assets", "assets-temp")

            //Encrypt assets
            if (file.shouldBeEncrypted()) {
                FileInputStream(file).use { fis ->
                    FileOutputStream("${file.absolutePath}.enc").use { fos ->
                        fis.copyEncryptedTo(fos, cipher, bufferSize = 64)
                    }
                }
                file.delete()
            }
        }
    } else {
        throw RuntimeException("Old temporary assets found! Try and do a clean project.")
    }
}

android.applicationVariants.configureEach {
    if (buildType.name == "release") {
        tasks.named("encryptAssets").configure {
            outputs.upToDateWhen { false }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("encryptAssets")
}

gradle.buildFinished {
    if (shouldEncrypt()) {
        projectDir.cleanEncryptedAssets()
    }
}

fun shouldEncrypt(): Boolean {
    val tasks = project.gradle.startParameter.taskNames
    return SHOULD_ENCRYPT_ASSETS && tasks.joinToString().contains("release", ignoreCase = true)
}
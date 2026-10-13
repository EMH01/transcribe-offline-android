import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.emh01.transcribe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emh01.transcribeoffline"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "0.4.0-alpha7"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += "bin"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core-audio"))
    implementation(project(":core-speech"))
    implementation(project(":core-parakeet"))
    implementation(project(":core-text"))
    implementation(project(":core-llm"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val parakeetModelUrl =
    "https://huggingface.co/ggml-org/parakeet-GGUF/resolve/35156454d1a39de06863303dd209fd2bed6ee079/ggml-parakeet-tdt-0.6b-v3-q4_0.bin"
val parakeetModelSha256 =
    "aa7fe2f5fb47d863ca23e8b1d490632d63a2599f515268b6d6bd656158dad45e"
val parakeetModel = layout.projectDirectory.file(
    "src/main/assets/models/ggml-parakeet-tdt-0.6b-v3-q4_0.bin",
).asFile

tasks.register("prepareParakeetModel") {
    group = "speech"
    description = "Downloads and verifies Parakeet TDT 0.6B v3 Q4_0 for packaging in the APK."

    doLast {
        parakeetModel.parentFile.mkdirs()

        listOf(
            "ggml-base-q5_1.bin",
            "ggml-tiny-q5_1.bin",
            "ggml-large-v3-turbo-q5_0.bin",
        ).forEach { previous ->
            layout.projectDirectory.file("src/main/assets/models/$previous").asFile
                .takeIf { it.exists() }
                ?.delete()
        }

        val currentHash = if (parakeetModel.exists()) sha256(parakeetModel) else null
        if (currentHash != parakeetModelSha256) {
            if (parakeetModel.exists()) parakeetModel.delete()
            logger.lifecycle("Downloading Parakeet TDT 0.6B v3 Q4_0 (about 356 MB)…")

            val connection = URI.create(parakeetModelUrl).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 600_000
                setRequestProperty("User-Agent", "transcribe-offline-android-build")
            }
            connection.getInputStream().buffered().use { input ->
                parakeetModel.outputStream().buffered(1024 * 1024).use { output ->
                    input.copyTo(output, 1024 * 1024)
                }
            }
        }

        val verifiedHash = sha256(parakeetModel)
        check(verifiedHash == parakeetModelSha256) {
            "Parakeet model checksum mismatch. Expected $parakeetModelSha256, got $verifiedHash"
        }
        logger.lifecycle("Parakeet TDT 0.6B v3 Q4_0 verified and ready for APK packaging.")
    }
}

tasks.named("preBuild").configure {
    dependsOn("prepareParakeetModel")
}

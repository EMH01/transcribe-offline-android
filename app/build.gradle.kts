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
        versionCode = 6
        versionName = "0.3.2"
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
    implementation(project(":core-whisper"))

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

val whisperModelUrl =
    "https://huggingface.co/ggerganov/whisper.cpp/resolve/f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-base-q5_1.bin"
val whisperModelSha256 =
    "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
val whisperModel = layout.projectDirectory.file(
    "src/main/assets/models/ggml-base-q5_1.bin",
).asFile

tasks.register("prepareWhisperModel") {
    group = "whisper"
    description = "Downloads and verifies Whisper Base Q5_1 for packaging in the APK."

    doLast {
        whisperModel.parentFile.mkdirs()

        val currentHash = if (whisperModel.exists()) sha256(whisperModel) else null
        if (currentHash != whisperModelSha256) {
            if (whisperModel.exists()) whisperModel.delete()
            logger.lifecycle("Downloading Whisper Base Q5_1 (about 60 MB)…")

            val connection = URI.create(whisperModelUrl).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", "transcribe-offline-android-build")
            }
            connection.getInputStream().buffered().use { input ->
                whisperModel.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
        }

        val verifiedHash = sha256(whisperModel)
        check(verifiedHash == whisperModelSha256) {
            "Whisper model checksum mismatch. Expected $whisperModelSha256, got $verifiedHash"
        }
        logger.lifecycle("Whisper Base Q5_1 verified and ready for APK packaging.")
    }
}

tasks.named("preBuild").configure {
    dependsOn("prepareWhisperModel")
}

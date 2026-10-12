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
        versionCode = 12
        versionName = "0.4.0-alpha3b"
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
        // Whisper models are already quantized binary data. Keeping them
        // uncompressed avoids huge in-memory ZIP compression during packaging
        // and lets Android stream the asset directly.
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
    implementation(project(":core-whisper"))
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

val whisperModelUrl =
    "https://huggingface.co/ggerganov/whisper.cpp/resolve/98aa99a0a9db05ae2342309f5096248665f7cba3/ggml-large-v3-turbo-q5_0.bin"
val whisperModelSha256 =
    "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2"
val whisperModel = layout.projectDirectory.file(
    "src/main/assets/models/ggml-large-v3-turbo-q5_0.bin",
).asFile

tasks.register("prepareWhisperModel") {
    group = "whisper"
    description = "Downloads and verifies Whisper Large V3 Turbo Q5_0 for packaging in the APK."

    doLast {
        whisperModel.parentFile.mkdirs()

        // Avoid accidentally packaging a previous experiment model when
        // building repeatedly in the same local checkout.
        listOf(
            "ggml-base-q5_1.bin",
            "ggml-tiny-q5_1.bin",
        ).forEach { previous ->
            layout.projectDirectory.file("src/main/assets/models/$previous").asFile
                .takeIf { it.exists() }
                ?.delete()
        }

        val currentHash = if (whisperModel.exists()) sha256(whisperModel) else null
        if (currentHash != whisperModelSha256) {
            if (whisperModel.exists()) whisperModel.delete()
            logger.lifecycle("Downloading Whisper Large V3 Turbo Q5_0 (about 547 MiB)…")

            val connection = URI.create(whisperModelUrl).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 600_000
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
        logger.lifecycle("Whisper Large V3 Turbo Q5_0 verified and ready for APK packaging.")
    }
}

tasks.named("preBuild").configure {
    dependsOn("prepareWhisperModel")
}

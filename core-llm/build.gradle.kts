import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.emh01.transcribe.llm"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        ndk {
            // The local LLM is intentionally 64-bit only. The rest of the app
            // keeps its existing 32-bit Whisper support.
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    androidResources {
        noCompress += "gguf"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core-text"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val gemmaModelUrl =
    "https://huggingface.co/ggml-org/gemma-3-1b-it-qat-GGUF/resolve/main/gemma-3-1b-it-qat-Q4_0.gguf"
val gemmaModelSha256 =
    "ef60e4e91a738c99ae9976b050657dfe68a4007a0ccca121b55ec0c413dccd58"
val gemmaModel = layout.projectDirectory.file(
    "src/main/assets/models/gemma-3-1b-it-qat-Q4_0.gguf",
).asFile

tasks.register("prepareLocalTextModel") {
    group = "llm"
    description = "Downloads and verifies Gemma 3 1B IT QAT Q4_0 for offline text improvement."

    doLast {
        gemmaModel.parentFile.mkdirs()

        val currentHash = if (gemmaModel.exists()) sha256(gemmaModel) else null
        if (currentHash != gemmaModelSha256) {
            if (gemmaModel.exists()) gemmaModel.delete()
            logger.lifecycle("Downloading Gemma 3 1B IT QAT Q4_0 (about 720 MB)…")

            val connection = URI.create(gemmaModelUrl).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 300_000
                setRequestProperty("User-Agent", "transcribe-offline-android-build")
            }
            connection.getInputStream().buffered().use { input ->
                gemmaModel.outputStream().buffered(1024 * 1024).use { output ->
                    input.copyTo(output, 1024 * 1024)
                }
            }
        }

        val verifiedHash = sha256(gemmaModel)
        check(verifiedHash == gemmaModelSha256) {
            "Gemma model checksum mismatch. Expected $gemmaModelSha256, got $verifiedHash"
        }
        logger.lifecycle("Gemma 3 1B IT QAT Q4_0 verified and ready for APK packaging.")
    }
}

tasks.named("preBuild").configure {
    dependsOn("prepareLocalTextModel")
}

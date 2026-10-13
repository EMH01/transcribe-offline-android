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

val qwenModelUrl =
    "https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_0.gguf"
val qwenModelSha256 =
    "da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4"
val qwenModel = layout.projectDirectory.file(
    "src/main/assets/models/Qwen3-0.6B-Q4_0.gguf",
).asFile

tasks.register("prepareLocalTextModel") {
    group = "llm"
    description = "Downloads and verifies Qwen3 0.6B Q4_0 for offline text assistance."

    doLast {
        qwenModel.parentFile.mkdirs()

        val currentHash = if (qwenModel.exists()) sha256(qwenModel) else null
        if (currentHash != qwenModelSha256) {
            if (qwenModel.exists()) qwenModel.delete()
            logger.lifecycle("Downloading Qwen3 0.6B Q4_0 (about 429 MB)…")

            val connection = URI.create(qwenModelUrl).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 300_000
                setRequestProperty("User-Agent", "transcribe-offline-android-build")
            }
            connection.getInputStream().buffered().use { input ->
                qwenModel.outputStream().buffered(1024 * 1024).use { output ->
                    input.copyTo(output, 1024 * 1024)
                }
            }
        }

        val verifiedHash = sha256(qwenModel)
        check(verifiedHash == qwenModelSha256) {
            "Qwen model checksum mismatch. Expected $qwenModelSha256, got $verifiedHash"
        }
        logger.lifecycle("Qwen3 0.6B Q4_0 verified and ready for APK packaging.")
    }
}

tasks.named("preBuild").configure {
    dependsOn("prepareLocalTextModel")
}

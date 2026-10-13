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

val lfmModelUrl =
    "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
val lfmModelSha256 =
    "b1b3de114215d9507409a662a501a631095a479a419584e8a2ded6304b19b4f5"
val lfmModel = layout.projectDirectory.file(
    "src/main/assets/models/LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
).asFile

tasks.register("prepareLocalTextModel") {
    group = "llm"
    description = "Downloads and verifies LFM2.5 1.2B Instruct Q4_K_M for offline text assistance."

    doLast {
        lfmModel.parentFile.mkdirs()

        val currentHash = if (lfmModel.exists()) sha256(lfmModel) else null
        if (currentHash != lfmModelSha256) {
            if (lfmModel.exists()) lfmModel.delete()
            logger.lifecycle("Downloading LFM2.5 1.2B Instruct Q4_K_M (about 731 MB)…")

            val connection = URI.create(lfmModelUrl).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 300_000
                setRequestProperty("User-Agent", "transcribe-offline-android-build")
            }
            connection.getInputStream().buffered().use { input ->
                lfmModel.outputStream().buffered(1024 * 1024).use { output ->
                    input.copyTo(output, 1024 * 1024)
                }
            }
        }

        val verifiedHash = sha256(lfmModel)
        check(verifiedHash == lfmModelSha256) {
            "LFM model checksum mismatch. Expected $lfmModelSha256, got $verifiedHash"
        }
        logger.lifecycle("LFM2.5 1.2B Instruct Q4_K_M verified and ready for APK packaging.")
    }
}

tasks.named("preBuild").configure {
    dependsOn("prepareLocalTextModel")
}

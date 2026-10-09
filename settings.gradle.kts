pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TranscribeOffline"
include(":app")
include(":core-audio")
include(":core-speech")
include(":core-whisper")

include(":core-text")
include(":core-llm")

# JNI methods are resolved by their Java/Kotlin class and method names.
# Preserve native bridges when R8 minification is enabled for release.
-keep class com.emh01.transcribe.parakeet.ParakeetNative { *; }
-keep class com.emh01.transcribe.llm.LlamaNative { *; }

#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <algorithm>
#include <string>

#include "whisper.h"

namespace {
constexpr const char *TAG = "TranscribeWhisper";

void throwRuntime(JNIEnv *env, const char *message) {
    jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message);
    }
}

size_t assetRead(void *context, void *output, size_t readSize) {
    auto *asset = static_cast<AAsset *>(context);
    const int read = AAsset_read(asset, output, readSize);
    return read > 0 ? static_cast<size_t>(read) : 0;
}

bool assetEof(void *context) {
    auto *asset = static_cast<AAsset *>(context);
    return AAsset_getRemainingLength64(asset) <= 0;
}

void assetClose(void *context) {
    auto *asset = static_cast<AAsset *>(context);
    if (asset != nullptr) {
        AAsset_close(asset);
    }
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_emh01_transcribe_whisper_WhisperNative_initContextFromAsset(
        JNIEnv *env,
        jobject,
        jobject assetManager,
        jstring assetPath) {
    const char *path = env->GetStringUTFChars(assetPath, nullptr);
    AAssetManager *manager = AAssetManager_fromJava(env, assetManager);
    AAsset *asset = manager == nullptr
                    ? nullptr
                    : AAssetManager_open(manager, path, AASSET_MODE_STREAMING);
    env->ReleaseStringUTFChars(assetPath, path);

    if (asset == nullptr) {
        throwRuntime(env, "Whisper model asset could not be opened");
        return 0;
    }

    whisper_model_loader loader{};
    loader.context = asset;
    loader.read = assetRead;
    loader.eof = assetEof;
    loader.close = assetClose;

    whisper_context_params params = whisper_context_default_params();
    whisper_context *context = whisper_init_with_params(&loader, params);
    if (context == nullptr) {
        throwRuntime(env, "Whisper context could not be initialized");
        return 0;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "Whisper model loaded from assets");
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_emh01_transcribe_whisper_WhisperNative_transcribe(
        JNIEnv *env,
        jobject,
        jlong contextPtr,
        jfloatArray audioData,
        jint threadCount,
        jstring language) {
    auto *context = reinterpret_cast<whisper_context *>(contextPtr);
    if (context == nullptr) {
        throwRuntime(env, "Whisper context is null");
        return nullptr;
    }

    const char *languageChars = env->GetStringUTFChars(language, nullptr);
    jfloat *samples = env->GetFloatArrayElements(audioData, nullptr);
    const jsize sampleCount = env->GetArrayLength(audioData);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = languageChars;
    params.n_threads = std::max(1, static_cast<int>(threadCount));
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    whisper_reset_timings(context);
    const int result = whisper_full(context, params, samples, sampleCount);

    env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(language, languageChars);

    if (result != 0) {
        throwRuntime(env, "Whisper transcription failed");
        return nullptr;
    }

    std::string text;
    const int segmentCount = whisper_full_n_segments(context);
    for (int i = 0; i < segmentCount; ++i) {
        const char *segment = whisper_full_get_segment_text(context, i);
        if (segment != nullptr) {
            text += segment;
        }
    }

    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_emh01_transcribe_whisper_WhisperNative_freeContext(
        JNIEnv *,
        jobject,
        jlong contextPtr) {
    auto *context = reinterpret_cast<whisper_context *>(contextPtr);
    if (context != nullptr) {
        whisper_free(context);
    }
}

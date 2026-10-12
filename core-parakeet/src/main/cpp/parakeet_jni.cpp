#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <algorithm>
#include <string>

#include "parakeet.h"

namespace {

constexpr const char * TAG = "TranscribeParakeet";

void throwRuntime(JNIEnv * env, const char * message) {
    jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message);
    }
}

size_t assetRead(void * context, void * output, size_t readSize) {
    auto * asset = static_cast<AAsset *>(context);
    const int read = AAsset_read(asset, output, readSize);
    return read > 0 ? static_cast<size_t>(read) : 0;
}

bool assetEof(void * context) {
    auto * asset = static_cast<AAsset *>(context);
    return AAsset_getRemainingLength64(asset) <= 0;
}

void assetClose(void * context) {
    auto * asset = static_cast<AAsset *>(context);
    if (asset != nullptr) {
        AAsset_close(asset);
    }
}

} // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_com_emh01_transcribe_parakeet_ParakeetNative_initContextFromAsset(
    JNIEnv * env,
    jobject,
    jobject assetManager,
    jstring assetPath
) {
    const char * path = env->GetStringUTFChars(assetPath, nullptr);
    if (path == nullptr) return 0L;

    AAssetManager * manager = AAssetManager_fromJava(env, assetManager);
    AAsset * asset = manager == nullptr
        ? nullptr
        : AAssetManager_open(manager, path, AASSET_MODE_STREAMING);
    env->ReleaseStringUTFChars(assetPath, path);

    if (asset == nullptr) {
        throwRuntime(env, "Parakeet model asset could not be opened");
        return 0L;
    }

    parakeet_model_loader loader{};
    loader.context = asset;
    loader.read = assetRead;
    loader.eof = assetEof;
    loader.close = assetClose;

    parakeet_context_params params = parakeet_context_default_params();
    params.use_gpu = false;
    params.gpu_device = 0;

    parakeet_context * context = parakeet_init_with_params(&loader, params);
    if (context == nullptr) {
        throwRuntime(env, "Parakeet context could not be initialized");
        return 0L;
    }

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "Parakeet model loaded from assets"
    );
    return reinterpret_cast<jlong>(context);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_emh01_transcribe_parakeet_ParakeetNative_transcribe(
    JNIEnv * env,
    jobject,
    jlong contextPtr,
    jfloatArray audioData,
    jint threadCount
) {
    auto * context = reinterpret_cast<parakeet_context *>(contextPtr);
    if (context == nullptr) {
        throwRuntime(env, "Parakeet context is null");
        return nullptr;
    }

    jfloat * samples = env->GetFloatArrayElements(audioData, nullptr);
    const jsize sampleCount = env->GetArrayLength(audioData);

    parakeet_full_params params =
        parakeet_full_default_params(PARAKEET_SAMPLING_GREEDY);
    params.n_threads = std::max(1, static_cast<int>(threadCount));
    params.offset_ms = 0;
    params.duration_ms = 0;
    params.no_context = true;
    params.audio_ctx = 0;

    parakeet_reset_timings(context);
    const int result = parakeet_full(
        context,
        params,
        samples,
        static_cast<int>(sampleCount)
    );

    env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);

    if (result != 0) {
        throwRuntime(env, "Parakeet transcription failed");
        return nullptr;
    }

    std::string text;
    const int segmentCount = parakeet_full_n_segments(context);
    for (int i = 0; i < segmentCount; ++i) {
        const char * segment = parakeet_full_get_segment_text(context, i);
        if (segment != nullptr) {
            if (!text.empty() && text.back() != ' ') {
                text += ' ';
            }
            text += segment;
        }
    }

    return env->NewStringUTF(text.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_emh01_transcribe_parakeet_ParakeetNative_freeContext(
    JNIEnv *,
    jobject,
    jlong contextPtr
) {
    auto * context = reinterpret_cast<parakeet_context *>(contextPtr);
    if (context != nullptr) {
        parakeet_free(context);
    }
}

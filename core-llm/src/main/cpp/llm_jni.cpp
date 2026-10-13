#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {

constexpr const char * TAG = "TranscribeLLM";
std::once_flag backend_once;

void log_error(const std::string & message) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", message.c_str());
}

void batch_set_tokens(
    llama_batch_ext * batch,
    const llama_token * tokens,
    int32_t n_tokens,
    llama_pos pos_0
) {
    llama_batch_ext_clear(batch);
    for (int32_t i = 0; i < n_tokens; ++i) {
        const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i]);
        const llama_pos pos = pos_0 + i;
        llama_batch_ext_set_pos(batch, idx, &pos);
    }
    llama_batch_ext_set_output_logits(batch, n_tokens - 1, true);
}

std::string token_piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(256);
    int n = llama_token_to_piece(
        vocab,
        token,
        buffer.data(),
        static_cast<int32_t>(buffer.size()),
        0,
        true
    );

    if (n < 0) {
        buffer.resize(static_cast<size_t>(-n));
        n = llama_token_to_piece(
            vocab,
            token,
            buffer.data(),
            static_cast<int32_t>(buffer.size()),
            0,
            true
        );
    }

    if (n <= 0) {
        return {};
    }
    return std::string(buffer.data(), static_cast<size_t>(n));
}

std::string apply_model_chat_template(
    llama_model * model,
    const std::vector<std::string> & roles,
    const std::vector<std::string> & contents
) {
    if (roles.empty() || roles.size() != contents.size()) {
        return {};
    }

    const char * tmpl = llama_model_chat_template(model, nullptr);
    if (tmpl == nullptr) {
        // Conservative fallback for a GGUF without an embedded chat template.
        return contents.back();
    }

    std::vector<llama_chat_message> messages;
    messages.reserve(roles.size());
    size_t source_size = 0;
    for (size_t i = 0; i < roles.size(); ++i) {
        messages.push_back({roles[i].c_str(), contents[i].c_str()});
        source_size += roles[i].size() + contents[i].size();
    }
    std::vector<char> formatted(
        std::max<size_t>(2048, source_size * 2 + 512)
    );

    int32_t length = llama_chat_apply_template(
        tmpl,
        messages.data(),
        messages.size(),
        true,
        formatted.data(),
        static_cast<int32_t>(formatted.size())
    );

    if (length > static_cast<int32_t>(formatted.size())) {
        formatted.resize(static_cast<size_t>(length));
        length = llama_chat_apply_template(
            tmpl,
            messages.data(),
            messages.size(),
            true,
            formatted.data(),
            static_cast<int32_t>(formatted.size())
        );
    }

    if (length < 0) {
        log_error("Unable to apply model chat template");
        return {};
    }

    return std::string(formatted.data(), static_cast<size_t>(length));
}

jstring generate_from_formatted_prompt(
    JNIEnv * env,
    llama_model * model,
    const std::string & prompt_text,
    jint max_tokens,
    jint thread_count
) {
    const llama_vocab * vocab = llama_model_get_vocab(model);
    const int32_t n_prompt = -llama_tokenize(
        vocab, prompt_text.c_str(), prompt_text.size(), nullptr, 0, true, true
    );
    if (n_prompt <= 0) {
        log_error("Unable to tokenize prompt");
        return env->NewStringUTF("");
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt));
    if (llama_tokenize(
            vocab, prompt_text.c_str(), prompt_text.size(), prompt_tokens.data(),
            static_cast<int32_t>(prompt_tokens.size()), true, true
        ) < 0) {
        log_error("Prompt tokenization failed");
        return env->NewStringUTF("");
    }

    const int32_t safe_max_tokens = std::clamp(static_cast<int32_t>(max_tokens), 32, 768);
    const int32_t safe_threads = std::clamp(static_cast<int32_t>(thread_count), 1, 6);
    const int32_t required_context = n_prompt + safe_max_tokens + 8;
    if (required_context > 4096) {
        log_error("Prompt exceeds local LLM context limit");
        return env->NewStringUTF("");
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(std::max(512, required_context));
    ctx_params.n_batch = static_cast<uint32_t>(std::max(64, n_prompt));
    ctx_params.n_threads = safe_threads;
    ctx_params.n_threads_batch = safe_threads;
    ctx_params.no_perf = true;
    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        log_error("Unable to create llama context");
        return env->NewStringUTF("");
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    // Greedy decoding made short conversational turns collapse into repeated
    // stock answers. Keep the sampler conservative but allow alternatives.
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(50));
    llama_sampler_chain_add(
        sampler,
        llama_sampler_init_penalties(
            llama_vocab_n_tokens(vocab),
            64,
            1.05f,
            0.0f,
            0.0f
        )
    );
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.1f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    llama_batch_ext * batch = llama_batch_ext_init(ctx);
    if (batch == nullptr) {
        llama_sampler_free(sampler);
        llama_free(ctx);
        log_error("Unable to create llama batch");
        return env->NewStringUTF("");
    }

    batch_set_tokens(batch, prompt_tokens.data(), n_prompt, 0);
    std::string output;
    int32_t n_tokens = n_prompt;
    int32_t n_pos = 0;
    for (int32_t generated = 0; generated < safe_max_tokens; ++generated) {
        if (llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
            log_error("llama_process failed");
            break;
        }
        n_pos += n_tokens;
        const llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        output += token_piece(vocab, token);
        batch_set_tokens(batch, &token, 1, n_pos);
        n_tokens = 1;
    }
    llama_batch_ext_free(batch);
    llama_sampler_free(sampler);
    llama_free(ctx);
    return env->NewStringUTF(output.c_str());
}

} // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_com_emh01_transcribe_llm_LlamaNative_loadModel(
    JNIEnv * env,
    jobject,
    jstring path
) {
    if (path == nullptr) return 0L;

    const char * model_path = env->GetStringUTFChars(path, nullptr);
    if (model_path == nullptr) return 0L;

    std::call_once(backend_once, []() {
        llama_backend_init();
    });

    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;
    params.use_extra_bufts = true;

    llama_model * model = llama_model_load_from_file(model_path, params);
    env->ReleaseStringUTFChars(path, model_path);

    if (model == nullptr) {
        log_error("Unable to load local LLM model");
        return 0L;
    }

    return reinterpret_cast<jlong>(model);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_emh01_transcribe_llm_LlamaNative_generate(
    JNIEnv * env,
    jobject,
    jlong model_ptr,
    jstring user_prompt,
    jint max_tokens,
    jint thread_count
) {
    auto * model = reinterpret_cast<llama_model *>(model_ptr);
    if (model == nullptr || user_prompt == nullptr) {
        return env->NewStringUTF("");
    }

    const char * prompt_chars = env->GetStringUTFChars(user_prompt, nullptr);
    if (prompt_chars == nullptr) {
        return env->NewStringUTF("");
    }

    const std::string raw_user_prompt(prompt_chars);
    env->ReleaseStringUTFChars(user_prompt, prompt_chars);

    const std::string prompt_text = apply_model_chat_template(
        model, {"user"}, {raw_user_prompt}
    );
    if (prompt_text.empty()) {
        return env->NewStringUTF("");
    }
    return generate_from_formatted_prompt(
        env, model, prompt_text, max_tokens, thread_count
    );
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_emh01_transcribe_llm_LlamaNative_generateConversation(
    JNIEnv * env,
    jobject,
    jlong model_ptr,
    jobjectArray roles_array,
    jobjectArray contents_array,
    jint max_tokens,
    jint thread_count
) {
    auto * model = reinterpret_cast<llama_model *>(model_ptr);
    if (model == nullptr || roles_array == nullptr || contents_array == nullptr) {
        return env->NewStringUTF("");
    }

    const jsize count = env->GetArrayLength(roles_array);
    if (count == 0 || count != env->GetArrayLength(contents_array)) {
        return env->NewStringUTF("");
    }

    std::vector<std::string> roles;
    std::vector<std::string> contents;
    roles.reserve(static_cast<size_t>(count));
    contents.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto * role = static_cast<jstring>(env->GetObjectArrayElement(roles_array, i));
        auto * content = static_cast<jstring>(env->GetObjectArrayElement(contents_array, i));
        if (role == nullptr || content == nullptr) return env->NewStringUTF("");
        const char * role_chars = env->GetStringUTFChars(role, nullptr);
        const char * content_chars = env->GetStringUTFChars(content, nullptr);
        if (role_chars == nullptr || content_chars == nullptr) return env->NewStringUTF("");
        roles.emplace_back(role_chars);
        contents.emplace_back(content_chars);
        env->ReleaseStringUTFChars(role, role_chars);
        env->ReleaseStringUTFChars(content, content_chars);
        env->DeleteLocalRef(role);
        env->DeleteLocalRef(content);
    }

    const std::string prompt_text = apply_model_chat_template(model, roles, contents);
    if (prompt_text.empty()) return env->NewStringUTF("");
    return generate_from_formatted_prompt(env, model, prompt_text, max_tokens, thread_count);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_emh01_transcribe_llm_LlamaNative_freeModel(
    JNIEnv *,
    jobject,
    jlong model_ptr
) {
    auto * model = reinterpret_cast<llama_model *>(model_ptr);
    if (model != nullptr) {
        llama_model_free(model);
    }
}

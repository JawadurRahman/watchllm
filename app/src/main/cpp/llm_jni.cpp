// JNI bridge between com.focussystems.watchllm.llm.LlamaEngine and llama.cpp.
//
// Stateless generation: every nativeGenerate() clears the KV cache, formats
// (system, user) with the model's chat template, decodes the prompt and streams
// tokens back through a Kotlin callback. Only one model/context exists at a time and
// the Kotlin side serialises all calls on a single thread (nativeCancel() excepted).

#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "WatchLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

llama_model   *g_model = nullptr;
llama_context *g_ctx = nullptr;
int            g_n_ctx = 0;
int            g_n_batch = 0;
std::atomic<bool> g_cancel{false};

void log_cb(ggml_log_level level, const char *text, void *) {
    int prio = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR
             : level == GGML_LOG_LEVEL_WARN  ? ANDROID_LOG_WARN
                                             : ANDROID_LOG_INFO;
    __android_log_write(prio, TAG, text);
}

bool abort_cb(void *) { return g_cancel.load(std::memory_order_relaxed); }

// Length of the longest prefix of s that does not end in a partial UTF-8 sequence.
size_t complete_utf8_prefix(const std::string &s) {
    size_t n = s.size();
    for (size_t back = 1; back <= 3 && back <= n; back++) {
        unsigned char c = (unsigned char) s[n - back];
        if ((c & 0xC0) == 0x80) continue;              // continuation byte, keep looking
        size_t need = (c & 0x80) == 0x00 ? 1 : (c & 0xE0) == 0xC0 ? 2
                    : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 1;
        return need > back ? n - back : n;
    }
    return n;
}

std::string token_to_piece(const llama_vocab *vocab, llama_token tok) {
    char buf[256];
    int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, false);
    if (n < 0) {
        std::string big(-n, '\0');
        n = llama_token_to_piece(vocab, tok, big.data(), (int) big.size(), 0, false);
        return n > 0 ? big.substr(0, n) : std::string();
    }
    return std::string(buf, n);
}

void free_all() {
    if (g_ctx)   { llama_free(g_ctx);         g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

jbyteArray to_bytes(JNIEnv *env, const std::string &s) {
    jbyteArray arr = env->NewByteArray((jsize) s.size());
    env->SetByteArrayRegion(arr, 0, (jsize) s.size(), (const jbyte *) s.data());
    return arr;
}

std::string from_jstring(JNIEnv *env, jstring js) {
    const char *c = env->GetStringUTFChars(js, nullptr);
    std::string s(c ? c : "");
    if (c) env->ReleaseStringUTFChars(js, c);
    return s;
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_focussystems_watchllm_llm_LlamaEngine_nativeLoad(
        JNIEnv *env, jobject, jstring jpath, jint n_ctx, jint n_threads) {
    free_all();
    llama_log_set(log_cb, nullptr);
    llama_backend_init();

    const std::string path = from_jstring(env, jpath);
    llama_model_params mp = llama_model_default_params();
    g_model = llama_model_load_from_file(path.c_str(), mp);
    if (!g_model) { LOGE("failed to load model %s", path.c_str()); return JNI_FALSE; }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = n_ctx;
    cp.n_batch = 256;
    cp.n_ubatch = 256;
    cp.n_threads = n_threads;
    cp.n_threads_batch = n_threads;
    cp.abort_callback = abort_cb;
    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) { LOGE("failed to create context"); free_all(); return JNI_FALSE; }

    g_n_ctx = (int) llama_n_ctx(g_ctx);
    g_n_batch = (int) llama_n_batch(g_ctx);
    LOGI("loaded: n_ctx=%d n_batch=%d threads=%d | %s", g_n_ctx, g_n_batch, n_threads,
         llama_print_system_info());
    return JNI_TRUE;
}

// Returns {promptTokens, generatedTokens, promptMs, generateMs}, or null on failure.
// callback: LlamaEngine.TokenCallback { boolean onToken(byte[] utf8) } - return false to stop.
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_focussystems_watchllm_llm_LlamaEngine_nativeGenerate(
        JNIEnv *env, jobject, jstring jsystem, jstring juser,
        jint max_tokens, jfloat temp, jobject callback) {
    if (!g_ctx || !g_model) { LOGE("generate: no model loaded"); return nullptr; }
    g_cancel = false;

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    const std::string system = from_jstring(env, jsystem);
    const std::string user = from_jstring(env, juser);

    // Format with the model's own chat template (chatml fallback).
    const char *tmpl = llama_model_chat_template(g_model, nullptr);
    std::vector<llama_chat_message> msgs;
    if (!system.empty()) msgs.push_back({"system", system.c_str()});
    msgs.push_back({"user", user.c_str()});
    std::string prompt(system.size() + user.size() + 512, '\0');
    int n = llama_chat_apply_template(tmpl ? tmpl : "chatml", msgs.data(), msgs.size(), true,
                                      prompt.data(), (int) prompt.size());
    if (n > (int) prompt.size()) {
        prompt.resize(n);
        n = llama_chat_apply_template(tmpl ? tmpl : "chatml", msgs.data(), msgs.size(), true,
                                      prompt.data(), (int) prompt.size());
    }
    if (n < 0) { LOGE("chat template failed"); return nullptr; }
    prompt.resize(n);

    int n_tok = -llama_tokenize(vocab, prompt.c_str(), (int) prompt.size(), nullptr, 0, true, true);
    std::vector<llama_token> toks(n_tok);
    if (llama_tokenize(vocab, prompt.c_str(), (int) prompt.size(), toks.data(), n_tok, true, true) < 0) {
        LOGE("tokenize failed");
        return nullptr;
    }
    if (n_tok + max_tokens > g_n_ctx) { LOGE("prompt too long: %d tokens", n_tok); return nullptr; }

    llama_memory_clear(llama_get_memory(g_ctx), true);

    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    jclass cb_cls = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb_cls, "onToken", "([B)Z");

    // Prompt (prefill), in n_batch chunks.
    const int64_t t0 = ggml_time_us();
    bool ok = true;
    for (int i = 0; i < n_tok && ok; i += g_n_batch) {
        const int cnt = std::min(g_n_batch, n_tok - i);
        if (llama_decode(g_ctx, llama_batch_get_one(toks.data() + i, cnt)) != 0) ok = false;
    }
    const int64_t t1 = ggml_time_us();

    int generated = 0;
    if (ok) {
        std::string pending;
        for (; generated < max_tokens && !g_cancel.load(); ) {
            llama_token tok = llama_sampler_sample(smpl, g_ctx, -1);
            if (llama_vocab_is_eog(vocab, tok)) break;
            generated++;

            pending += token_to_piece(vocab, tok);
            const size_t cut = complete_utf8_prefix(pending);
            if (cut > 0) {
                jbyteArray arr = to_bytes(env, pending.substr(0, cut));
                jboolean cont = env->CallBooleanMethod(callback, on_token, arr);
                env->DeleteLocalRef(arr);
                pending.erase(0, cut);
                if (env->ExceptionCheck() || !cont) break;
            }
            if (llama_decode(g_ctx, llama_batch_get_one(&tok, 1)) != 0) break;
        }
    }
    const int64_t t2 = ggml_time_us();
    llama_sampler_free(smpl);
    env->DeleteLocalRef(cb_cls);

    if (!ok && !g_cancel.load()) { LOGE("prompt decode failed"); return nullptr; }

    jdoubleArray out = env->NewDoubleArray(4);
    const jdouble vals[4] = {(jdouble) n_tok, (jdouble) generated,
                             (t1 - t0) / 1000.0, (t2 - t1) / 1000.0};
    env->SetDoubleArrayRegion(out, 0, 4, vals);
    return out;
}

// Thread-safe: may be called while nativeGenerate is running on another thread.
extern "C" JNIEXPORT void JNICALL
Java_com_focussystems_watchllm_llm_LlamaEngine_nativeCancel(JNIEnv *, jobject) {
    g_cancel = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_focussystems_watchllm_llm_LlamaEngine_nativeFree(JNIEnv *, jobject) {
    free_all();
    llama_backend_free();
}

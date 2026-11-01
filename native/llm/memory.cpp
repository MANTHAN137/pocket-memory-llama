#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#include "llama.h"
#include "chat.h"
#include "generation_guard.h"

namespace {
std::mutex engine_mutex;
std::atomic<bool> cancelled{false};
std::atomic<int64_t> deadline{0};
int generation_timeout_ms=45000;
float temperature=0.3f,repeat_penalty=1.08f;
llama_model * model = nullptr;
llama_context * context = nullptr;
llama_model * embedding_model = nullptr;
llama_context * embedding_context = nullptr;
std::string loaded_path, embedding_path;
bool initialized = false;
common_chat_templates_ptr chat_templates;

std::string string_utf8(JNIEnv * env, jstring value) {
    if (!value) return {};
    jclass cls = env->FindClass("java/lang/String");
    jmethodID method = env->GetMethodID(cls, "getBytes", "(Ljava/lang/String;)[B");
    jstring charset = env->NewStringUTF("UTF-8");
    auto bytes = (jbyteArray) env->CallObjectMethod(value, method, charset);
    jsize length = env->GetArrayLength(bytes);
    std::string out(length, '\0');
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(out.data()));
    env->DeleteLocalRef(bytes); env->DeleteLocalRef(charset); env->DeleteLocalRef(cls);
    return out;
}
jstring java_string(JNIEnv * env, const std::string & text) {
    auto bytes = env->NewByteArray(text.size());
    env->SetByteArrayRegion(bytes, 0, text.size(), reinterpret_cast<const jbyte *>(text.data()));
    jclass cls = env->FindClass("java/lang/String");
    jmethodID init = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
    jstring charset = env->NewStringUTF("UTF-8");
    auto out = (jstring) env->NewObject(cls, init, bytes, charset);
    env->DeleteLocalRef(bytes); env->DeleteLocalRef(charset); env->DeleteLocalRef(cls);
    return out;
}
void fail(JNIEnv * env, const std::exception & e) { env->ThrowNew(env->FindClass("java/io/IOException"), e.what()); }
void init() { if (!initialized) { llama_backend_init(); initialized = true; } }
bool abort_inference(void *) { return cancelled.load() || (deadline.load()>0 && pocket_guard::milliseconds()>=deadline.load()); }
bool load_progress(float, void *) { return !abort_inference(nullptr); }
void begin_stage(int timeout) {cancelled.store(false);deadline.store(pocket_guard::milliseconds()+timeout);}
void check_stage() {
    if(cancelled.load())throw std::runtime_error("CANCELLED: Task stopped.");
    if(deadline.load()>0 && pocket_guard::milliseconds()>=deadline.load())throw std::runtime_error("TIME_LIMIT: The model took too long. Try a shorter request.");
}
std::vector<llama_token> tokenize(llama_model * m, const std::string & text) {
    auto * vocab = llama_model_get_vocab(m);
    int count = llama_tokenize(vocab, text.data(), text.size(), nullptr, 0, true, true);
    if (count >= 0) throw std::runtime_error("Unable to tokenize input.");
    std::vector<llama_token> tokens(-count);
    count = llama_tokenize(vocab, text.data(), text.size(), tokens.data(), tokens.size(), true, true);
    if (count < 0) throw std::runtime_error("Unable to tokenize input.");
    tokens.resize(count); return tokens;
}
void unload() {
    chat_templates.reset();
    if (context) llama_free(context);
    if (model) llama_model_free(model);
    context = nullptr; model = nullptr; loaded_path.clear();
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocketmemory_app_NativeEngine_cancel(JNIEnv *, jclass) { cancelled.store(true); }

extern "C" JNIEXPORT void JNICALL
Java_com_pocketmemory_app_NativeEngine_configure(JNIEnv *,jclass,jint timeout,jfloat temp,jfloat repeat) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    generation_timeout_ms=std::clamp((int)timeout,100,90000);
    temperature=std::clamp((float)temp,0.0f,1.0f);repeat_penalty=std::clamp((float)repeat,1.0f,1.5f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocketmemory_app_NativeEngine_unload(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(engine_mutex); unload();
    if (embedding_context) llama_free(embedding_context);
    if (embedding_model) llama_model_free(embedding_model);
    embedding_context = nullptr; embedding_model = nullptr; embedding_path.clear();
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocketmemory_app_NativeEngine_load(JNIEnv * env, jclass, jstring path) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    try {
        init(); begin_stage(45000);
        std::string requested = string_utf8(env, path);
        if (model && loaded_path == requested) return;
        unload();
        auto mp = llama_model_default_params(); mp.n_gpu_layers = 0; mp.load_mode = LLAMA_LOAD_MODE_MMAP;
        mp.progress_callback=load_progress;
        model = llama_model_load_from_file(requested.c_str(), mp);
        check_stage();
        if (!model) throw std::runtime_error("This model could not be opened. Re-download it or choose another model.");
        auto cp = llama_context_default_params();
        cp.n_ctx = 4096; cp.n_batch = 256; cp.n_ubatch = 128;
        cp.n_threads = 4; cp.n_threads_batch = 4;
        cp.abort_callback = abort_inference;
        context = llama_init_from_model(model, cp);
        if (!context) { unload(); throw std::runtime_error("Not enough memory to load this model. Close other apps and retry."); }
        check_stage();
        loaded_path = requested;
        chat_templates = common_chat_templates_init(model, "");
    } catch (const std::exception & e) { unload();fail(env, e); }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pocketmemory_app_NativeEngine_generate(JNIEnv * env, jclass, jstring system, jstring user, jint maximum, jobject listener) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    llama_sampler * sampler = nullptr;
    try {
        if (!context) throw std::runtime_error("Choose an installed model first.");
        begin_stage(generation_timeout_ms);
        common_chat_templates_inputs input;
        common_chat_msg sys; sys.role = "system"; sys.content = string_utf8(env, system);
        common_chat_msg usr; usr.role = "user"; usr.content = string_utf8(env, user);
        input.messages = {sys, usr}; input.enable_thinking = false;
        input.add_generation_prompt = true;
        auto formatted = common_chat_templates_apply(chat_templates.get(), input);
        auto tokens = tokenize(model, formatted.prompt.str());
        int limit = std::clamp((int) maximum, 32, 768);
        if (tokens.size() + limit + 8 > llama_n_ctx(context))
            throw std::runtime_error("This text is too long for the phone's working context. Select a shorter passage.");
        llama_memory_clear(llama_get_memory(context), true);
        for (size_t i = 0; i < tokens.size(); i += 128) {
            check_stage();
            auto batch = llama_batch_get_one(tokens.data() + i, std::min<size_t>(128, tokens.size()-i));
            if (llama_decode(context, batch) != 0) {check_stage();throw std::runtime_error("Could not read the input. Try a shorter passage.");}
        }
        sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(llama_model_get_vocab(model)), 128, repeat_penalty, 0, 0));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(50));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(42));
        const auto * vocab = llama_model_get_vocab(model);
        std::string output;
        std::vector<int32_t> generated;bool complete=false;
        jclass listener_class = listener ? env->GetObjectClass(listener) : nullptr;
        jmethodID on_token = listener ? env->GetMethodID(listener_class, "onToken", "([B)V") : nullptr;
        for (int n = 0; n < limit && !cancelled.load(); n++) {
            check_stage();
            llama_token token = llama_sampler_sample(sampler, context, -1);
            if (llama_vocab_is_eog(vocab, token)) {complete=true;break;}
            generated.push_back(token);
            if(pocket_guard::repeating(generated))throw std::runtime_error("REPETITION: The model began repeating itself. Generation stopped.");
            char piece[4096];
            int size = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
            if (size < 0) throw std::runtime_error("Model emitted an unsupported token.");
            output.append(piece, size);
            if (listener && size > 0) {
                auto bytes = env->NewByteArray(size);
                env->SetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte *>(piece));
                env->CallVoidMethod(listener, on_token, bytes); env->DeleteLocalRef(bytes);
                if (env->ExceptionCheck()) break;
            }
            auto batch = llama_batch_get_one(&token, 1);
            if (llama_decode(context, batch) != 0) {
                check_stage();
                throw std::runtime_error("Generation stopped unexpectedly. Try again with a shorter question.");
            }
        }
        if (listener_class) env->DeleteLocalRef(listener_class);
        llama_sampler_free(sampler); sampler = nullptr;
        if (env->ExceptionCheck()) return nullptr;
        check_stage();
        if(!complete)throw std::runtime_error("TOKEN_LIMIT: The response exceeded its output limit. Try a shorter passage.");
        if (output.empty()) throw std::runtime_error("The model returned no answer. Please try again.");
        return java_string(env, output);
    } catch (const std::exception & e) { if (sampler) llama_sampler_free(sampler); fail(env, e); return nullptr; }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pocketmemory_app_NativeEngine_promptTokens(JNIEnv * env, jclass, jstring system, jstring user) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    try {
        if (!model || !chat_templates) throw std::runtime_error("Load a model before measuring its input.");
        common_chat_templates_inputs input;
        common_chat_msg sys; sys.role="system"; sys.content=string_utf8(env,system);
        common_chat_msg usr; usr.role="user"; usr.content=string_utf8(env,user);
        input.messages={sys,usr}; input.enable_thinking=false; input.add_generation_prompt=true;
        return tokenize(model,common_chat_templates_apply(chat_templates.get(),input).prompt.str()).size();
    } catch(const std::exception & e) {fail(env,e);return -1;}
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_pocketmemory_app_NativeEngine_embed(JNIEnv * env, jclass, jstring path, jstring value) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    try {
        init();begin_stage(20000);
        auto requested = string_utf8(env, path);
        if (!embedding_model || requested != embedding_path) {
            if (embedding_context) llama_free(embedding_context);
            if (embedding_model) llama_model_free(embedding_model);
            embedding_context = nullptr; embedding_model = nullptr; embedding_path.clear();
            auto mp = llama_model_default_params(); mp.n_gpu_layers = 0;
            mp.progress_callback=load_progress;
            embedding_model = llama_model_load_from_file(requested.c_str(), mp);
            if (!embedding_model) throw std::runtime_error("The meaning-search model could not be loaded.");
            auto cp = llama_context_default_params(); cp.n_ctx = 1024; cp.n_batch = 1024; cp.n_ubatch = 1024;
            cp.n_threads = 4; cp.n_threads_batch = 4; cp.embeddings = true;
            cp.pooling_type = LLAMA_POOLING_TYPE_MEAN;
            cp.abort_callback=abort_inference;
            embedding_context = llama_init_from_model(embedding_model, cp);
            if (!embedding_context) throw std::runtime_error("Not enough memory for meaning search.");
            embedding_path = requested;
        }
        auto tokens = tokenize(embedding_model, string_utf8(env, value));
        if (tokens.size() > 1000) throw std::runtime_error("Search passage exceeds the embedding limit.");
        llama_memory_clear(llama_get_memory(embedding_context), true);
        auto batch = llama_batch_get_one(tokens.data(), tokens.size());
        if (llama_decode(embedding_context, batch) != 0) {check_stage();throw std::runtime_error("Could not index this passage.");}
        check_stage();
        float * values = llama_get_embeddings_seq(embedding_context, 0);
        if (!values) throw std::runtime_error("The embedding model produced no search vector.");
        int dims = llama_model_n_embd(embedding_model);
        std::vector<float> normalized(values, values+dims);
        double norm = 0; for (auto v : normalized) norm += v*v;
        norm = std::sqrt(norm); if (norm > 0) for (auto & v : normalized) v /= norm;
        auto result = env->NewFloatArray(dims);
        env->SetFloatArrayRegion(result, 0, dims, normalized.data()); return result;
    } catch (const std::exception & e) { fail(env, e); return nullptr; }
}

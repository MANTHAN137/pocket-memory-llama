#include <jni.h>
#include <algorithm>
#include "llama.h"
#include "generation_guard.h"
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
std::atomic<long long> epoch{0};
std::mutex engine_mutex;
std::once_flag backend;
using Clock=std::chrono::steady_clock;
struct Job { long long ticket; Clock::time_point end; };
bool stopped(void * opaque){auto * job=static_cast<Job *>(opaque);return epoch.load()!=job->ticket||Clock::now()>=job->end;}
bool progress(float,void * opaque){return !stopped(opaque);}
void check(Job & job){if(epoch.load()!=job.ticket)throw std::runtime_error("Reply stopped.");if(Clock::now()>=job.end)throw std::runtime_error("The reply reached its time limit. Try a shorter question.");}
std::string utf8(JNIEnv * env,jstring value){jclass type=env->FindClass("java/lang/String");jmethodID method=env->GetMethodID(type,"getBytes","(Ljava/lang/String;)[B");jstring encoding=env->NewStringUTF("UTF-8");auto bytes=static_cast<jbyteArray>(env->CallObjectMethod(value,method,encoding));jsize count=env->GetArrayLength(bytes);std::string out(count,'\0');env->GetByteArrayRegion(bytes,0,count,reinterpret_cast<jbyte *>(out.data()));env->DeleteLocalRef(bytes);env->DeleteLocalRef(encoding);env->DeleteLocalRef(type);return out;}

}
extern "C" JNIEXPORT jlong JNICALL Java_com_pocketmemory_app_LocalLlama_newJob(JNIEnv *,jclass){return epoch.fetch_add(1)+1;}
extern "C" JNIEXPORT void JNICALL Java_com_pocketmemory_app_LocalLlama_cancel(JNIEnv *,jclass,jlong ticket){long long expected=ticket;epoch.compare_exchange_strong(expected,ticket+1);}
static jstring generate(JNIEnv * env,jstring path,jstring system,jstring input,const std::string & rules,int max_tokens,jint timeout,jlong ticket){
    try{
        std::lock_guard<std::mutex> lock(engine_mutex);
        Job job{ticket,Clock::now()+std::chrono::milliseconds(std::max(100,std::min(140000,static_cast<int>(timeout))))};check(job);std::call_once(backend,[]{llama_backend_init();});
        auto mp=llama_model_default_params();mp.n_gpu_layers=0;mp.load_mode=LLAMA_LOAD_MODE_MMAP;mp.progress_callback=progress;mp.progress_callback_user_data=&job;
        std::unique_ptr<llama_model,decltype(&llama_model_free)> model(llama_model_load_from_file(utf8(env,path).c_str(),mp),llama_model_free);check(job);if(!model)throw std::runtime_error("Llama could not be loaded. Check the model in Models and free some memory.");
        auto cp=llama_context_default_params();cp.n_ctx=4096;cp.n_batch=256;cp.n_ubatch=128;cp.n_threads=4;cp.n_threads_batch=4;cp.abort_callback=stopped;cp.abort_callback_data=&job;
        std::unique_ptr<llama_context,decltype(&llama_free)> context(llama_init_from_model(model.get(),cp),llama_free);check(job);if(!context)throw std::runtime_error("There is not enough memory for Llama. Close other apps and try again.");
        std::string sys=utf8(env,system),user=utf8(env,input);llama_chat_message messages[]={{"system",sys.c_str()},{"user",user.c_str()}};
        // This app pins Llama 3.2; use llama.cpp's public Llama 3 chat format.
        int needed=llama_chat_apply_template("llama3",messages,2,true,nullptr,0);if(needed<=0)throw std::runtime_error("The Llama prompt could not be formatted.");
        std::string prompt(needed,'\0');llama_chat_apply_template("llama3",messages,2,true,prompt.data(),needed);
        const llama_vocab * vocab=llama_model_get_vocab(model.get());int total=llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true);if(total>=0)throw std::runtime_error("The source context could not be read.");
        std::vector<llama_token> tokens(-total);total=llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),tokens.size(),true,true);if(total<0||total+max_tokens+16>4096)throw std::runtime_error("The web excerpts are too long. Try a shorter question.");tokens.resize(total);
        for(size_t offset=0;offset<tokens.size();offset+=128){check(job);auto batch=llama_batch_get_one(tokens.data()+offset,std::min<size_t>(128,tokens.size()-offset));if(llama_decode(context.get(),batch)!=0){check(job);throw std::runtime_error("Llama could not read the source context.");}}
        std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()),llama_sampler_free);
        auto * constrained=llama_sampler_init_grammar(vocab,rules.c_str(),"root");if(!constrained)throw std::runtime_error("The answer format could not be prepared.");
        llama_sampler_chain_add(sampler.get(),constrained);llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_k(40));llama_sampler_chain_add(sampler.get(),llama_sampler_init_temp(0.1f));llama_sampler_chain_add(sampler.get(),llama_sampler_init_dist(42));
        std::string output;bool complete=false;
        bool quoted=false,escaped=false;int depth=0;std::vector<int32_t> generated;
        for(int step=0;step<max_tokens;step++){
            check(job);llama_token token=llama_sampler_sample(sampler.get(),context.get(),-1);if(llama_vocab_is_eog(vocab,token))break;
            generated.push_back(token);if(pocket_guard::repeating(generated))throw std::runtime_error("The model began repeating. Reply stopped.");
            char text[4096];int length=llama_token_to_piece(vocab,token,text,sizeof(text),0,false);if(length<0)throw std::runtime_error("Llama emitted an unsupported token.");output.append(text,length);
            for(int i=0;i<length;i++){char ch=text[i];if(quoted){if(escaped)escaped=false;else if(ch=='\\')escaped=true;else if(ch=='"')quoted=false;}else if(ch=='"')quoted=true;else if(ch=='{')depth++;else if(ch=='}'&&--depth==0)complete=true;}if(complete)break;
            auto batch=llama_batch_get_one(&token,1);if(llama_decode(context.get(),batch)!=0){check(job);throw std::runtime_error("Llama stopped unexpectedly.");}
        }
        check(job);if(!complete)throw std::runtime_error("Llama did not finish a checked answer. Try a shorter question.");jbyteArray bytes=env->NewByteArray(output.size());env->SetByteArrayRegion(bytes,0,output.size(),reinterpret_cast<const jbyte *>(output.data()));jclass string_type=env->FindClass("java/lang/String");jmethodID constructor=env->GetMethodID(string_type,"<init>","([BLjava/lang/String;)V");jstring encoding=env->NewStringUTF("UTF-8");jstring answer=static_cast<jstring>(env->NewObject(string_type,constructor,bytes,encoding));env->DeleteLocalRef(bytes);env->DeleteLocalRef(encoding);env->DeleteLocalRef(string_type);return answer;
    }catch(const std::exception & error){env->ThrowNew(env->FindClass("java/io/IOException"),error.what());return nullptr;}
}

extern "C" JNIEXPORT jstring JNICALL Java_com_pocketmemory_app_LocalLlama_answer(JNIEnv * env,jclass,jstring path,jstring system,jstring input,jint sources,jint timeout,jlong ticket){
    if(sources<1||sources>3){env->ThrowNew(env->FindClass("java/io/IOException"),"The web source set is outside its limit.");return nullptr;}
    std::string rules=R"GBNF(root ::= "{" ws "\"answer\"" ws ":" ws string ws "," ws "\"sources\"" ws ":" ws "[" ws (id (ws "," ws id)? (ws "," ws id)?)? ws "]" ws "}"
string ::= "\"" char* "\""
char ::= [^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F])
ws ::= [ \t\n\r]*
id ::= )GBNF";
    for(int i=1;i<=sources;i++){if(i>1)rules+=" | ";rules+='"'+std::to_string(i)+'"';}rules+='\n';
    return generate(env,path,system,input,rules,192,timeout,ticket);
}

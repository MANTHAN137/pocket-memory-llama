#include <jni.h>
#include <atomic>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>
#include "whisper.h"

namespace {
std::atomic<bool> stopped{false};
std::atomic<int64_t> deadline{0};
std::mutex voice_mutex;
int64_t now(){return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();}
bool abort_voice(void *){return stopped.load()||now()>=deadline.load();}
void fail(JNIEnv * env,const char * message){env->ThrowNew(env->FindClass("java/io/IOException"),message);}
}
extern "C" JNIEXPORT void JNICALL
Java_com_pocketmemory_app_OfflineVoice_cancel(JNIEnv *,jclass){stopped.store(true);}
extern "C" JNIEXPORT jstring JNICALL
Java_com_pocketmemory_app_OfflineVoice_transcribe(JNIEnv * env,jclass,jstring path,jfloatArray samples,jstring language) {
    std::lock_guard<std::mutex> lock(voice_mutex);stopped.store(false);deadline.store(now()+45000);
    int length=env->GetArrayLength(samples);
    if(length<4000||length>16000*30){fail(env,"Record between a quarter second and 30 seconds of speech.");return nullptr;}
    const char * p=env->GetStringUTFChars(path,nullptr);
    auto cp=whisper_context_default_params();cp.use_gpu=false;
    whisper_context * ctx=whisper_init_from_file_with_params(p,cp);
    env->ReleaseStringUTFChars(path,p);
    if(!ctx){fail(env,"The offline voice model could not be loaded.");return nullptr;}
    std::vector<float> data(length);env->GetFloatArrayRegion(samples,0,length,data.data());
    const char * lang=env->GetStringUTFChars(language,nullptr);std::string chosen(lang);env->ReleaseStringUTFChars(language,lang);
    auto params=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads=4;params.language=chosen.c_str();params.translate=false;
    params.print_progress=false;params.print_realtime=false;params.print_timestamps=false;
    params.no_context=true;params.single_segment=false;params.suppress_blank=true;
    params.abort_callback=abort_voice;params.max_tokens=256;
    int result=abort_voice(nullptr)?1:whisper_full(ctx,params,data.data(),length);
    if(result!=0||abort_voice(nullptr)){whisper_free(ctx);fail(env,stopped.load()?"Voice task stopped.":now()>=deadline.load()?"Voice reached its time limit. Try a shorter recording.":"Could not transcribe this recording.");return nullptr;}
    std::string text;for(int i=0;i<whisper_full_n_segments(ctx);i++)text+=whisper_full_get_segment_text(ctx,i);
    whisper_free(ctx);
    auto bytes=env->NewByteArray(text.size());env->SetByteArrayRegion(bytes,0,text.size(),reinterpret_cast<const jbyte *>(text.data()));
    jclass cls=env->FindClass("java/lang/String");jstring charset=env->NewStringUTF("UTF-8");
    auto output=(jstring)env->NewObject(cls,env->GetMethodID(cls,"<init>","([BLjava/lang/String;)V"),bytes,charset);
    env->DeleteLocalRef(bytes);env->DeleteLocalRef(cls);env->DeleteLocalRef(charset);return output;
}

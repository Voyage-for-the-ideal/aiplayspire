#include "Snapshot.h"
#include <jni.h>
#include <limits>

// Called only by the Java serial executor. A handle never leaves its owning client.
extern "C" JNIEXPORT jlong JNICALL Java_battleaimod_networking_NativeEngine_open(JNIEnv *env,jclass) {
    try {return reinterpret_cast<jlong>(new bridge::Session());}
    catch (...) {env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"),"battle session");return 0;}
}
extern "C" JNIEXPORT void JNICALL Java_battleaimod_networking_NativeEngine_close(JNIEnv *,jclass,jlong handle) {
    delete reinterpret_cast<bridge::Session*>(handle);
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_battleaimod_networking_NativeEngine_call(JNIEnv *env,jclass,jlong handle,jbyteArray input) {
    try {
        if (!handle || !input) throw std::invalid_argument("missing session or input");
        std::string data(env->GetArrayLength(input),'\0');
        env->GetByteArrayRegion(input,0,static_cast<jsize>(data.size()),reinterpret_cast<jbyte*>(&data[0]));
        if(env->ExceptionCheck()) return nullptr;
        auto response=reinterpret_cast<bridge::Session*>(handle)->search(bridge::Json::parse(data));
        auto bytes=response.dump();
        if(bytes.size()>std::numeric_limits<jsize>::max()) throw std::length_error("response too large");
        auto out=env->NewByteArray(static_cast<jsize>(bytes.size()));
        if(out) env->SetByteArrayRegion(out,0,static_cast<jsize>(bytes.size()),reinterpret_cast<const jbyte*>(bytes.data()));
        return out;
    } catch(const std::exception &e) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());return nullptr;
    } catch(...) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"native search failed");return nullptr;
    }
}

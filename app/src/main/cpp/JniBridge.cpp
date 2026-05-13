#include <jni.h>
#include "AudioEngine.h"

static AudioEngine *engine(jlong handle) {
    return reinterpret_cast<AudioEngine *>(handle);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_clickcue_audio_AudioEngineJni_create(JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(new AudioEngine());
}

JNIEXPORT void JNICALL
Java_com_clickcue_audio_AudioEngineJni_destroy(JNIEnv *, jobject, jlong handle) {
    delete engine(handle);
}

// Returns the oboe::Result int; OK == 0.
JNIEXPORT jint JNICALL
Java_com_clickcue_audio_AudioEngineJni_start(JNIEnv *, jobject, jlong handle) {
    return static_cast<jint>(engine(handle)->start());
}

JNIEXPORT jint JNICALL
Java_com_clickcue_audio_AudioEngineJni_stop(JNIEnv *, jobject, jlong handle) {
    return static_cast<jint>(engine(handle)->stop());
}

JNIEXPORT void JNICALL
Java_com_clickcue_audio_AudioEngineJni_play(JNIEnv *, jobject, jlong handle) {
    engine(handle)->play();
}

JNIEXPORT void JNICALL
Java_com_clickcue_audio_AudioEngineJni_pause(JNIEnv *, jobject, jlong handle) {
    engine(handle)->pause();
}

JNIEXPORT void JNICALL
Java_com_clickcue_audio_AudioEngineJni_setBpm(JNIEnv *, jobject, jlong handle, jdouble bpm) {
    engine(handle)->setBpm(static_cast<double>(bpm));
}

} // extern "C"

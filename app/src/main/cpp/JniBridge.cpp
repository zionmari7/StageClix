#include <jni.h>
#include "AudioEngine.h"

// JFUNC(return_type, method_name) expands to the full JNI signature for
// com.stageclix.audio.AudioEngineJni so the class path is only written once.
#define JFUNC(ret, name) \
    JNIEXPORT ret JNICALL Java_com_stageclix_audio_AudioEngineJni_##name

static AudioEngine* engine(jlong handle) {
    return reinterpret_cast<AudioEngine*>(handle);
}

extern "C" {

JFUNC(jlong, nativeCreate)(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new AudioEngine());
}

JFUNC(void, nativeDestroy)(JNIEnv*, jobject, jlong handle) {
    delete engine(handle);
}

JFUNC(jboolean, nativeStart)(JNIEnv*, jobject, jlong handle, jint deviceId) {
    return engine(handle)->start(static_cast<int>(deviceId)) ? JNI_TRUE : JNI_FALSE;
}

JFUNC(void, nativeStop)(JNIEnv*, jobject, jlong handle) {
    engine(handle)->stop();
}

JFUNC(void, nativePlay)(JNIEnv*, jobject, jlong handle) {
    engine(handle)->play();
}

JFUNC(void, nativePause)(JNIEnv*, jobject, jlong handle) {
    engine(handle)->pause();
}

JFUNC(void, nativeRewind)(JNIEnv*, jobject, jlong handle) {
    engine(handle)->rewind();
}

JFUNC(void, nativeSetBpm)(JNIEnv*, jobject, jlong handle, jdouble bpm) {
    engine(handle)->setBpm(static_cast<double>(bpm));
}

JFUNC(void, nativeSetTimeSignature)(JNIEnv*, jobject, jlong handle,
    jint numerator, jint denominator) {
    engine(handle)->setTimeSignature(
        static_cast<int>(numerator), static_cast<int>(denominator));
}

JFUNC(void, nativeSetClickEnabled)(JNIEnv*, jobject, jlong handle, jboolean enabled) {
    engine(handle)->setClickEnabled(enabled == JNI_TRUE);
}

JFUNC(void, nativeSetClickSample)(JNIEnv* env, jobject, jlong handle, jfloatArray pcm) {
    jsize   len  = env->GetArrayLength(pcm);
    jfloat* data = env->GetFloatArrayElements(pcm, nullptr);
    engine(handle)->setClickSample(data, static_cast<int>(len));
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
}

JFUNC(void, nativeSetAccentSample)(JNIEnv* env, jobject, jlong handle, jfloatArray pcm) {
    jsize   len  = env->GetArrayLength(pcm);
    jfloat* data = env->GetFloatArrayElements(pcm, nullptr);
    engine(handle)->setAccentSample(data, static_cast<int>(len));
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
}

JFUNC(void, nativeSetAccentEnabled)(JNIEnv*, jobject, jlong handle, jboolean enabled) {
    engine(handle)->setAccentEnabled(enabled == JNI_TRUE);
}

JFUNC(void, nativeSetAccentVolume)(JNIEnv*, jobject, jlong handle, jfloat volume) {
    engine(handle)->setAccentVolume(static_cast<float>(volume));
}

JFUNC(void, nativeSetBeatEnabled)(JNIEnv*, jobject, jlong handle, jboolean enabled) {
    engine(handle)->setBeatEnabled(enabled == JNI_TRUE);
}

JFUNC(void, nativeSetBeatVolume)(JNIEnv*, jobject, jlong handle, jfloat volume) {
    engine(handle)->setBeatVolume(static_cast<float>(volume));
}

JFUNC(void, nativeSetQuarterEnabled)(JNIEnv*, jobject, jlong handle, jboolean enabled) {
    engine(handle)->setQuarterEnabled(enabled == JNI_TRUE);
}

JFUNC(void, nativeSetQuarterVolume)(JNIEnv*, jobject, jlong handle, jfloat volume) {
    engine(handle)->setQuarterVolume(static_cast<float>(volume));
}

JFUNC(void, nativeSetEighthEnabled)(JNIEnv*, jobject, jlong handle, jboolean enabled) {
    engine(handle)->setEighthEnabled(enabled == JNI_TRUE);
}

JFUNC(void, nativeSetEighthVolume)(JNIEnv*, jobject, jlong handle, jfloat volume) {
    engine(handle)->setEighthVolume(static_cast<float>(volume));
}

JFUNC(void, nativeSetSixteenthEnabled)(JNIEnv*, jobject, jlong handle, jboolean enabled) {
    engine(handle)->setSixteenthEnabled(enabled == JNI_TRUE);
}

JFUNC(void, nativeSetSixteenthVolume)(JNIEnv*, jobject, jlong handle, jfloat volume) {
    engine(handle)->setSixteenthVolume(static_cast<float>(volume));
}

JFUNC(void, nativeSetMasterVolume)(JNIEnv*, jobject, jlong handle, jfloat volume) {
    engine(handle)->setMasterVolume(static_cast<float>(volume));
}

JFUNC(jdouble, nativeGetPositionBeats)(JNIEnv*, jobject, jlong handle) {
    return static_cast<jdouble>(engine(handle)->getPositionBeats());
}

JFUNC(void, nativeLoadVoiceCue)(JNIEnv* env, jobject, jlong handle, jint cueId, jfloatArray pcm) {
    jsize   len  = env->GetArrayLength(pcm);
    jfloat* data = env->GetFloatArrayElements(pcm, nullptr);
    engine(handle)->loadVoiceCue(static_cast<int>(cueId), data, static_cast<int>(len));
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
}

JFUNC(void, nativeSetCueTimeline)(JNIEnv* env, jobject, jlong handle,
    jintArray bars, jintArray cueIds) {
    jsize count     = env->GetArrayLength(bars);
    jint* barsData  = env->GetIntArrayElements(bars,   nullptr);
    jint* cuesData  = env->GetIntArrayElements(cueIds, nullptr);
    engine(handle)->setCueTimeline(barsData, cuesData, static_cast<int>(count));
    env->ReleaseIntArrayElements(bars,   barsData, JNI_ABORT);
    env->ReleaseIntArrayElements(cueIds, cuesData, JNI_ABORT);
}

JFUNC(void, nativeSetVoiceCueVolume)(JNIEnv*, jobject, jlong handle, jfloat volume) {
    engine(handle)->setVoiceCueVolume(static_cast<float>(volume));
}

JFUNC(void, nativeSetVoiceCueMuted)(JNIEnv*, jobject, jlong handle, jboolean muted) {
    engine(handle)->setVoiceCueMuted(muted == JNI_TRUE);
}

} // extern "C"

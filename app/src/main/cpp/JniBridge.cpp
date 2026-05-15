#include <jni.h>
#include <android/log.h>
#include "AudioEngine.h"

#define TAG  "JniBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)

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

JFUNC(void, nativeSetBeatPattern)(JNIEnv* env, jobject, jlong handle,
    jintArray beatIndices, jintArray rows, jintArray subIndices,
    jint timeSigNumerator, jdouble bpm) {
    jsize count = env->GetArrayLength(beatIndices);
    jint* beatPtr = env->GetIntArrayElements(beatIndices, nullptr);
    jint* rowPtr = env->GetIntArrayElements(rows, nullptr);
    jint* subPtr = env->GetIntArrayElements(subIndices, nullptr);

    std::vector<BeatEvent> events;
    events.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        events.push_back({
            static_cast<int>(beatPtr[i]),
            static_cast<int>(rowPtr[i]),
            static_cast<int>(subPtr[i]),
        });
    }

    engine(handle)->setBeatPattern(
        events.data(),
        static_cast<int>(count),
        static_cast<int>(timeSigNumerator),
        static_cast<double>(bpm));

    env->ReleaseIntArrayElements(beatIndices, beatPtr, JNI_ABORT);
    env->ReleaseIntArrayElements(rows, rowPtr, JNI_ABORT);
    env->ReleaseIntArrayElements(subIndices, subPtr, JNI_ABORT);
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

JFUNC(void, nativeAuditNote)(JNIEnv*, jobject, jlong handle, jint row, jint /*clickTypeIndex*/) {
    engine(handle)->auditNote(static_cast<int>(row));
}

JFUNC(void, nativeLoadVoiceCue)(JNIEnv* env, jobject, jlong handle, jint cueId, jfloatArray pcm) {
    jsize   len  = env->GetArrayLength(pcm);
    jfloat* data = env->GetFloatArrayElements(pcm, nullptr);
    engine(handle)->loadVoiceCue(static_cast<int>(cueId), data, static_cast<int>(len));
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
}

JFUNC(void, nativeSetSections)(JNIEnv* env, jobject, jlong handle,
    jintArray sectionBars, jintArray voiceCueIds) {
    jsize count   = env->GetArrayLength(sectionBars);
    jint* barsPtr = env->GetIntArrayElements(sectionBars, nullptr);
    jint* idsPtr  = env->GetIntArrayElements(voiceCueIds, nullptr);
    LOGI("setSections: %d sections", (int)count);
    for (int i = 0; i < count; i++) {
        LOGI("  section[%d]: bar=%d cueId=%d", i, barsPtr[i], idsPtr[i]);
    }
    engine(handle)->setSections(barsPtr, idsPtr, static_cast<int>(count));
    env->ReleaseIntArrayElements(sectionBars, barsPtr, JNI_ABORT);
    env->ReleaseIntArrayElements(voiceCueIds, idsPtr,  JNI_ABORT);
}

JFUNC(void, nativeSetVoiceCueVolume)(JNIEnv*, jobject, jlong handle, jfloat volume) {
    engine(handle)->setVoiceCueVolume(static_cast<float>(volume));
}

JFUNC(void, nativeSetVoiceCueMuted)(JNIEnv*, jobject, jlong handle, jboolean muted) {
    engine(handle)->setVoiceCueMuted(muted == JNI_TRUE);
}

JFUNC(void, nativeSetCueData)(JNIEnv* env, jobject, jlong handle,
    jfloatArray pcm, jint frameCount, jint channelCount) {
    jsize   len  = env->GetArrayLength(pcm);
    jfloat* data = env->GetFloatArrayElements(pcm, nullptr);
    LOGI("setCueData: frameCount=%d channelCount=%d samples=%d",
         (int)frameCount, (int)channelCount, (int)len);
    engine(handle)->setCueData(data, static_cast<int32_t>(frameCount),
                                     static_cast<int32_t>(channelCount));
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
}

JFUNC(void, nativeSetCueVolume)(JNIEnv*, jobject, jlong handle, jfloat volume) {
    engine(handle)->setCueVolume(static_cast<float>(volume));
}

JFUNC(void, nativeSetCueMuted)(JNIEnv*, jobject, jlong handle, jboolean muted) {
    engine(handle)->setCueMuted(muted == JNI_TRUE);
}

JFUNC(void, nativePlayCue)(JNIEnv*, jobject, jlong handle) {
    engine(handle)->playCue();
}

JFUNC(void, nativePauseCue)(JNIEnv*, jobject, jlong handle) {
    engine(handle)->pauseCue();
}

JFUNC(void, nativeRewindCue)(JNIEnv*, jobject, jlong handle) {
    engine(handle)->rewindCue();
}

JFUNC(jint, nativeAddBackingTrack)(JNIEnv*, jobject, jlong handle) {
    return static_cast<jint>(engine(handle)->addBackingTrack());
}

JFUNC(void, nativeRemoveBackingTrack)(JNIEnv*, jobject, jlong handle, jint index) {
    engine(handle)->removeBackingTrack(static_cast<int>(index));
}

JFUNC(void, nativeSetBackingTrackData)(JNIEnv* env, jobject, jlong handle,
    jint index, jfloatArray pcm, jint channels) {
    jsize   len    = env->GetArrayLength(pcm);
    jfloat* data   = env->GetFloatArrayElements(pcm, nullptr);
    int32_t frames = static_cast<int32_t>(len) / static_cast<int32_t>(channels);
    LOGI("setBackingTrackData: index=%d frames=%d channels=%d", (int)index, frames, (int)channels);
    engine(handle)->setBackingTrackData(static_cast<int>(index), data, frames,
                                        static_cast<int32_t>(channels));
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
}

JFUNC(void, nativeSetBackingTrackVolume)(JNIEnv*, jobject, jlong handle,
    jint index, jfloat volume) {
    engine(handle)->setBackingTrackVolume(static_cast<int>(index), static_cast<float>(volume));
}

JFUNC(void, nativeSetBackingTrackMuted)(JNIEnv*, jobject, jlong handle,
    jint index, jboolean muted) {
    engine(handle)->setBackingTrackMuted(static_cast<int>(index), muted == JNI_TRUE);
}

} // extern "C"

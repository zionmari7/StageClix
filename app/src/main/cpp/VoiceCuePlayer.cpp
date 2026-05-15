#include "VoiceCuePlayer.h"
#include <android/log.h>

#define TAG  "VoiceCuePlayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

void VoiceCuePlayer::loadCue(int cueId, const float* pcm, int frameCount) {
    std::lock_guard<std::mutex> lock(mCueMutex);
    auto& buf = mCueBuffers[cueId];
    buf.assign(pcm, pcm + frameCount);
}

void VoiceCuePlayer::trigger(int cueId) {
    if (mCueBuffers.find(cueId) == mCueBuffers.end()) {
        LOGE("VoiceCue: cueId %d not in buffers!", cueId);
        return;
    }
    mActiveCueId.store(cueId, std::memory_order_relaxed);
    mReadPosition.store(0,    std::memory_order_relaxed);
    mPlaying.store(true,      std::memory_order_relaxed);
    LOGI("VoiceCue triggered: id=%d frames=%d",
         cueId, (int)mCueBuffers[cueId].size());
}

void VoiceCuePlayer::reset() {
    mActiveCueId.store(-1,   std::memory_order_relaxed);
    mReadPosition.store(0,   std::memory_order_relaxed);
    mPlaying.store(false,    std::memory_order_relaxed);
}

void VoiceCuePlayer::setVolume(float volume) {
    mVolume.store(volume, std::memory_order_relaxed);
}

void VoiceCuePlayer::setMuted(bool muted) {
    mMuted.store(muted, std::memory_order_relaxed);
}

void VoiceCuePlayer::render(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!mPlaying.load(std::memory_order_relaxed) || mMuted.load(std::memory_order_relaxed)) return;

    const int cueId = mActiveCueId.load(std::memory_order_relaxed);
    if (cueId < 0) return;

    auto it = mCueBuffers.find(cueId);
    if (it == mCueBuffers.end()) return;

    const auto& samples = it->second;
    int         readPos = mReadPosition.load(std::memory_order_relaxed);
    const float vol     = mVolume.load(std::memory_order_relaxed);

    for (int32_t f = 0; f < numFrames; f++) {
        if (readPos >= static_cast<int>(samples.size())) {
            mPlaying.store(false,  std::memory_order_relaxed);
            mActiveCueId.store(-1, std::memory_order_relaxed);
            break;
        }
        const float s = samples[readPos++] * vol;
        for (int32_t ch = 0; ch < channelCount; ch++) {
            buffer[f * channelCount + ch] += s;
        }
    }
    mReadPosition.store(readPos, std::memory_order_relaxed);
}

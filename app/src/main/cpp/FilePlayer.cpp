#include "FilePlayer.h"
#include <android/log.h>

#define TAG  "FilePlayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

void FilePlayer::setData(const float* pcm, int32_t frameCount, int32_t channelCount) {
    if (!pcm || frameCount <= 0 || channelCount <= 0) {
        LOGE("setData: invalid args frameCount=%d channelCount=%d", frameCount, channelCount);
        return;
    }
    mHasData.store(false, std::memory_order_relaxed);
    mPlaying.store(false, std::memory_order_relaxed);
    mReadPosition.store(0, std::memory_order_relaxed);
    mSrcChannels.store(channelCount, std::memory_order_relaxed);
    mPcm.assign(pcm, pcm + static_cast<size_t>(frameCount) * channelCount);
    mHasData.store(true, std::memory_order_release);
    LOGI("setData: %d frames %d ch (%d samples total)",
         frameCount, channelCount, (int)mPcm.size());
}

void FilePlayer::setVolume(float volume) { mVolume.store(volume, std::memory_order_relaxed); }
void FilePlayer::setMuted(bool muted)    { mMuted.store(muted,   std::memory_order_relaxed); }
void FilePlayer::play()                  { mPlaying.store(true,  std::memory_order_relaxed); }
void FilePlayer::pause()                 { mPlaying.store(false, std::memory_order_relaxed); }

void FilePlayer::rewind() {
    mPlaying.store(false, std::memory_order_relaxed);
    mReadPosition.store(0, std::memory_order_relaxed);
}

void FilePlayer::render(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!mPlaying.load(std::memory_order_relaxed)) return;
    if (!mHasData.load(std::memory_order_acquire)) return;
    if (mMuted.load(std::memory_order_relaxed))    return;

    const float   vol          = mVolume.load(std::memory_order_relaxed);
    const int32_t srcCh        = mSrcChannels.load(std::memory_order_relaxed);
    const int32_t totalSamples = static_cast<int32_t>(mPcm.size());
    int32_t       readPos      = mReadPosition.load(std::memory_order_relaxed);

    for (int32_t f = 0; f < numFrames; f++) {
        int32_t srcFrame = readPos * srcCh;
        if (srcFrame >= totalSamples) {
            mPlaying.store(false, std::memory_order_relaxed);
            break;
        }

        if (srcCh == 1) {
            const float s = mPcm[srcFrame] * vol;
            for (int32_t ch = 0; ch < channelCount; ch++)
                buffer[f * channelCount + ch] += s;
        } else {
            // Interleaved stereo source
            const float l = mPcm[srcFrame + 0] * vol;
            const float r = (srcFrame + 1 < totalSamples) ? mPcm[srcFrame + 1] * vol : l;
            if (channelCount >= 2) {
                buffer[f * channelCount + 0] += l;
                buffer[f * channelCount + 1] += r;
            } else {
                buffer[f] += (l + r) * 0.5f;
            }
        }
        readPos++;
    }
    mReadPosition.store(readPos, std::memory_order_relaxed);
}

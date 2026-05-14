#include "VoiceCuePlayer.h"

void VoiceCuePlayer::loadCue(int cueId, const float* pcm, int frameCount) {
    std::lock_guard<std::mutex> lock(mCueMutex);
    auto& buf = mCueBuffers[cueId];
    buf.assign(pcm, pcm + frameCount);
}

void VoiceCuePlayer::trigger(int cueId) {
    mActiveCueId.store(cueId, std::memory_order_relaxed);
    mReadPosition.store(0, std::memory_order_relaxed);
    mPlaying.store(true, std::memory_order_relaxed);
}

void VoiceCuePlayer::setVolume(float volume) {
    mVolume.store(volume, std::memory_order_relaxed);
}

void VoiceCuePlayer::setMuted(bool muted) {
    mMuted.store(muted, std::memory_order_relaxed);
}

void VoiceCuePlayer::render(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!mPlaying.load(std::memory_order_relaxed)) return;
    if (mMuted.load(std::memory_order_relaxed)) return;

    const float volume = mVolume.load(std::memory_order_relaxed);
    const int   cueId  = mActiveCueId.load(std::memory_order_relaxed);

    // RT-safe: skip this callback if loadCue is mid-write
    if (!mCueMutex.try_lock()) return;

    auto it = mCueBuffers.find(cueId);
    if (it == mCueBuffers.end()) {
        mCueMutex.unlock();
        mPlaying.store(false, std::memory_order_relaxed);
        return;
    }

    const auto& buf    = it->second;
    int         pos    = mReadPosition.load(std::memory_order_relaxed);
    const int   bufEnd = static_cast<int>(buf.size());

    for (int32_t i = 0; i < numFrames && pos < bufEnd; ++i) {
        const float s = buf[pos++] * volume;
        for (int32_t ch = 0; ch < channelCount; ++ch) {
            buffer[i * channelCount + ch] += s;
        }
    }

    mCueMutex.unlock();

    mReadPosition.store(pos, std::memory_order_relaxed);
    if (pos >= bufEnd) {
        mPlaying.store(false, std::memory_order_relaxed);
    }
}

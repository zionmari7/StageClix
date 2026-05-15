#include "BackingMixer.h"

int BackingMixer::addTrack() {
    for (int i = 0; i < MAX_BACKING_TRACKS; ++i) {
        if (!mTracks[i].hasData.load(std::memory_order_relaxed))
            return i;
    }
    return -1;
}

void BackingMixer::removeTrack(int index) {
    if (index < 0 || index >= MAX_BACKING_TRACKS) return;
    BackingTrack& t = mTracks[index];
    t.hasData.store(false, std::memory_order_release);
    {
        std::lock_guard<std::mutex> lock(mTrackMutex);
        t.pcm.clear();
    }
    t.readPosition.store(0, std::memory_order_relaxed);
    t.playing.store(false, std::memory_order_relaxed);
}

void BackingMixer::setTrackData(int index, const float* pcm, int32_t frames, int32_t channels) {
    if (index < 0 || index >= MAX_BACKING_TRACKS || !pcm || frames <= 0) return;
    BackingTrack& t = mTracks[index];
    t.hasData.store(false, std::memory_order_release); // gate render while we write
    {
        std::lock_guard<std::mutex> lock(mTrackMutex);
        t.pcm.assign(pcm, pcm + static_cast<size_t>(frames * channels));
    }
    t.srcChannels.store(channels, std::memory_order_relaxed);
    t.readPosition.store(0, std::memory_order_relaxed);
    t.hasData.store(true, std::memory_order_release);
}

void BackingMixer::setTrackVolume(int index, float volume) {
    if (index < 0 || index >= MAX_BACKING_TRACKS) return;
    mTracks[index].volume.store(volume, std::memory_order_relaxed);
}

void BackingMixer::setTrackMuted(int index, bool muted) {
    if (index < 0 || index >= MAX_BACKING_TRACKS) return;
    mTracks[index].muted.store(muted, std::memory_order_relaxed);
}

void BackingMixer::play() {
    for (auto& t : mTracks) {
        if (t.hasData.load(std::memory_order_relaxed))
            t.playing.store(true, std::memory_order_relaxed);
    }
    mPlaying.store(true, std::memory_order_release);
}

void BackingMixer::pause() {
    mPlaying.store(false, std::memory_order_release);
}

void BackingMixer::rewind() {
    mPlaying.store(false, std::memory_order_release);
    for (auto& t : mTracks) {
        t.readPosition.store(0, std::memory_order_relaxed);
        t.playing.store(false, std::memory_order_relaxed);
    }
}

void BackingMixer::render(float* buffer, int32_t numFrames, int32_t channelCount) {
    if (!mPlaying.load(std::memory_order_acquire)) return;

    for (auto& t : mTracks) {
        if (!t.hasData.load(std::memory_order_acquire)) continue;
        if (!t.playing.load(std::memory_order_relaxed)) continue;
        if (t.muted.load(std::memory_order_relaxed)) continue;

        const float   vol        = t.volume.load(std::memory_order_relaxed);
        const int32_t srcCh      = t.srcChannels.load(std::memory_order_relaxed);
        int32_t       readPos    = t.readPosition.load(std::memory_order_relaxed);
        const int32_t totalFrames = static_cast<int32_t>(t.pcm.size()) / srcCh;

        bool ended = false;
        for (int32_t f = 0; f < numFrames; ++f) {
            if (readPos >= totalFrames) {
                ended = true;
                break;
            }
            int32_t srcIdx = readPos * srcCh;

            if (srcCh == 1) {
                float s = t.pcm[srcIdx] * vol;
                for (int32_t c = 0; c < channelCount; ++c)
                    buffer[f * channelCount + c] += s;
            } else {
                float l = t.pcm[srcIdx]     * vol;
                float r = t.pcm[srcIdx + 1] * vol;
                if (channelCount >= 2) {
                    buffer[f * channelCount + 0] += l;
                    buffer[f * channelCount + 1] += r;
                    for (int32_t c = 2; c < channelCount; ++c)
                        buffer[f * channelCount + c] += (l + r) * 0.5f;
                } else {
                    buffer[f] += (l + r) * 0.5f;
                }
            }
            ++readPos;
        }

        t.readPosition.store(readPos, std::memory_order_relaxed);
        if (ended)
            t.playing.store(false, std::memory_order_relaxed);
    }
}

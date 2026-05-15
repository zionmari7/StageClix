#pragma once

#include <array>
#include <atomic>
#include <mutex>
#include <vector>
#include <cstdint>

static constexpr int MAX_BACKING_TRACKS = 8;

struct BackingTrack {
    std::vector<float>   pcm;
    std::atomic<int32_t> readPosition{0};
    std::atomic<float>   volume{1.0f};
    std::atomic<bool>    muted{false};
    std::atomic<bool>    hasData{false};
    std::atomic<bool>    playing{false};
    std::atomic<int32_t> srcChannels{1};
};

class BackingMixer {
public:
    BackingMixer() = default;

    // Track management — called from Kotlin thread
    int  addTrack();
    void removeTrack(int index);
    void setTrackData(int index, const float* pcm, int32_t frames, int32_t channels);
    void setTrackVolume(int index, float volume);
    void setTrackMuted(int index, bool muted);

    // Transport — called from Kotlin thread
    void play();
    void pause();
    void rewind();

    // RT-safe render — called from audio callback; mixes all active tracks additively
    void render(float* buffer, int32_t numFrames, int32_t channelCount);

private:
    std::array<BackingTrack, MAX_BACKING_TRACKS> mTracks;
    std::atomic<bool> mPlaying{false};
    std::mutex        mTrackMutex;
};

#pragma once

#include <atomic>
#include <cstdint>
#include <mutex>
#include <unordered_map>
#include <vector>

class VoiceCuePlayer {
public:
    void loadCue(int cueId, const float* pcm, int frameCount);
    void trigger(int cueId);
    void reset();
    void setVolume(float volume);
    void setMuted(bool muted);
    void render(float* buffer, int32_t numFrames, int32_t channelCount);

private:
    std::unordered_map<int, std::vector<float>> mCueBuffers;
    std::mutex         mCueMutex;
    std::atomic<int>   mActiveCueId{-1};
    std::atomic<int>   mReadPosition{0};
    std::atomic<float> mVolume{1.0f};
    std::atomic<bool>  mMuted{false};
    std::atomic<bool>  mPlaying{false};
};

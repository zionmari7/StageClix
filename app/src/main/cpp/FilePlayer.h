#pragma once

#include <atomic>
#include <cstdint>
#include <vector>

class FilePlayer {
public:
    void setData(const float* pcm, int32_t frameCount, int32_t channelCount);
    void setVolume(float volume);
    void setMuted(bool muted);
    void play();
    void pause();
    void rewind();
    void render(float* buffer, int32_t numFrames, int32_t channelCount);

private:
    std::vector<float>   mPcm;
    std::atomic<int32_t> mSrcChannels{1};
    std::atomic<int32_t> mReadPosition{0};
    std::atomic<float>   mVolume{1.0f};
    std::atomic<bool>    mMuted{false};
    std::atomic<bool>    mPlaying{false};
    std::atomic<bool>    mHasData{false};
};

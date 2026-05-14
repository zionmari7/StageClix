#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <cstring>

struct SampleBuffer {
    float* data;
    int    length;
    SampleBuffer(const float* pcm, int len) : length(len) {
        data = new float[len];
        std::memcpy(data, pcm, len * sizeof(float));
    }
    ~SampleBuffer() { delete[] data; }
    SampleBuffer(const SampleBuffer&) = delete;
    SampleBuffer& operator=(const SampleBuffer&) = delete;
};

class AudioEngine : public oboe::AudioStreamCallback {
public:
    ~AudioEngine();

    bool   start(int deviceId);
    void   stop();
    void   play();
    void   pause();
    void   rewind();

    void   setBpm(double bpm);
    void   setTimeSignature(int numerator, int denominator);

    void   setClickEnabled(bool enabled);
    void   setClickSample(const float* pcm, int length);
    void   setAccentSample(const float* pcm, int length);
    void   setAccentEnabled(bool enabled);
    void   setAccentVolume(float volume);

    void   setQuarterEnabled(bool enabled);
    void   setQuarterVolume(float volume);
    void   setEighthEnabled(bool enabled);
    void   setEighthVolume(float volume);
    void   setSixteenthEnabled(bool enabled);
    void   setSixteenthVolume(float volume);

    void   setMasterVolume(float volume);

    // Legacy: forward to quarter tier so existing callers keep working.
    void   setBeatEnabled(bool enabled);
    void   setBeatVolume(float volume);

    double getPositionBeats();

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    void swapPendingBuffers();

    std::shared_ptr<oboe::AudioStream> mStream;

    std::atomic<double> mBpm{120.0};
    std::atomic<bool>   mIsPlaying{false};
    std::atomic<bool>   mRewindRequested{false};
    std::atomic<int>    mNumerator{4};
    std::atomic<int>    mDenominator{4};

    std::atomic<bool>  mClickEnabled{true};
    std::atomic<bool>  mAccentEnabled{true};
    std::atomic<float> mAccentVolume{1.0f};

    std::atomic<bool>  mQuarterEnabled{true};
    std::atomic<float> mQuarterVolume{0.85f};
    std::atomic<bool>  mEighthEnabled{true};
    std::atomic<float> mEighthVolume{0.55f};
    std::atomic<bool>  mSixteenthEnabled{true};
    std::atomic<float> mSixteenthVolume{0.35f};

    std::atomic<float> mMasterVolume{0.8f};

    // Double-buffer swap: main thread writes mPending*; audio thread owns mActive*.
    std::atomic<SampleBuffer*> mPendingAccentBuf{nullptr};
    std::atomic<SampleBuffer*> mPendingClickBuf{nullptr};
    SampleBuffer* mAccentBuf{nullptr};
    SampleBuffer* mClickBuf{nullptr};

    int32_t              mSampleRate{48000};
    int64_t              mCurrentFrame{0};
    int64_t              mLastQuarterNum{-1};
    int64_t              mLastEighthNum{-1};
    int64_t              mLastSixteenthNum{-1};
    std::atomic<int64_t> mAtomicFramePos{0};

    struct PlayHead {
        const SampleBuffer* buf    = nullptr;
        int                 pos    = 0;
        float               volume = 1.0f;
        bool                active = false;
        int                 tier   = 0; // 0=accent 1=quarter 2=eighth 3=sixteenth
    };
    PlayHead mAccentHead;
    PlayHead mClickHead;
};

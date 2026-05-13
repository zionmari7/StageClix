#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <cmath>

class AudioEngine : public oboe::AudioStreamCallback {
public:
    oboe::Result start();
    oboe::Result stop();
    void play();
    void pause();
    void setBpm(double bpm);

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *stream,
            void *audioData,
            int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> mStream;

    std::atomic<double> mBpm{120.0};
    std::atomic<bool>   mIsPlaying{false};

    // Accessed only from the audio callback thread
    double  mPhase         = 0.0;
    int64_t mBeatSamplePos = 0;
    int32_t mSampleRate    = 48000;

    static constexpr double kClickFreqHz       = 880.0;
    static constexpr double kClickDurationSecs = 0.050;
    static constexpr float  kAmplitude         = 0.7f;
    static constexpr double kTwoPi             = 2.0 * M_PI;
};

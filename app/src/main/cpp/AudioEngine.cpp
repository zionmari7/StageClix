#include "AudioEngine.h"
#include <android/log.h>

#define TAG "AudioEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

oboe::Result AudioEngine::start() {
    if (mStream) {
        mStream->requestStop();
        mStream->close();
        mStream.reset();
    }

    oboe::AudioStreamBuilder builder;
    oboe::Result result = builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setDataCallback(this)
        ->setErrorCallback(this)
        ->openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGE("openStream failed: %s", oboe::convertToText(result));
        return result;
    }

    mSampleRate    = mStream->getSampleRate();
    mPhase         = 0.0;
    mBeatSamplePos = 0;

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("requestStart failed: %s", oboe::convertToText(result));
    }
    return result;
}

oboe::Result AudioEngine::stop() {
    mIsPlaying = false;
    if (mStream) {
        oboe::Result r = mStream->requestStop();
        mStream->close();
        mStream.reset();
        return r;
    }
    return oboe::Result::OK;
}

void AudioEngine::play()             { mIsPlaying = true; }
void AudioEngine::pause()            { mIsPlaying = false; }
void AudioEngine::setBpm(double bpm) { if (bpm > 0.0) mBpm = bpm; }

oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream * /*stream*/,
        void *audioData,
        int32_t numFrames) {

    auto *out = static_cast<float *>(audioData);

    const double bpm     = mBpm.load(std::memory_order_relaxed);
    const bool   playing = mIsPlaying.load(std::memory_order_relaxed);

    // Recompute per-callback so setBpm() takes effect at the next beat boundary.
    const auto   beatInterval = static_cast<int64_t>(mSampleRate * 60.0 / bpm);
    const auto   clickSamples = static_cast<int64_t>(mSampleRate * kClickDurationSecs);
    const double phaseInc     = kTwoPi * kClickFreqHz / mSampleRate;

    for (int32_t i = 0; i < numFrames; ++i) {
        float sample = 0.0f;

        if (playing && mBeatSamplePos < clickSamples) {
            sample  = kAmplitude * static_cast<float>(std::sin(mPhase));
            mPhase += phaseInc;
            if (mPhase >= kTwoPi) mPhase -= kTwoPi;
        }

        *out++ = sample;

        if (++mBeatSamplePos >= beatInterval) {
            mBeatSamplePos = 0;
            mPhase         = 0.0; // start each click from phase zero for a clean attack
        }
    }

    return oboe::DataCallbackResult::Continue;
}

// Restart the stream after a device change or error.
void AudioEngine::onErrorAfterClose(oboe::AudioStream * /*stream*/, oboe::Result error) {
    LOGE("Stream error: %s — restarting", oboe::convertToText(error));
    start();
}

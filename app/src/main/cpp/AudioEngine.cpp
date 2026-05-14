#include "AudioEngine.h"
#include <android/log.h>
#include <cmath>

#define TAG "AudioEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)

// Padé approximant for tanh — accurate to ~0.5% for |x| <= 3, hard clips beyond.
static inline float softClip(float x) {
    if (x >  3.0f) return  1.0f;
    if (x < -3.0f) return -1.0f;
    float x2 = x * x;
    return x * (27.0f + x2) / (27.0f + 9.0f * x2);
}

AudioEngine::~AudioEngine() {
    stop();
    delete mAccentBuf;
    delete mClickBuf;
    SampleBuffer* p;
    p = mPendingAccentBuf.exchange(nullptr, std::memory_order_acq_rel);
    delete p;
    p = mPendingClickBuf.exchange(nullptr, std::memory_order_acq_rel);
    delete p;
}

bool AudioEngine::start(int deviceId) {
    if (mStream) {
        mStream->requestStop();
        mStream->close();
        mStream.reset();
    }

    // Oboe uses 0 (kUnspecified) for the default device; negative values are invalid.
    const int32_t resolvedDeviceId = (deviceId < 0) ? oboe::kUnspecified : deviceId;

    oboe::AudioStreamBuilder builder;
    auto openResult = builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setDeviceId(resolvedDeviceId)
        ->setDataCallback(this)
        ->setErrorCallback(this)
        ->openStream(mStream);

    LOGI("Stream open result: %s", oboe::convertToText(openResult));

    if (openResult != oboe::Result::OK) {
        LOGE("openStream failed: %s", oboe::convertToText(openResult));
        return false;
    }

    mSampleRate   = mStream->getSampleRate();
    mCurrentFrame = 0;
    mLastBeatNum  = -1;
    mAtomicFramePos.store(0, std::memory_order_relaxed);
    mAccentHead = {};
    mClickHead  = {};

    auto startResult = mStream->requestStart();

    LOGI("Stream state: %s",   oboe::convertToText(mStream->getState()));
    LOGI("Sample rate: %d",    mSampleRate);
    LOGI("Buffer size: %d",    mStream->getBufferSizeInFrames());

    if (startResult != oboe::Result::OK) {
        LOGE("requestStart failed: %s", oboe::convertToText(startResult));
        return false;
    }
    return true;
}

void AudioEngine::stop() {
    mIsPlaying = false;
    if (mStream) {
        mStream->requestStop();
        mStream->close();
        mStream.reset();
    }
}

void AudioEngine::play() {
    LOGI("play() called — mIsPlaying -> true");
    mIsPlaying = true;
}

void AudioEngine::pause() { mIsPlaying = false; }
void AudioEngine::rewind() {
    mIsPlaying = false;
    mRewindRequested.store(true, std::memory_order_release);
}

void AudioEngine::setBpm(double bpm) {
    if (bpm > 0.0) {
        LOGI("setBpm(%.2f) — framesPerBeat=%.1f", bpm, mSampleRate * 60.0 / bpm);
        mBpm = bpm;
    }
}

void AudioEngine::setTimeSignature(int numerator, int denominator) {
    if (numerator   > 0) mNumerator   = numerator;
    if (denominator > 0) mDenominator = denominator;
}

void AudioEngine::setClickEnabled(bool enabled)  { mClickEnabled  = enabled; }
void AudioEngine::setAccentEnabled(bool enabled) { mAccentEnabled = enabled; }
void AudioEngine::setAccentVolume(float volume)  { mAccentVolume  = volume;  }
void AudioEngine::setBeatEnabled(bool enabled)   { mBeatEnabled   = enabled; }
void AudioEngine::setBeatVolume(float volume)    { mBeatVolume    = volume;  }
void AudioEngine::setMasterVolume(float volume)  { mMasterVolume  = volume;  }

void AudioEngine::setAccentSample(const float* pcm, int length) {
    if (!pcm || length <= 0) return;
    LOGI("setAccentSample: %d frames", length);
    auto* next = new SampleBuffer(pcm, length);
    auto* old  = mPendingAccentBuf.exchange(next, std::memory_order_acq_rel);
    delete old;
}

void AudioEngine::setClickSample(const float* pcm, int length) {
    if (!pcm || length <= 0) return;
    LOGI("setClickSample: %d frames", length);
    auto* next = new SampleBuffer(pcm, length);
    auto* old  = mPendingClickBuf.exchange(next, std::memory_order_acq_rel);
    delete old;
}

double AudioEngine::getPositionBeats() {
    int64_t frame = mAtomicFramePos.load(std::memory_order_relaxed);
    double  bpm   = mBpm.load(std::memory_order_relaxed);
    return static_cast<double>(frame) / (mSampleRate * 60.0 / bpm);
}

// Called from audio thread: atomically pick up any pending sample, delete the old one.
void AudioEngine::swapPendingBuffers() {
    SampleBuffer* pending = mPendingAccentBuf.exchange(nullptr, std::memory_order_acq_rel);
    if (pending) {
        if (mAccentHead.buf == mAccentBuf) mAccentHead.active = false;
        delete mAccentBuf;
        mAccentBuf = pending;
    }

    pending = mPendingClickBuf.exchange(nullptr, std::memory_order_acq_rel);
    if (pending) {
        if (mClickHead.buf == mClickBuf) mClickHead.active = false;
        delete mClickBuf;
        mClickBuf = pending;
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* /*stream*/,
    void* audioData,
    int32_t numFrames) {

    // Log every 100 calls to confirm the callback is running without spamming.
    static int sCallCount = 0;
    if (++sCallCount % 100 == 0) {
        const bool playing = mIsPlaying.load(std::memory_order_relaxed);
        LOGI("onAudioReady called, numFrames=%d call#%d", numFrames, sCallCount);
        LOGI("  playing=%d clickEnabled=%d accentEnabled=%d beatEnabled=%d",
             (int)playing,
             (int)mClickEnabled.load(std::memory_order_relaxed),
             (int)mAccentEnabled.load(std::memory_order_relaxed),
             (int)mBeatEnabled.load(std::memory_order_relaxed));
        LOGI("  mAccentBuf=%s len=%d  mClickBuf=%s len=%d",
             mAccentBuf ? "ok" : "null", mAccentBuf ? mAccentBuf->length : 0,
             mClickBuf  ? "ok" : "null", mClickBuf  ? mClickBuf->length  : 0);
        double bpm = mBpm.load(std::memory_order_relaxed);
        LOGI("  bpm=%.2f framesPerBeat=%.1f", bpm, mSampleRate * 60.0 / bpm);
    }

    auto* out = static_cast<float*>(audioData);

    swapPendingBuffers();

    if (mRewindRequested.exchange(false, std::memory_order_acq_rel)) {
        mCurrentFrame = 0;
        mLastBeatNum  = -1;
        mAtomicFramePos.store(0, std::memory_order_relaxed);
        mAccentHead = {};
        mClickHead  = {};
    }

    const bool   playing       = mIsPlaying.load(std::memory_order_relaxed);
    const double bpm           = mBpm.load(std::memory_order_relaxed);
    const int    numerator     = mNumerator.load(std::memory_order_relaxed);
    const bool   clickEnabled  = mClickEnabled.load(std::memory_order_relaxed);
    const bool   accentEnabled = mAccentEnabled.load(std::memory_order_relaxed);
    const bool   beatEnabled   = mBeatEnabled.load(std::memory_order_relaxed);
    const float  accentVol     = mAccentVolume.load(std::memory_order_relaxed);
    const float  beatVol       = mBeatVolume.load(std::memory_order_relaxed);
    const float  masterVol     = mMasterVolume.load(std::memory_order_relaxed);

    const double framesPerBeat = mSampleRate * 60.0 / bpm;

    for (int32_t i = 0; i < numFrames; ++i) {
        float sample = 0.0f;

        if (playing && clickEnabled) {
            int64_t frame   = mCurrentFrame + i;
            int64_t beatNum = static_cast<int64_t>(static_cast<double>(frame) / framesPerBeat);

            if (beatNum != mLastBeatNum) {
                mLastBeatNum    = beatNum;
                int barBeat     = static_cast<int>(beatNum % numerator);

                if (barBeat == 0 && accentEnabled && mAccentBuf) {
                    mAccentHead = {mAccentBuf, 0, accentVol * masterVol, true};
                } else if (barBeat != 0 && beatEnabled && mClickBuf) {
                    mClickHead  = {mClickBuf,  0, beatVol   * masterVol, true};
                }
            }
        }

        if (mAccentHead.active) {
            if (mAccentHead.pos < mAccentHead.buf->length)
                sample += mAccentHead.buf->data[mAccentHead.pos++] * mAccentHead.volume;
            else
                mAccentHead.active = false;
        }

        if (mClickHead.active) {
            if (mClickHead.pos < mClickHead.buf->length)
                sample += mClickHead.buf->data[mClickHead.pos++] * mClickHead.volume;
            else
                mClickHead.active = false;
        }

        *out++ = softClip(sample);
    }

    if (playing) {
        mCurrentFrame += numFrames;
        mAtomicFramePos.store(mCurrentFrame, std::memory_order_relaxed);
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    LOGE("Stream error: %s — restarting", oboe::convertToText(error));
    start(0);
}

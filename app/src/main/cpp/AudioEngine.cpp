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

    mSampleRate        = mStream->getSampleRate();
    mCurrentFrame      = 0;
    mLastQuarterNum    = -1;
    mLastEighthNum     = -1;
    mLastSixteenthNum  = -1;
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

void AudioEngine::setClickEnabled(bool enabled)     { mClickEnabled     = enabled; }
void AudioEngine::setAccentEnabled(bool enabled)    { mAccentEnabled    = enabled; }
void AudioEngine::setAccentVolume(float volume)     { mAccentVolume     = volume;  }
void AudioEngine::setQuarterEnabled(bool enabled)   { mQuarterEnabled   = enabled; }
void AudioEngine::setQuarterVolume(float volume)    { mQuarterVolume    = volume;  }
void AudioEngine::setEighthEnabled(bool enabled)    { mEighthEnabled    = enabled; }
void AudioEngine::setEighthVolume(float volume)     { mEighthVolume     = volume;  }
void AudioEngine::setSixteenthEnabled(bool enabled) { mSixteenthEnabled = enabled; }
void AudioEngine::setSixteenthVolume(float volume)  { mSixteenthVolume  = volume;  }
void AudioEngine::setMasterVolume(float volume)     { mMasterVolume     = volume;  }

// Legacy: forward to quarter tier.
void AudioEngine::setBeatEnabled(bool enabled) { mQuarterEnabled = enabled; }
void AudioEngine::setBeatVolume(float volume)  { mQuarterVolume  = volume;  }

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
        LOGI("  playing=%d clickEnabled=%d accentEnabled=%d",
             (int)playing,
             (int)mClickEnabled.load(std::memory_order_relaxed),
             (int)mAccentEnabled.load(std::memory_order_relaxed));
        LOGI("  quarter=%d/%.2f eighth=%d/%.2f sixteenth=%d/%.2f",
             (int)mQuarterEnabled.load(std::memory_order_relaxed),
             mQuarterVolume.load(std::memory_order_relaxed),
             (int)mEighthEnabled.load(std::memory_order_relaxed),
             mEighthVolume.load(std::memory_order_relaxed),
             (int)mSixteenthEnabled.load(std::memory_order_relaxed),
             mSixteenthVolume.load(std::memory_order_relaxed));
        LOGI("  mAccentBuf=%s len=%d  mClickBuf=%s len=%d",
             mAccentBuf ? "ok" : "null", mAccentBuf ? mAccentBuf->length : 0,
             mClickBuf  ? "ok" : "null", mClickBuf  ? mClickBuf->length  : 0);
        double bpm = mBpm.load(std::memory_order_relaxed);
        LOGI("  bpm=%.2f framesPerBeat=%.1f", bpm, mSampleRate * 60.0 / bpm);
    }

    auto* out = static_cast<float*>(audioData);

    swapPendingBuffers();

    if (mRewindRequested.exchange(false, std::memory_order_acq_rel)) {
        mCurrentFrame     = 0;
        mLastQuarterNum   = -1;
        mLastEighthNum    = -1;
        mLastSixteenthNum = -1;
        mAtomicFramePos.store(0, std::memory_order_relaxed);
        mAccentHead = {};
        mClickHead  = {};
    }

    const bool   playing          = mIsPlaying.load(std::memory_order_relaxed);
    const double bpm              = mBpm.load(std::memory_order_relaxed);
    const int    numerator        = mNumerator.load(std::memory_order_relaxed);
    const bool   clickEnabled     = mClickEnabled.load(std::memory_order_relaxed);
    const bool   accentEnabled    = mAccentEnabled.load(std::memory_order_relaxed);
    const float  accentVol        = mAccentVolume.load(std::memory_order_relaxed);
    const bool   quarterEnabled   = mQuarterEnabled.load(std::memory_order_relaxed);
    const float  quarterVol       = mQuarterVolume.load(std::memory_order_relaxed);
    const bool   eighthEnabled    = mEighthEnabled.load(std::memory_order_relaxed);
    const float  eighthVol        = mEighthVolume.load(std::memory_order_relaxed);
    const bool   sixteenthEnabled = mSixteenthEnabled.load(std::memory_order_relaxed);
    const float  sixteenthVol     = mSixteenthVolume.load(std::memory_order_relaxed);
    const float  masterVol        = mMasterVolume.load(std::memory_order_relaxed);

    const double framesPerBeat = mSampleRate * 60.0 / bpm;

    for (int32_t i = 0; i < numFrames; ++i) {
        float sample = 0.0f;

        if (playing && clickEnabled) {
            int64_t frame        = mCurrentFrame + i;
            int64_t quarterNum   = static_cast<int64_t>(static_cast<double>(frame) / framesPerBeat);
            int64_t eighthNum    = static_cast<int64_t>(static_cast<double>(frame) / (framesPerBeat * 0.5));
            int64_t sixteenthNum = static_cast<int64_t>(static_cast<double>(frame) / (framesPerBeat * 0.25));

            if (quarterNum != mLastQuarterNum) {
                // Sync coarser trackers so they don't double-fire this frame.
                mLastQuarterNum   = quarterNum;
                mLastEighthNum    = eighthNum;
                mLastSixteenthNum = sixteenthNum;
                int barBeat = static_cast<int>(quarterNum % numerator);
                if (barBeat == 0) {
                    // Beat 1 of the bar — ONLY play if accent is enabled.
                    if (accentEnabled && mAccentBuf)
                        mAccentHead = {mAccentBuf, 0, accentVol * masterVol, true, 0};
                } else {
                    // Beats 2,3,4 — ONLY play if quarter is enabled.
                    if (quarterEnabled && mClickBuf)
                        mClickHead = {mClickBuf, 0, quarterVol * masterVol, true, 1};
                }
            } else if (eighthNum != mLastEighthNum) {
                mLastEighthNum    = eighthNum;
                mLastSixteenthNum = sixteenthNum;
                // 8th-note subdivision — ONLY play if eighth is enabled.
                if (eighthEnabled && mClickBuf)
                    mClickHead = {mClickBuf, 0, eighthVol * masterVol, true, 2};
            } else if (sixteenthNum != mLastSixteenthNum) {
                mLastSixteenthNum = sixteenthNum;
                // 16th-note subdivision — ONLY play if sixteenth is enabled.
                if (sixteenthEnabled && mClickBuf)
                    mClickHead = {mClickBuf, 0, sixteenthVol * masterVol, true, 3};
            }
        }

        // Drain accent — immediately silent when accentEnabled is false.
        if (mAccentHead.active) {
            if (accentEnabled && mAccentHead.pos < mAccentHead.buf->length) {
                sample += mAccentHead.buf->data[mAccentHead.pos++] * mAccentHead.volume;
            } else {
                mAccentHead.active = false;
            }
        }

        // Drain click (shared by QTR/8TH/16TH) — gated by the tier that triggered it.
        if (mClickHead.active) {
            const bool tierOn = (mClickHead.tier == 1) ? quarterEnabled
                              : (mClickHead.tier == 2) ? eighthEnabled
                              :                          sixteenthEnabled;
            if (tierOn && mClickHead.pos < mClickHead.buf->length) {
                sample += mClickHead.buf->data[mClickHead.pos++] * mClickHead.volume;
            } else {
                mClickHead.active = false;
            }
        }

        *out++ = softClip(sample);
    }

    if (playing) {
        mCurrentFrame += numFrames;
        mAtomicFramePos.store(mCurrentFrame, std::memory_order_relaxed);
    }

    // Log enabled flags every ~200 frames to confirm UI changes reach the audio thread.
    static int64_t sDebugFrames = 0;
    sDebugFrames += numFrames;
    if (sDebugFrames >= 200) {
        sDebugFrames = 0;
        LOGI("Enabled — ACC:%d QTR:%d 8TH:%d 16TH:%d",
             mAccentEnabled.load(std::memory_order_relaxed),
             mQuarterEnabled.load(std::memory_order_relaxed),
             mEighthEnabled.load(std::memory_order_relaxed),
             mSixteenthEnabled.load(std::memory_order_relaxed));
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    LOGE("Stream error: %s — restarting", oboe::convertToText(error));
    start(0);
}

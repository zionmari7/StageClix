#include "AudioEngine.h"
#include <android/log.h>
#include <algorithm>
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
    auto* b = builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setDeviceId(resolvedDeviceId)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    // Request 3 channels for independent click / cue / voice routing.
    // Fall back to 2 or 1 if the device doesn't support it.
    oboe::Result openResult = oboe::Result::ErrorInternal;
    for (int chCount : {3, 2, 1}) {
        b->setChannelCount(chCount);
        openResult = b->openStream(mStream);
        if (openResult == oboe::Result::OK) {
            LOGI("Opened stream with %d channels", chCount);
            break;
        }
        LOGI("Failed to open %d-ch stream: %s — retrying with fewer channels",
             chCount, oboe::convertToText(openResult));
    }

    if (openResult != oboe::Result::OK) {
        LOGE("openStream failed: %s", oboe::convertToText(openResult));
        return false;
    }

    mSampleRate   = mStream->getSampleRate();
    mChannelCount = mStream->getChannelCount();
    LOGI("Stream ready — sampleRate=%d channels=%d bufferFrames=%d",
         mSampleRate, mChannelCount, mStream->getBufferSizeInFrames());

    // Pre-allocate per-channel mix buffers; sized generously to avoid mid-callback growth.
    int bufFrames = std::max(mStream->getBufferSizeInFrames(), 2048);
    mClickMixBuf.assign(bufFrames, 0.0f);
    mCueMixBuf.assign(bufFrames, 0.0f);
    mVoiceMixBuf.assign(bufFrames, 0.0f);

    mCurrentFrame     = 0;
    mLastQuarterNum   = -1;
    mLastEighthNum    = -1;
    mLastSixteenthNum = -1;
    mLastFiredBar.store(-1, std::memory_order_relaxed);
    mAtomicFramePos.store(0, std::memory_order_relaxed);
    mAccentHead = {};
    mClickHead  = {};

    auto startResult = mStream->requestStart();
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

    static int sCallCount = 0;
    if (++sCallCount % 100 == 0) {
        const bool playing = mIsPlaying.load(std::memory_order_relaxed);
        LOGI("onAudioReady numFrames=%d channels=%d call#%d playing=%d",
             numFrames, mChannelCount, sCallCount, (int)playing);
        LOGI("  clickEnabled=%d accentEnabled=%d quarter=%d/%.2f eighth=%d/%.2f sixteenth=%d/%.2f",
             (int)mClickEnabled.load(std::memory_order_relaxed),
             (int)mAccentEnabled.load(std::memory_order_relaxed),
             (int)mQuarterEnabled.load(std::memory_order_relaxed),
             mQuarterVolume.load(std::memory_order_relaxed),
             (int)mEighthEnabled.load(std::memory_order_relaxed),
             mEighthVolume.load(std::memory_order_relaxed),
             (int)mSixteenthEnabled.load(std::memory_order_relaxed),
             mSixteenthVolume.load(std::memory_order_relaxed));
        double bpm = mBpm.load(std::memory_order_relaxed);
        LOGI("  bpm=%.2f framesPerBeat=%.1f", bpm, mSampleRate * 60.0 / bpm);
    }

    swapPendingBuffers();

    if (mRewindRequested.exchange(false, std::memory_order_acq_rel)) {
        mCurrentFrame     = 0;
        mLastQuarterNum   = -1;
        mLastEighthNum    = -1;
        mLastSixteenthNum = -1;
        mLastFiredBar.store(-1, std::memory_order_relaxed);
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

    // Grow mix buffers if a larger-than-expected callback arrives (no-alloc in steady state).
    if (static_cast<int32_t>(mClickMixBuf.size()) < numFrames) {
        mClickMixBuf.assign(numFrames, 0.0f);
        mCueMixBuf.assign(numFrames, 0.0f);
        mVoiceMixBuf.assign(numFrames, 0.0f);
    }
    std::fill_n(mClickMixBuf.data(), numFrames, 0.0f);
    std::fill_n(mCueMixBuf.data(),   numFrames, 0.0f);
    std::fill_n(mVoiceMixBuf.data(), numFrames, 0.0f);

    // ── Channel 0: click track (accent + beat ticks) ──────────────────────────
    for (int32_t i = 0; i < numFrames; ++i) {
        float sample = 0.0f;

        if (playing && clickEnabled) {
            int64_t frame        = mCurrentFrame + i;
            int64_t quarterNum   = static_cast<int64_t>(static_cast<double>(frame) / framesPerBeat);
            int64_t eighthNum    = static_cast<int64_t>(static_cast<double>(frame) / (framesPerBeat * 0.5));
            int64_t sixteenthNum = static_cast<int64_t>(static_cast<double>(frame) / (framesPerBeat * 0.25));

            if (quarterNum != mLastQuarterNum) {
                mLastQuarterNum   = quarterNum;
                mLastEighthNum    = eighthNum;
                mLastSixteenthNum = sixteenthNum;
                int barBeat = static_cast<int>(quarterNum % numerator);
                if (barBeat == 0) {
                    if (accentEnabled && mAccentBuf)
                        mAccentHead = {mAccentBuf, 0, accentVol * masterVol, true, 0};
                    if (quarterEnabled && mClickBuf)
                        mClickHead = {mClickBuf, 0, quarterVol * masterVol, true, 1};
                } else {
                    if (quarterEnabled && mClickBuf)
                        mClickHead = {mClickBuf, 0, quarterVol * masterVol, true, 1};
                }
            } else if (eighthNum != mLastEighthNum) {
                mLastEighthNum    = eighthNum;
                mLastSixteenthNum = sixteenthNum;
                if (eighthEnabled && mClickBuf)
                    mClickHead = {mClickBuf, 0, eighthVol * masterVol, true, 2};
            } else if (sixteenthNum != mLastSixteenthNum) {
                mLastSixteenthNum = sixteenthNum;
                if (sixteenthEnabled && mClickBuf)
                    mClickHead = {mClickBuf, 0, sixteenthVol * masterVol, true, 3};
            }
        }

        if (mAccentHead.active) {
            if (accentEnabled && mAccentHead.pos < mAccentHead.buf->length)
                sample += mAccentHead.buf->data[mAccentHead.pos++] * mAccentHead.volume;
            else
                mAccentHead.active = false;
        }

        if (mClickHead.active) {
            const bool tierOn = (mClickHead.tier == 1) ? quarterEnabled
                              : (mClickHead.tier == 2) ? eighthEnabled
                              :                          sixteenthEnabled;
            if (tierOn && mClickHead.pos < mClickHead.buf->length)
                sample += mClickHead.buf->data[mClickHead.pos++] * mClickHead.volume;
            else
                mClickHead.active = false;
        }

        mClickMixBuf[i] = softClip(sample);
    }

    // ── Channel 1: cue audio (FilePlayer — reserved, currently silent) ────────

    // ── Voice cue bar-change check (fires 1 bar early as a heads-up) ─────────
    if (playing) {
        double currentBeat = static_cast<double>(mCurrentFrame) / framesPerBeat;
        int currentBar = static_cast<int>(currentBeat / numerator);
        if (currentBar != mLastFiredBar.load(std::memory_order_relaxed)) {
            mLastFiredBar.store(currentBar, std::memory_order_relaxed);
            int lookAheadBar = currentBar + 1;
            if (mTimelineMutex.try_lock()) {
                for (auto& event : mCueTimeline) {
                    if (event.first == lookAheadBar) {
                        mVoiceCuePlayer->trigger(event.second);
                        break;
                    }
                }
                mTimelineMutex.unlock();
            }
        }
    }

    // ── Channel 2: voice cues ─────────────────────────────────────────────────
    mVoiceCuePlayer->render(mVoiceMixBuf.data(), numFrames, 1);

    // ── Interleave the 3 mono channels into the Oboe output buffer ───────────
    // 3+ ch: Ch0=click  Ch1=cue  Ch2=voice  (extra channels silenced)
    //    2ch: Ch0=click  Ch1=cue+voice mixed
    //    1ch: everything mixed to mono
    auto* out = static_cast<float*>(audioData);
    for (int32_t f = 0; f < numFrames; ++f) {
        if (mChannelCount >= 3) {
            out[f * mChannelCount + 0] = mClickMixBuf[f];
            out[f * mChannelCount + 1] = mCueMixBuf[f];
            out[f * mChannelCount + 2] = mVoiceMixBuf[f];
            for (int32_t c = 3; c < mChannelCount; ++c)
                out[f * mChannelCount + c] = 0.0f;
        } else if (mChannelCount == 2) {
            out[f * 2 + 0] = mClickMixBuf[f];
            out[f * 2 + 1] = softClip(mCueMixBuf[f] + mVoiceMixBuf[f]);
        } else {
            out[f] = softClip(mClickMixBuf[f] + mCueMixBuf[f] + mVoiceMixBuf[f]);
        }
    }

    if (playing) {
        mCurrentFrame += numFrames;
        mAtomicFramePos.store(mCurrentFrame, std::memory_order_relaxed);
    }

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

void AudioEngine::loadVoiceCue(int cueId, const float* pcm, int frameCount) {
    mVoiceCuePlayer->loadCue(cueId, pcm, frameCount);
}

void AudioEngine::setCueTimeline(const int* bars, const int* cueIds, int count) {
    std::vector<std::pair<int,int>> timeline;
    timeline.reserve(count);
    for (int i = 0; i < count; ++i)
        timeline.emplace_back(bars[i], cueIds[i]);
    std::lock_guard<std::mutex> lock(mTimelineMutex);
    mCueTimeline = std::move(timeline);
}

void AudioEngine::setVoiceCueVolume(float volume) {
    mVoiceCuePlayer->setVolume(volume);
}

void AudioEngine::setVoiceCueMuted(bool muted) {
    mVoiceCuePlayer->setMuted(muted);
}

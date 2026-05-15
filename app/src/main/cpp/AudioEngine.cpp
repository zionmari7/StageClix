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
    mCurrentBar.store(-1, std::memory_order_relaxed);
    mCurrentBeat.store(-1, std::memory_order_relaxed);
    mCueFiredThisBar.store(false, std::memory_order_relaxed);
    mCountFiredThisBeat.store(false, std::memory_order_relaxed);
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
    mBackingMixer->play();
}

void AudioEngine::pause() { mIsPlaying = false; mBackingMixer->pause(); }

void AudioEngine::rewind() {
    mIsPlaying = false;
    mCurrentBar.store(-1, std::memory_order_relaxed);
    mCurrentBeat.store(-1, std::memory_order_relaxed);
    mCueFiredThisBar.store(false, std::memory_order_relaxed);
    mCountFiredThisBeat.store(false, std::memory_order_relaxed);
    mVoiceCuePlayer->reset();
    mBackingMixer->rewind();
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

void AudioEngine::setBeatPattern(
    const BeatEvent* events,
    int count,
    int timeSigNumerator,
    double bpm
) {
    std::vector<BeatEvent> pattern;
    if (events && count > 0) {
        pattern.assign(events, events + count);
    }
    LOGI("setBeatPattern: %d events, timeSig=%d, bpm=%.1f", count, timeSigNumerator, bpm);
    for (int i = 0; i < count; i++) {
        LOGI("  event[%d]: beat=%d row=%d sub=%d",
             i, events[i].beatIndex, events[i].row, events[i].subIndex);
    }
    {
        std::lock_guard<std::mutex> lock(mBeatPatternMutex);
        mBeatPattern = std::move(pattern);
    }
    setTimeSignature(timeSigNumerator, mDenominator.load(std::memory_order_relaxed));
    setBpm(bpm);
    mLastSixteenthNum = -1;
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

void AudioEngine::triggerSampleAt(
    SampleBuffer* buf,
    int offsetInBuffer,
    float gain,
    float* mixBuffer,
    int numFrames
) {
    if (!buf || buf->length == 0) return;
    int readPos = 0;
    for (int i = offsetInBuffer; i < numFrames && readPos < buf->length; i++, readPos++) {
        mixBuffer[i] += buf->data[readPos] * gain;
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

    if (mSectionsDirty.load(std::memory_order_relaxed)) {
        std::lock_guard<std::mutex> lock(mSectionsMutex);
        mSectionsActive = mSectionsPending;
        mSectionsDirty.store(false, std::memory_order_relaxed);
    }

    if (mRewindRequested.exchange(false, std::memory_order_acq_rel)) {
        mCurrentFrame     = 0;
        mLastQuarterNum   = -1;
        mLastEighthNum    = -1;
        mLastSixteenthNum = -1;
        mCurrentBar.store(-1, std::memory_order_relaxed);
        mCurrentBeat.store(-1, std::memory_order_relaxed);
        mCueFiredThisBar.store(false, std::memory_order_relaxed);
        mCountFiredThisBeat.store(false, std::memory_order_relaxed);
        mAtomicFramePos.store(0, std::memory_order_relaxed);
        mAccentHead = {};
        mClickHead  = {};
        mVoiceCuePlayer->reset();
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

    if (mAuditPending.exchange(false, std::memory_order_acq_rel)) {
        const int   auditRow  = mPendingAuditRow.load(std::memory_order_relaxed);
        const float masterV   = mMasterVolume.load(std::memory_order_relaxed);
        if (auditRow == 0 && mAccentBuf) {
            triggerSampleAt(mAccentBuf, 0,
                mAccentVolume.load(std::memory_order_relaxed) * masterV,
                mClickMixBuf.data(), numFrames);
        } else if (mClickBuf) {
            const float vol =
                (auditRow == 1) ? mQuarterVolume.load(std::memory_order_relaxed)
              : (auditRow == 2) ? mEighthVolume.load(std::memory_order_relaxed)
              :                   mSixteenthVolume.load(std::memory_order_relaxed);
            triggerSampleAt(mClickBuf, 0, vol * masterV, mClickMixBuf.data(), numFrames);
        }
    }

    std::vector<BeatEvent> beatPattern;
    {
        std::lock_guard<std::mutex> lock(mBeatPatternMutex);
        beatPattern = mBeatPattern;
    }

    // ── Channel 0: click track (accent + beat ticks) ──────────────────────────
    if (playing && clickEnabled && !beatPattern.empty()) {
        const int timeSigNum   = std::max(numerator, 1);
        const int64_t framesPerBar = static_cast<int64_t>(framesPerBeat * timeSigNum);
        const int64_t posInBar = (framesPerBar > 0) ? mCurrentFrame % framesPerBar : 0;

        for (auto& event : beatPattern) {
            double eventBeatPos = static_cast<double>(event.beatIndex);
            if (event.row == 2)      // 8TH
                eventBeatPos += event.subIndex * 0.5;
            else if (event.row == 3) // 16TH
                eventBeatPos += event.subIndex * 0.25;

            const int64_t eventFrame = static_cast<int64_t>(eventBeatPos * framesPerBeat);

            if (posInBar <= eventFrame && posInBar + numFrames > eventFrame) {
                const int offsetInBuffer = static_cast<int>(eventFrame - posInBar);

                if (event.row == 0) { // ACC
                    if (accentEnabled)
                        triggerSampleAt(mAccentBuf, offsetInBuffer,
                            accentVol * masterVol, mClickMixBuf.data(), numFrames);
                } else {
                    float gain = 1.0f;
                    bool  tierOn = false;
                    if (event.row == 1) {
                        tierOn = quarterEnabled;
                        gain   = quarterVol * masterVol;
                    } else if (event.row == 2) {
                        tierOn = eighthEnabled;
                        gain   = eighthVol * masterVol;
                    } else if (event.row == 3) {
                        tierOn = sixteenthEnabled;
                        gain   = sixteenthVol * masterVol;
                    }
                    if (tierOn)
                        triggerSampleAt(mClickBuf, offsetInBuffer,
                            gain, mClickMixBuf.data(), numFrames);
                }
            }
        }

        for (int32_t i = 0; i < numFrames; ++i)
            mClickMixBuf[i] = softClip(mClickMixBuf[i]);
    }

    // ── Channel 1: cue / backing audio (BackingMixer) ────────────────────────
    mBackingMixer->render(mCueMixBuf.data(), numFrames, 1);

    // ── Voice cue beat-accurate trigger ──────────────────────────────────────
    if (playing) {
        double totalBeats = static_cast<double>(mCurrentFrame) / framesPerBeat;
        int newBar  = static_cast<int>(totalBeats / numerator) + 1; // 1-indexed
        int newBeat = static_cast<int>(totalBeats) % numerator;     // 0-indexed

        if (newBar != mCurrentBar.load(std::memory_order_relaxed)) {
            mCurrentBar.store(newBar, std::memory_order_relaxed);
            mCueFiredThisBar.store(false, std::memory_order_relaxed);
            LOGI("Bar: %d Beat: %d", newBar, newBeat);
        }

        bool beatChanged = (newBeat != mCurrentBeat.load(std::memory_order_relaxed));
        if (beatChanged) {
            mCurrentBeat.store(newBeat, std::memory_order_relaxed);
            mCountFiredThisBeat.store(false, std::memory_order_relaxed);
        }

        for (auto& section : mSectionsActive) {
            int N        = section.sectionBar;
            int cueBar   = N - 2;
            int countBar = N - 1;

            // CUE BAR: fire section voice cue on beat 3 (0-indexed beat 2)
            if (newBar == cueBar &&
                newBeat == 2 &&
                !mCueFiredThisBar.load(std::memory_order_relaxed)) {
                mCueFiredThisBar.store(true, std::memory_order_relaxed);
                mVoiceCuePlayer->trigger(section.voiceCueId);
                LOGI("Fired voice cue id=%d at bar=%d beat=3",
                     section.voiceCueId, newBar);
            }

            // COUNT BAR: fire count cue on each beat change
            if (newBar == countBar &&
                beatChanged &&
                !mCountFiredThisBeat.load(std::memory_order_relaxed) &&
                newBeat < numerator) {
                mCountFiredThisBeat.store(true, std::memory_order_relaxed);
                // newBeat 0→vc_1(id=0), 1→vc_2(id=1), etc.
                mVoiceCuePlayer->trigger(newBeat);
                LOGI("Fired count vc_%d at bar=%d beat=%d",
                     newBeat + 1, newBar, newBeat + 1);
            }
        }
    }

    // ── Channel 2: voice cues ─────────────────────────────────────────────────
    mVoiceCuePlayer->render(mVoiceMixBuf.data(), numFrames, 1);

    // ── Interleave the 3 mono channels into the Oboe output buffer ───────────
    // 3+ ch: Ch0=click  Ch1=cue  Ch2=voice  (extra channels silenced)
    //    2ch: Ch0=click+voice  Ch1=cue
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
            // Ch0 = click + voice cues (drummer's IEM)
            // Ch1 = cue audio (backing track / conductor feed)
            out[f * 2 + 0] = mClickMixBuf[f] + mVoiceMixBuf[f];
            out[f * 2 + 1] = mCueMixBuf[f];
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

// Legacy single-file cue wrappers — delegate to BackingMixer track 0.
void AudioEngine::setCueData(const float* pcm, int32_t frameCount, int32_t channelCount) {
    mBackingMixer->setTrackData(0, pcm, frameCount, channelCount);
}
void AudioEngine::setCueVolume(float volume) { mBackingMixer->setTrackVolume(0, volume); }
void AudioEngine::setCueMuted(bool muted)    { mBackingMixer->setTrackMuted(0, muted);   }
void AudioEngine::playCue()                  { mBackingMixer->play();                    }
void AudioEngine::pauseCue()                 { mBackingMixer->pause();                   }
void AudioEngine::rewindCue()                { mBackingMixer->rewind();                  }

int  AudioEngine::addBackingTrack()                   { return mBackingMixer->addTrack(); }
void AudioEngine::removeBackingTrack(int index)       { mBackingMixer->removeTrack(index); }
void AudioEngine::setBackingTrackData(int index, const float* pcm, int32_t frames, int32_t channels) {
    mBackingMixer->setTrackData(index, pcm, frames, channels);
}
void AudioEngine::setBackingTrackVolume(int index, float vol)  { mBackingMixer->setTrackVolume(index, vol);  }
void AudioEngine::setBackingTrackMuted(int index, bool muted)  { mBackingMixer->setTrackMuted(index, muted); }

void AudioEngine::loadVoiceCue(int cueId, const float* pcm, int frameCount) {
    mVoiceCuePlayer->loadCue(cueId, pcm, frameCount);
}

void AudioEngine::setSections(const int* sectionBars, const int* voiceCueIds, int count) {
    std::vector<VoiceCueSection> sections;
    sections.reserve(count);
    for (int i = 0; i < count; ++i)
        sections.push_back({sectionBars[i], voiceCueIds[i]});
    std::lock_guard<std::mutex> lock(mSectionsMutex);
    mSectionsPending = std::move(sections);
    mSectionsDirty.store(true, std::memory_order_relaxed);
}

void AudioEngine::auditNote(int row) {
    mPendingAuditRow.store(row, std::memory_order_relaxed);
    mAuditPending.store(true, std::memory_order_release);
}

void AudioEngine::setVoiceCueVolume(float volume) {
    mVoiceCuePlayer->setVolume(volume);
}

void AudioEngine::setVoiceCueMuted(bool muted) {
    mVoiceCuePlayer->setMuted(muted);
}

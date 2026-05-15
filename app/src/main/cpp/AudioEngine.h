#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <cstring>
#include <memory>
#include <mutex>
#include <utility>
#include <vector>
#include "BackingMixer.h"
#include "VoiceCuePlayer.h"

struct VoiceCueSection {
    int sectionBar;
    int voiceCueId;
};

struct BeatEvent {
    int beatIndex;   // 0-based beat in bar
    int row;         // 0=ACC 1=QTR 2=8TH 3=16TH
    int subIndex;    // subdivision within beat
};

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
    void   setBeatPattern(
        const BeatEvent* events,
        int count,
        int timeSigNumerator,
        double bpm
    );

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

    void   setCueData(const float* pcm, int32_t frameCount, int32_t channelCount);
    void   setCueVolume(float volume);
    void   setCueMuted(bool muted);
    void   playCue();
    void   pauseCue();
    void   rewindCue();

    int    addBackingTrack();
    void   removeBackingTrack(int index);
    void   setBackingTrackData(int index, const float* pcm, int32_t frames, int32_t channels);
    void   setBackingTrackVolume(int index, float vol);
    void   setBackingTrackMuted(int index, bool muted);

    void   loadVoiceCue(int cueId, const float* pcm, int frameCount);
    void   setSections(const int* sectionBars, const int* voiceCueIds, int count);
    void   setVoiceCueVolume(float volume);
    void   setVoiceCueMuted(bool muted);

    void   auditNote(int row);

    double getPositionBeats();

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    void swapPendingBuffers();
    void triggerSampleAt(SampleBuffer* buf, int offsetInBuffer, float gain,
                         float* mixBuffer, int numFrames);

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

    std::unique_ptr<BackingMixer>     mBackingMixer{new BackingMixer()};
    std::unique_ptr<VoiceCuePlayer>  mVoiceCuePlayer{new VoiceCuePlayer()};
    std::vector<VoiceCueSection>     mSectionsPending;
    std::vector<VoiceCueSection>     mSectionsActive;
    std::atomic<bool>                mSectionsDirty{false};
    std::mutex                       mSectionsMutex;
    std::vector<BeatEvent>           mBeatPattern;
    std::mutex                       mBeatPatternMutex;

    std::atomic<int>  mCurrentBar{-1};
    std::atomic<int>  mCurrentBeat{-1};
    std::atomic<bool> mCueFiredThisBar{false};
    std::atomic<bool> mCountFiredThisBeat{false};

    std::vector<float> mClickMixBuf;
    std::vector<float> mCueMixBuf;
    std::vector<float> mVoiceMixBuf;

    int32_t              mSampleRate{48000};
    int32_t              mChannelCount{1};
    int64_t              mCurrentFrame{0};
    int64_t              mLastQuarterNum{-1};
    int64_t              mLastEighthNum{-1};
    int64_t              mLastSixteenthNum{-1};
    std::atomic<int64_t> mAtomicFramePos{0};

    std::atomic<bool> mAuditPending{false};
    std::atomic<int>  mPendingAuditRow{0};

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

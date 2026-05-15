package com.stageclix.audio

class AudioEngineJni {

    private var handle: Long = 0L

    companion object {
        init {
            System.loadLibrary("stageclix_engine")
        }
    }

    fun create()                                      { handle = nativeCreate() }
    fun destroy()                                     { nativeDestroy(handle) }
    fun start(deviceId: Int = -1): Boolean            = nativeStart(handle, deviceId)
    fun stop()                                        { nativeStop(handle) }
    fun play()                                        { nativePlay(handle) }
    fun pause()                                       { nativePause(handle) }
    fun rewind()                                      { nativeRewind(handle) }
    fun setBpm(bpm: Double)                           { nativeSetBpm(handle, bpm) }
    fun setTimeSignature(numerator: Int, denominator: Int) =
        nativeSetTimeSignature(handle, numerator, denominator)
    fun setBeatPattern(
        beatIndices: IntArray,
        rows: IntArray,
        subIndices: IntArray,
        timeSigNumerator: Int,
        bpm: Double
    ) {
        nativeSetBeatPattern(handle, beatIndices, rows, subIndices, timeSigNumerator, bpm)
    }
    fun setClickEnabled(enabled: Boolean)             { nativeSetClickEnabled(handle, enabled) }
    fun setClickSample(pcm: FloatArray)               { nativeSetClickSample(handle, pcm) }
    fun setAccentSample(pcm: FloatArray)              { nativeSetAccentSample(handle, pcm) }
    fun setAccentEnabled(enabled: Boolean)            { nativeSetAccentEnabled(handle, enabled) }
    fun setAccentVolume(volume: Float)                { nativeSetAccentVolume(handle, volume) }
    fun setBeatEnabled(enabled: Boolean)              { nativeSetBeatEnabled(handle, enabled) }
    fun setBeatVolume(volume: Float)                  { nativeSetBeatVolume(handle, volume) }
    fun setQuarterEnabled(enabled: Boolean)           { nativeSetQuarterEnabled(handle, enabled) }
    fun setQuarterVolume(volume: Float)               { nativeSetQuarterVolume(handle, volume) }
    fun setEighthEnabled(enabled: Boolean)            { nativeSetEighthEnabled(handle, enabled) }
    fun setEighthVolume(volume: Float)                { nativeSetEighthVolume(handle, volume) }
    fun setSixteenthEnabled(enabled: Boolean)         { nativeSetSixteenthEnabled(handle, enabled) }
    fun setSixteenthVolume(volume: Float)             { nativeSetSixteenthVolume(handle, volume) }
    fun setMasterVolume(volume: Float)                { nativeSetMasterVolume(handle, volume) }
    fun getPositionBeats(): Double                    = nativeGetPositionBeats(handle)
    fun setCueData(pcm: FloatArray, frameCount: Int, channels: Int) {
        nativeSetCueData(handle, pcm, frameCount, channels)
    }
    fun setCueVolume(volume: Float)                  { nativeSetCueVolume(handle, volume) }
    fun setCueMuted(muted: Boolean)                  { nativeSetCueMuted(handle, muted)  }
    fun playCue()                                    { nativePlayCue(handle)              }
    fun pauseCue()                                   { nativePauseCue(handle)             }
    fun rewindCue()                                  { nativeRewindCue(handle)            }

    fun addBackingTrack(): Int                       = nativeAddBackingTrack(handle)
    fun removeBackingTrack(index: Int)               { nativeRemoveBackingTrack(handle, index) }
    fun setBackingTrackData(index: Int, pcm: FloatArray, channels: Int) {
        nativeSetBackingTrackData(handle, index, pcm, channels)
    }
    fun setBackingTrackVolume(index: Int, volume: Float) { nativeSetBackingTrackVolume(handle, index, volume) }
    fun setBackingTrackMuted(index: Int, muted: Boolean) { nativeSetBackingTrackMuted(handle, index, muted) }

    fun auditNote(row: Int, clickTypeIndex: Int = 0)  { nativeAuditNote(handle, row, clickTypeIndex) }

    fun loadVoiceCue(cueId: Int, pcm: FloatArray)    { nativeLoadVoiceCue(handle, cueId, pcm) }
    fun setSections(sectionBars: IntArray, voiceCueIds: IntArray) { nativeSetSections(handle, sectionBars, voiceCueIds) }
    fun setVoiceCueVolume(volume: Float)              { nativeSetVoiceCueVolume(handle, volume) }
    fun setVoiceCueMuted(muted: Boolean)              { nativeSetVoiceCueMuted(handle, muted) }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long, deviceId: Int): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativePlay(handle: Long)
    private external fun nativePause(handle: Long)
    private external fun nativeRewind(handle: Long)
    private external fun nativeSetBpm(handle: Long, bpm: Double)
    private external fun nativeSetTimeSignature(handle: Long, numerator: Int, denominator: Int)
    private external fun nativeSetBeatPattern(
        handle: Long,
        beatIndices: IntArray,
        rows: IntArray,
        subIndices: IntArray,
        timeSigNumerator: Int,
        bpm: Double
    )
    private external fun nativeSetClickEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetClickSample(handle: Long, pcm: FloatArray)
    private external fun nativeSetAccentSample(handle: Long, pcm: FloatArray)
    private external fun nativeSetAccentEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetAccentVolume(handle: Long, volume: Float)
    private external fun nativeSetBeatEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetBeatVolume(handle: Long, volume: Float)
    private external fun nativeSetQuarterEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetQuarterVolume(handle: Long, volume: Float)
    private external fun nativeSetEighthEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetEighthVolume(handle: Long, volume: Float)
    private external fun nativeSetSixteenthEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetSixteenthVolume(handle: Long, volume: Float)
    private external fun nativeSetMasterVolume(handle: Long, volume: Float)
    private external fun nativeGetPositionBeats(handle: Long): Double
    private external fun nativeSetCueData(handle: Long, pcm: FloatArray, frameCount: Int, channelCount: Int)
    private external fun nativeSetCueVolume(handle: Long, volume: Float)
    private external fun nativeSetCueMuted(handle: Long, muted: Boolean)
    private external fun nativePlayCue(handle: Long)
    private external fun nativePauseCue(handle: Long)
    private external fun nativeRewindCue(handle: Long)
    private external fun nativeAddBackingTrack(handle: Long): Int
    private external fun nativeRemoveBackingTrack(handle: Long, index: Int)
    private external fun nativeSetBackingTrackData(handle: Long, index: Int, pcm: FloatArray, channels: Int)
    private external fun nativeSetBackingTrackVolume(handle: Long, index: Int, volume: Float)
    private external fun nativeSetBackingTrackMuted(handle: Long, index: Int, muted: Boolean)
    private external fun nativeAuditNote(handle: Long, row: Int, clickTypeIndex: Int)
    private external fun nativeLoadVoiceCue(handle: Long, cueId: Int, pcm: FloatArray)
    private external fun nativeSetSections(handle: Long, sectionBars: IntArray, voiceCueIds: IntArray)
    private external fun nativeSetVoiceCueVolume(handle: Long, volume: Float)
    private external fun nativeSetVoiceCueMuted(handle: Long, muted: Boolean)
}

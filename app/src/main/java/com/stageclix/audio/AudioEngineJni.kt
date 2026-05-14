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
    fun loadVoiceCue(cueId: Int, pcm: FloatArray)    { nativeLoadVoiceCue(handle, cueId, pcm) }
    fun setCueTimeline(bars: IntArray, cueIds: IntArray) { nativeSetCueTimeline(handle, bars, cueIds) }
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
    private external fun nativeLoadVoiceCue(handle: Long, cueId: Int, pcm: FloatArray)
    private external fun nativeSetCueTimeline(handle: Long, bars: IntArray, cueIds: IntArray)
    private external fun nativeSetVoiceCueVolume(handle: Long, volume: Float)
    private external fun nativeSetVoiceCueMuted(handle: Long, muted: Boolean)
}

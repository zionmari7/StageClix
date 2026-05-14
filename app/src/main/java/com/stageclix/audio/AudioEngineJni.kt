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
    fun setMasterVolume(volume: Float)                { nativeSetMasterVolume(handle, volume) }
    fun getPositionBeats(): Double                    = nativeGetPositionBeats(handle)

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
    private external fun nativeSetMasterVolume(handle: Long, volume: Float)
    private external fun nativeGetPositionBeats(handle: Long): Double
}

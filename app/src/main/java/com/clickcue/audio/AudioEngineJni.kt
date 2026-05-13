package com.clickcue.audio

class AudioEngineJni {

    companion object {
        init {
            System.loadLibrary("clickcue_engine")
        }
    }

    external fun create(): Long
    external fun destroy(handle: Long)
    external fun start(handle: Long): Int
    external fun stop(handle: Long): Int
    external fun play(handle: Long)
    external fun pause(handle: Long)
    external fun setBpm(handle: Long, bpm: Double)
}

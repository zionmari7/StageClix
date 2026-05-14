package com.stageclix.audio

import android.content.Context
import com.stageclix.data.VoiceCue

object VoiceCueLoader {
    fun loadAll(context: Context, engine: AudioEngineJni) {
        VoiceCue.entries.forEachIndexed { index, cue ->
            val pcm = SampleLoader.loadFromRaw(context, cue.rawResId)
            engine.loadVoiceCue(index, pcm)
        }
    }
}

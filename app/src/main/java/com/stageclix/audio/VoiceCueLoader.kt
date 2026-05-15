package com.stageclix.audio

import android.content.Context
import android.util.Log
import com.stageclix.data.VoiceCue

object VoiceCueLoader {
    private const val TAG = "StageClix"

    fun loadAll(context: Context, engine: AudioEngineJni) {
        VoiceCue.entries.forEachIndexed { index, cue ->
            val pcm = try {
                SampleLoader.loadFromRaw(context, cue.rawResId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cue ${cue.displayName} resId=${cue.rawResId}", e)
                return@forEachIndexed
            }
            if (pcm.isEmpty()) {
                Log.e(TAG, "Empty PCM for cue ${cue.displayName} resId=${cue.rawResId} — skipping")
                return@forEachIndexed
            }
            Log.d(TAG, "Loaded cue ${cue.displayName} index=$index pcm size=${pcm.size}")
            engine.loadVoiceCue(index, pcm)
        }
        Log.d(TAG, "VoiceCueLoader.loadAll complete (${VoiceCue.entries.size} cues)")
    }
}

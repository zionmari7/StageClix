package com.stageclix.audio

import android.content.Context
import android.net.Uri

object PcmDecoder {

    data class DecodedAudio(val pcm: FloatArray, val channelCount: Int)

    fun decode(context: Context, uri: Uri): DecodedAudio? = try {
        val (pcm, channels) = SampleLoader.loadCueFromUri(context, uri)
        DecodedAudio(pcm, channels)
    } catch (e: Exception) {
        null
    }
}

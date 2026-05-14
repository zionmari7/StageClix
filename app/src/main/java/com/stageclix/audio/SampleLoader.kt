package com.stageclix.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.stageclix.data.BeatMode
import com.stageclix.data.ClickType
import java.nio.ByteBuffer
import kotlin.math.abs

object SampleLoader {

    /**
     * Decodes a raw audio resource to a normalized mono FloatArray.
     * Stereo files are downmixed by averaging L+R. Peak sample is scaled to 1.0f.
     */
    fun loadFromRaw(context: Context, resId: Int): FloatArray {
        val afd = context.resources.openRawResourceFd(resId)
        val extractor = MediaExtractor().apply {
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        }
        afd.close()

        val trackIndex = (0 until extractor.trackCount).indexOfFirst { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }
        require(trackIndex >= 0) { "No audio track found in resource $resId" }
        extractor.selectTrack(trackIndex)

        val inputFormat = extractor.getTrackFormat(trackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val rawSamples = ArrayList<Float>(65536)
        val info = MediaCodec.BufferInfo()
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val idx = codec.dequeueInputBuffer(10_000L)
                if (idx >= 0) {
                    val inBuf = codec.getInputBuffer(idx)!!
                    val size = extractor.readSampleData(inBuf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val outIdx = codec.dequeueOutputBuffer(info, 10_000L)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val fmt = codec.outputFormat
                    channelCount = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    pcmEncoding = if (fmt.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    else
                        AudioFormat.ENCODING_PCM_16BIT
                }
                else -> if (outIdx >= 0) {
                    appendSamples(
                        buf      = codec.getOutputBuffer(outIdx)!!,
                        size     = info.size,
                        encoding = pcmEncoding,
                        channels = channelCount,
                        out      = rawSamples,
                    )
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val samples = rawSamples.toFloatArray()
        normalize(samples)
        return samples
    }

    /**
     * Loads two samples for the given click type and beat mode:
     *  - first:  the beat sample  (clickType + beatMode)
     *  - second: the accent sample (clickType + BeatMode.ACCENTS)
     */
    fun loadClickPair(
        context: Context,
        clickType: ClickType,
        beatMode: BeatMode,
    ): Pair<FloatArray, FloatArray> {
        val beatResId   = clickType.sounds.getValue(beatMode)
        val accentResId = clickType.sounds.getValue(BeatMode.ACCENTS)
        return Pair(
            loadFromRaw(context, beatResId),
            loadFromRaw(context, accentResId),
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun appendSamples(
        buf: ByteBuffer,
        size: Int,
        encoding: Int,
        channels: Int,
        out: ArrayList<Float>,
    ) {
        buf.limit(size)
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val floats = FloatArray(size / Float.SIZE_BYTES)
            buf.asFloatBuffer().get(floats)
            downmixToMono(floats, channels, out)
        } else {
            // PCM_16BIT (default for most WAV / AAC decoders)
            val shorts = ShortArray(size / Short.SIZE_BYTES)
            buf.asShortBuffer().get(shorts)
            if (channels == 2) {
                var i = 0
                while (i + 1 < shorts.size) {
                    // combine and scale in one multiply to keep FP operations minimal
                    out.add((shorts[i] + shorts[i + 1]) * (0.5f / 32768f))
                    i += 2
                }
            } else {
                shorts.forEach { out.add(it / 32768f) }
            }
        }
    }

    private fun downmixToMono(floats: FloatArray, channels: Int, out: ArrayList<Float>) {
        if (channels == 2) {
            var i = 0
            while (i + 1 < floats.size) {
                out.add((floats[i] + floats[i + 1]) * 0.5f)
                i += 2
            }
        } else {
            floats.forEach { out.add(it) }
        }
    }

    private fun normalize(samples: FloatArray) {
        val peak = samples.fold(0f) { acc, v -> maxOf(acc, abs(v)) }
        if (peak > 0f) {
            val scale = 1f / peak
            for (i in samples.indices) samples[i] *= scale
        }
    }
}

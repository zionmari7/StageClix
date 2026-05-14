package com.stageclix.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stageclix.audio.AudioEngineJni
import com.stageclix.audio.SampleLoader
import com.stageclix.data.BeatMode
import com.stageclix.data.ClickType
import com.stageclix.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AudioEngineJni()

    companion object { private const val TAG = "StageClix" }

    private val _currentSong  = MutableStateFlow(Song())
    val currentSong: StateFlow<Song> = _currentSong.asStateFlow()

    private val _isPlaying    = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionBeats = MutableStateFlow(0.0)
    val positionBeats: StateFlow<Double> = _positionBeats.asStateFlow()

    private val tapTimes = ArrayDeque<Long>()
    private var sampleLoadJob: Job? = null

    init {
        Log.d(TAG, "init: calling engine.create()")
        engine.create()
        val started = engine.start(deviceId = -1)
        Log.d(TAG, "init: engine.start(-1) returned $started")
        reloadSamples(currentSong.value.clickType, currentSong.value.beatMode)
        startPositionPolling()
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    fun play() {
        Log.d(TAG, "play() called")
        engine.play()
        _isPlaying.value = true
    }

    fun pause() {
        engine.pause()
        _isPlaying.value = false
    }

    fun rewind() {
        engine.pause()
        engine.rewind()
        _isPlaying.value = false
        _positionBeats.value = 0.0
    }

    // ── Tap tempo ─────────────────────────────────────────────────────────────

    fun onTap() {
        val now = System.currentTimeMillis()
        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2_000L) {
            tapTimes.clear()
        }
        tapTimes.addLast(now)
        if (tapTimes.size > 8) tapTimes.removeFirst()

        if (tapTimes.size >= 2) {
            var totalInterval = 0L
            for (i in 1 until tapTimes.size) totalInterval += tapTimes[i] - tapTimes[i - 1]
            val avg = totalInterval.toDouble() / (tapTimes.size - 1)
            val bpm = (60_000.0 / avg * 10.0).roundToInt() / 10.0
            setBpm(bpm)
        }
    }

    // ── Song settings ─────────────────────────────────────────────────────────

    fun setBpm(bpm: Double) {
        _currentSong.value = _currentSong.value.copy(bpm = bpm)
        engine.setBpm(bpm)
    }

    fun setTimeSignature(n: Int, d: Int) {
        _currentSong.value = _currentSong.value.copy(
            timeSigNumerator   = n,
            timeSigDenominator = d,
        )
        engine.setTimeSignature(n, d)
    }

    fun setClickType(type: ClickType) {
        val song = _currentSong.value.copy(clickType = type)
        _currentSong.value = song
        reloadSamples(type, song.beatMode)
    }

    fun setBeatMode(mode: BeatMode) {
        val song = _currentSong.value.copy(beatMode = mode)
        _currentSong.value = song
        reloadSamples(song.clickType, mode)
    }

    // ── Mixer settings ────────────────────────────────────────────────────────

    fun setClickEnabled(enabled: Boolean) {
        _currentSong.value = _currentSong.value.copy(clickEnabled = enabled)
        engine.setClickEnabled(enabled)
    }

    fun setAccentEnabled(enabled: Boolean) {
        _currentSong.value = _currentSong.value.copy(accentEnabled = enabled)
        engine.setAccentEnabled(enabled)
    }

    fun setAccentVolume(vol: Float) {
        _currentSong.value = _currentSong.value.copy(accentVolume = vol)
        engine.setAccentVolume(vol)
    }

    fun setBeatEnabled(enabled: Boolean) {
        _currentSong.value = _currentSong.value.copy(beatEnabled = enabled)
        engine.setBeatEnabled(enabled)
    }

    fun setBeatVolume(vol: Float) {
        _currentSong.value = _currentSong.value.copy(beatVolume = vol)
        engine.setBeatVolume(vol)
    }

    fun setMasterVolume(vol: Float) {
        _currentSong.value = _currentSong.value.copy(masterVolume = vol)
        engine.setMasterVolume(vol)
    }

    fun setQuarterEnabled(enabled: Boolean) {
        _currentSong.value = _currentSong.value.copy(
            beatMixer = _currentSong.value.beatMixer.copy(quarterEnabled = enabled)
        )
        engine.setQuarterEnabled(enabled)
    }

    fun setQuarterVolume(vol: Float) {
        _currentSong.value = _currentSong.value.copy(
            beatMixer = _currentSong.value.beatMixer.copy(quarterVolume = vol)
        )
        engine.setQuarterVolume(vol)
    }

    fun setEighthEnabled(enabled: Boolean) {
        _currentSong.value = _currentSong.value.copy(
            beatMixer = _currentSong.value.beatMixer.copy(eighthEnabled = enabled)
        )
        engine.setEighthEnabled(enabled)
    }

    fun setEighthVolume(vol: Float) {
        _currentSong.value = _currentSong.value.copy(
            beatMixer = _currentSong.value.beatMixer.copy(eighthVolume = vol)
        )
        engine.setEighthVolume(vol)
    }

    fun setSixteenthEnabled(enabled: Boolean) {
        _currentSong.value = _currentSong.value.copy(
            beatMixer = _currentSong.value.beatMixer.copy(sixteenthEnabled = enabled)
        )
        engine.setSixteenthEnabled(enabled)
    }

    fun setSixteenthVolume(vol: Float) {
        _currentSong.value = _currentSong.value.copy(
            beatMixer = _currentSong.value.beatMixer.copy(sixteenthVolume = vol)
        )
        engine.setSixteenthVolume(vol)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        engine.stop()
        engine.destroy()
    }

    // ── Private ───────────────────────────────────────────────────────────────

    // Cancels any in-flight load so a rapid clickType/beatMode change never
    // applies stale samples over a newer result.
    private fun reloadSamples(clickType: ClickType, beatMode: BeatMode) {
        Log.d(TAG, "reloadSamples: clickType=$clickType beatMode=$beatMode")
        sampleLoadJob?.cancel()
        sampleLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val (beatSamples, accentSamples) =
                    SampleLoader.loadClickPair(getApplication(), clickType, beatMode)
                Log.d(TAG, "Loaded samples: beat=${beatSamples.size} accent=${accentSamples.size}")
                withContext(Dispatchers.Main) {
                    engine.setClickSample(beatSamples)
                    engine.setAccentSample(accentSamples)
                    Log.d(TAG, "Samples pushed to engine")
                }
            } catch (e: Exception) {
                Log.e(TAG, "reloadSamples failed", e)
            }
        }
    }

    private fun startPositionPolling() {
        viewModelScope.launch {
            while (true) {
                _positionBeats.value = engine.getPositionBeats()
                delay(16L)
            }
        }
    }
}

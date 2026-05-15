package com.stageclix.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stageclix.audio.*
import com.stageclix.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    // ── Engine ─────────────────────────────────────────
    private val engine = AudioEngineJni().also {
        it.create()
    }
    val usbManager = UsbAudioDeviceManager(app)
    private val dataStore = AppDataStore(app)

    // ── App state ───────────────────────────────────────
    private val _appData = MutableStateFlow(AppData())
    val appData: StateFlow<AppData> = _appData.asStateFlow()

    val activeSetlist: StateFlow<Setlist?> =
        _appData.map { data ->
            data.setlists.find { it.id == data.activeSetlistId }
                ?: data.setlists.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeSong: StateFlow<Song?> =
        combine(_appData, activeSetlist) { data, setlist ->
            setlist?.songs?.find { it.id == data.activeSongId }
                ?: setlist?.songs?.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── Transport ───────────────────────────────────────
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionBeats = MutableStateFlow(0.0)
    val positionBeats: StateFlow<Double> = _positionBeats.asStateFlow()

    // ── USB ─────────────────────────────────────────────
    val selectedUsb = usbManager.selectedDevice
    val usbDevices = usbManager.connectedDevices

    // ── Tap tempo ───────────────────────────────────────
    private val tapTimes = mutableListOf<Long>()

    init {
        // Load saved data
        viewModelScope.launch {
            dataStore.appDataFlow.collect { data ->
                _appData.value = data
            }
        }

        // Start engine
        engine.start(-1)

        // Start USB manager
        usbManager.start()

        // Restart engine on USB change
        viewModelScope.launch {
            usbManager.selectedDevice.collect { dev ->
                if (dev != null) {
                    engine.stop()
                    engine.start(dev.deviceId)
                }
            }
        }

        // Load voice cues
        viewModelScope.launch(Dispatchers.IO) {
            VoiceCueLoader.loadAll(app, engine)
        }

        // Load default click samples so beat-builder audition works immediately
        loadClickSamples(ClickType.WOODBLOCK)

        // Poll position
        viewModelScope.launch {
            while (isActive) {
                _positionBeats.value = engine.getPositionBeats()
                delay(16)
            }
        }

        // Apply song to engine when active song changes
        viewModelScope.launch {
            activeSong.collect { song ->
                song?.let { applySongToEngine(it) }
            }
        }
    }

    // ── Transport ───────────────────────────────────────

    fun play() {
        engine.play()
        _isPlaying.value = true
    }

    fun pause() {
        engine.pause()
        _isPlaying.value = false
    }

    fun rewind() {
        engine.rewind()
        _isPlaying.value = false
        _positionBeats.value = 0.0
    }

    fun onTap() {
        val now = System.currentTimeMillis()
        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2000) {
            tapTimes.clear()
        }
        tapTimes.add(now)
        if (tapTimes.size >= 2) {
            val avg = tapTimes.zipWithNext()
                .map { (a, b) -> b - a }
                .average()
            val bpm = (60000.0 / avg).coerceIn(20.0, 300.0)
            updateActiveSong { it.copy(bpm = Math.round(bpm * 10) / 10.0) }
            engine.setBpm(bpm)
        }
    }

    // ── Song / Setlist management ───────────────────────

    fun selectSetlist(setlistId: String) {
        updateAppData { data ->
            val setlist = data.setlists.find { it.id == setlistId }
            data.copy(
                activeSetlistId = setlistId,
                activeSongId = setlist?.songs?.firstOrNull()?.id ?: ""
            )
        }
    }

    fun selectSong(songId: String) {
        rewind()
        updateAppData { it.copy(activeSongId = songId) }
        val song = _appData.value.setlists
            .flatMap { it.songs }
            .find { it.id == songId }
        song?.tracks
            ?.find { it.kind == TrackKind.CLICK }
            ?.clickClips
            ?.firstOrNull()
            ?.let { applyBeatPatternToEngine(it.pattern) }
    }

    fun addSetlist(name: String) {
        val newSetlist = Setlist(
            name = name,
            songs = listOf(Song(name = "Song 1"))
        )
        updateAppData { data ->
            data.copy(
                setlists = data.setlists + newSetlist,
                activeSetlistId = newSetlist.id,
                activeSongId = newSetlist.songs.first().id
            )
        }
    }

    fun renameSetlist(setlistId: String, name: String) {
        updateSetlist(setlistId) { it.copy(name = name) }
    }

    fun deleteSetlist(setlistId: String) {
        updateAppData { data ->
            data.copy(setlists = data.setlists.filter { it.id != setlistId })
        }
    }

    fun addSong(name: String) {
        val newSong = Song(name = name)
        updateActiveSetlist { setlist ->
            setlist.copy(songs = setlist.songs + newSong)
        }
        updateAppData { it.copy(activeSongId = newSong.id) }
    }

    fun renameSong(songId: String, name: String) {
        updateSong(songId) { it.copy(name = name) }
    }

    fun deleteSong(songId: String) {
        updateActiveSetlist { setlist ->
            setlist.copy(songs = setlist.songs.filter { it.id != songId })
        }
    }

    fun reorderSongs(songs: List<Song>) {
        updateActiveSetlist { it.copy(songs = songs) }
    }

    // ── Song settings ───────────────────────────────────

    fun setBpm(bpm: Double) {
        updateActiveSong { it.copy(bpm = bpm) }
        engine.setBpm(bpm)
    }

    fun setTimeSignature(n: Int, d: Int) {
        updateActiveSong { it.copy(timeSigNumerator = n, timeSigDenominator = d) }
        engine.setTimeSignature(n, d)
    }

    // ── Beat audition ───────────────────────────────────

    fun auditNote(row: Int) {
        engine.auditNote(row, 0)
    }

    // ── Click clips ─────────────────────────────────────

    fun addClickClip(clip: ClickClip) {
        Log.d("StageClix", "addClickClip: startBar=${clip.startBar} duration=${clip.durationBars} cells=${clip.pattern.cells.size}")
        updateClickTrack { track ->
            track.copy(clickClips = track.clickClips + clip)
        }
        applyBeatPatternToEngine(clip.pattern)
        loadClickSamples(clip.pattern.clickType)
    }

    fun loadClickSamples(clickType: ClickType) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val (beat, accent) = SampleLoader.loadClickPair(
                    getApplication(), clickType, BeatMode.QUARTER,
                )
                engine.setClickSample(beat)
                engine.setAccentSample(accent)
                Log.d("StageClix", "Click samples loaded for $clickType")
            }.onFailure { e ->
                Log.e("StageClix", "Failed to load click samples for $clickType", e)
            }
        }
    }

    fun updateClickClip(clip: ClickClip) {
        updateClickTrack { track ->
            track.copy(clickClips = track.clickClips.map {
                if (it.id == clip.id) clip else it
            })
        }
        applyBeatPatternToEngine(clip.pattern)
    }

    fun removeClickClip(clipId: String) {
        updateClickTrack { track ->
            track.copy(clickClips = track.clickClips.filter { it.id != clipId })
        }
    }

    // ── Voice cue clips ─────────────────────────────────

    fun addVoiceCueClip(clip: VoiceCueClip) {
        updateVoiceCueTrack { track ->
            track.copy(voiceCueClips = track.voiceCueClips + clip)
        }
    }

    fun removeVoiceCueClip(clipId: String) {
        updateVoiceCueTrack { track ->
            track.copy(voiceCueClips = track.voiceCueClips.filter { it.id != clipId })
        }
    }

    // ── Beat mixer ──────────────────────────────────────

    fun setAccentEnabled(enabled: Boolean) {
        engine.setAccentEnabled(enabled)
        updateBeatMixer { it.copy(accentEnabled = enabled) }
    }

    fun setAccentVolume(vol: Float) {
        engine.setAccentVolume(vol)
        updateBeatMixer { it.copy(accentVolume = vol) }
    }

    fun setQuarterEnabled(enabled: Boolean) {
        engine.setQuarterEnabled(enabled)
        updateBeatMixer { it.copy(quarterEnabled = enabled) }
    }

    fun setQuarterVolume(vol: Float) {
        engine.setQuarterVolume(vol)
        updateBeatMixer { it.copy(quarterVolume = vol) }
    }

    fun setEighthEnabled(enabled: Boolean) {
        engine.setEighthEnabled(enabled)
        updateBeatMixer { it.copy(eighthEnabled = enabled) }
    }

    fun setEighthVolume(vol: Float) {
        engine.setEighthVolume(vol)
        updateBeatMixer { it.copy(eighthVolume = vol) }
    }

    fun setSixteenthEnabled(enabled: Boolean) {
        engine.setSixteenthEnabled(enabled)
        updateBeatMixer { it.copy(sixteenthEnabled = enabled) }
    }

    fun setSixteenthVolume(vol: Float) {
        engine.setSixteenthVolume(vol)
        updateBeatMixer { it.copy(sixteenthVolume = vol) }
    }

    fun setMasterVolume(vol: Float) {
        engine.setMasterVolume(vol)
        updateBeatMixer { it.copy(masterVolume = vol) }
    }

    // ── Engine apply ────────────────────────────────────

    private fun applySongToEngine(song: Song) {
        engine.setBpm(song.bpm)
        engine.setTimeSignature(song.timeSigNumerator, song.timeSigDenominator)
        with(song.beatMixer) {
            engine.setAccentEnabled(accentEnabled)
            engine.setAccentVolume(accentVolume)
            engine.setQuarterEnabled(quarterEnabled)
            engine.setQuarterVolume(quarterVolume)
            engine.setEighthEnabled(eighthEnabled)
            engine.setEighthVolume(eighthVolume)
            engine.setSixteenthEnabled(sixteenthEnabled)
            engine.setSixteenthVolume(sixteenthVolume)
            engine.setMasterVolume(masterVolume)
        }
        song.tracks
            .find { it.kind == TrackKind.CLICK }
            ?.clickClips
            ?.firstOrNull()
            ?.let { clip ->
                applyBeatPatternToEngine(clip.pattern)
                loadClickSamples(clip.pattern.clickType)
            }
    }

    fun applyBeatPatternToEngine(pattern: BeatPattern) {
        val beats = pattern.cells.map { it.beatIndex }.toIntArray()
        val rows = pattern.cells.map { it.row.ordinal }.toIntArray()
        val subs = pattern.cells.map { it.subIndex }.toIntArray()
        engine.setBeatPattern(
            beats,
            rows,
            subs,
            pattern.timeSigNumerator,
            pattern.bpm
        )
    }

    // ── Helpers ─────────────────────────────────────────

    private fun updateAppData(transform: (AppData) -> AppData) {
        _appData.update(transform)
        viewModelScope.launch {
            dataStore.saveAppData(_appData.value)
        }
    }

    private fun updateSetlist(setlistId: String, transform: (Setlist) -> Setlist) {
        updateAppData { data ->
            data.copy(setlists = data.setlists.map {
                if (it.id == setlistId) transform(it) else it
            })
        }
    }

    private fun updateActiveSetlist(transform: (Setlist) -> Setlist) {
        val id = _appData.value.activeSetlistId
            .ifEmpty { _appData.value.setlists.firstOrNull()?.id ?: "" }
        updateSetlist(id, transform)
    }

    private fun updateSong(songId: String, transform: (Song) -> Song) {
        updateActiveSetlist { setlist ->
            setlist.copy(songs = setlist.songs.map {
                if (it.id == songId) transform(it) else it
            })
        }
    }

    private fun updateActiveSong(transform: (Song) -> Song) {
        val data = _appData.value
        val setlistId = data.activeSetlistId
            .ifEmpty { data.setlists.firstOrNull()?.id ?: "" }
        val songs = data.setlists.find { it.id == setlistId }?.songs ?: emptyList()
        val id = data.activeSongId.ifEmpty { songs.firstOrNull()?.id ?: "" }
        updateSong(id, transform)
    }

    private fun updateClickTrack(transform: (Track) -> Track) {
        updateActiveSong { song ->
            if (song.tracks.any { it.kind == TrackKind.CLICK }) {
                song.copy(tracks = song.tracks.map {
                    if (it.kind == TrackKind.CLICK) transform(it) else it
                })
            } else {
                Log.d("StageClix", "No CLICK track found — creating one")
                song.copy(tracks = song.tracks + transform(Track(kind = TrackKind.CLICK, name = "Click")))
            }
        }
    }

    private fun updateVoiceCueTrack(transform: (Track) -> Track) {
        updateActiveSong { song ->
            song.copy(tracks = song.tracks.map {
                if (it.kind == TrackKind.VOICE_CUE) transform(it) else it
            })
        }
    }

    private fun updateBeatMixer(transform: (BeatMixerState) -> BeatMixerState) {
        updateActiveSong { song ->
            song.copy(beatMixer = transform(song.beatMixer))
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.pause()
        engine.stop()
        engine.destroy()
        usbManager.stop()
    }
}

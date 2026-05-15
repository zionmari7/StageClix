package com.stageclix.data

import com.example.stageclix.R
import kotlinx.serialization.Serializable
import java.util.UUID

// ── Beat grid ─────────────────────────────────────────

/**
 * Represents one cell in the beat builder grid.
 * Each cell has a position: row (ACC/QTR/8TH/16TH)
 * and a subdivision index within that row.
 *
 * ACC row: 1 cell per beat  → subIndex always 0
 * QTR row: 1 cell per beat  → subIndex always 0
 * 8TH row: 2 cells per beat → subIndex 0 or 1
 * 16TH row: 4 cells per beat → subIndex 0,1,2,3
 */
enum class NoteRow { ACC, QTR, EIGHTH, SIXTEENTH }

@Serializable
data class BeatCell(
    val beatIndex: Int,    // 0-based beat in bar (0=beat1)
    val row: NoteRow,
    val subIndex: Int = 0, // subdivision within beat
    val enabled: Boolean = true,
)

/**
 * A custom 1-bar beat pattern built in the beat builder.
 * The engine loops this pattern for however many bars
 * the clip covers on the timeline.
 */
@Serializable
data class BeatPattern(
    val id: String = UUID.randomUUID().toString(),
    val cells: List<BeatCell> = emptyList(),
    val clickType: ClickType = ClickType.WOODBLOCK,
    val timeSigNumerator: Int = 4,
    val timeSigDenominator: Int = 4,
    val bpm: Double = 120.0,
)

// ── Timeline clips ────────────────────────────────────

@Serializable
data class ClickClip(
    val id: String = UUID.randomUUID().toString(),
    val startBar: Int = 0,      // 0-based bar on timeline
    val durationBars: Int = 8,  // draggable right edge
    val pattern: BeatPattern = BeatPattern(),
)

@Serializable
data class VoiceCueClip(
    val id: String = UUID.randomUUID().toString(),
    val startBar: Int = 0,
    val voiceCue: VoiceCue = VoiceCue.VC_INTRO,
    val label: String = "",
)

@Serializable
data class BackingClip(
    val id: String = UUID.randomUUID().toString(),
    val startBar: Int = 0,
    val durationBars: Int = 8,
    val fileUri: String = "",
    val fileName: String = "",
    val engineIndex: Int = -1,
)

// ── Track ─────────────────────────────────────────────

@Serializable
data class Track(
    val id: String = UUID.randomUUID().toString(),
    val kind: TrackKind,
    val name: String,
    val volume: Float = 1.0f,
    val muted: Boolean = false,
    val clickClips: List<ClickClip> = emptyList(),
    val voiceCueClips: List<VoiceCueClip> = emptyList(),
    val backingClips: List<BackingClip> = emptyList(),
)

enum class TrackKind { CLICK, VOICE_CUE, BACKING }

// ── Song ──────────────────────────────────────────────

@Serializable
data class Song(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New Song",
    val bpm: Double = 120.0,
    val timeSigNumerator: Int = 4,
    val timeSigDenominator: Int = 4,
    val totalBars: Int = 32,
    val tracks: List<Track> = listOf(
        Track(
            kind = TrackKind.CLICK,
            name = "Click",
        ),
        Track(
            kind = TrackKind.VOICE_CUE,
            name = "Cues",
        ),
        Track(
            kind = TrackKind.BACKING,
            name = "Backing",
        ),
    ),
    val beatMixer: BeatMixerState = BeatMixerState(),
    val voiceCueVolume: Float = 1.0f,
    val voiceCueMuted: Boolean = false,
)

// ── Setlist ───────────────────────────────────────────

@Serializable
data class Setlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New Setlist",
    val songs: List<Song> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis(),
)

@Serializable
data class AppData(
    val setlists: List<Setlist> = emptyList(),
    val activeSetlistId: String = "",
    val activeSongId: String = "",
)

// ── Existing types (unchanged) ────────────────────────

enum class BeatMode(val displayName: String) {
    QUARTER("Quarter"),
    EIGHTH("Eighth"),
    SIXTEENTH("Sixteenth"),
    ACCENTS("Accents"),
}

enum class ClickType(val displayName: String, val sounds: Map<BeatMode, Int>) {
    BLIP("Blip", mapOf(
        BeatMode.QUARTER   to R.raw.blip_quarter,
        BeatMode.EIGHTH    to R.raw.blip_eighth,
        BeatMode.SIXTEENTH to R.raw.blip_sixteenth,
        BeatMode.ACCENTS   to R.raw.blip_accents,
    )),
    CLASSIC("Classic", mapOf(
        BeatMode.QUARTER   to R.raw.classic_quarter,
        BeatMode.EIGHTH    to R.raw.classic_eighth,
        BeatMode.SIXTEENTH to R.raw.classic_sixteenth,
        BeatMode.ACCENTS   to R.raw.classic_accents,
    )),
    COWBELL("Cowbell", mapOf(
        BeatMode.QUARTER   to R.raw.cowbell_quarter,
        BeatMode.EIGHTH    to R.raw.cowbell_eighth,
        BeatMode.SIXTEENTH to R.raw.cowbell_sixteenth,
        BeatMode.ACCENTS   to R.raw.cowbell_accents,
    )),
    GENTLE("Gentle", mapOf(
        BeatMode.QUARTER   to R.raw.gentle_quarter,
        BeatMode.EIGHTH    to R.raw.gentle_eighth,
        BeatMode.SIXTEENTH to R.raw.gentle_sixteenth,
        BeatMode.ACCENTS   to R.raw.gentle_accents,
    )),
    PERCUSSIVE("Percussive", mapOf(
        BeatMode.QUARTER   to R.raw.percussive_quarter,
        BeatMode.EIGHTH    to R.raw.percussive_eighth,
        BeatMode.SIXTEENTH to R.raw.percussive_sixteenth,
        BeatMode.ACCENTS   to R.raw.percussive_accents,
    )),
    WOODBLOCK("Woodblock", mapOf(
        BeatMode.QUARTER   to R.raw.woodblock_quarter,
        BeatMode.EIGHTH    to R.raw.woodblock_eighth,
        BeatMode.SIXTEENTH to R.raw.woodblock_sixteenth,
        BeatMode.ACCENTS   to R.raw.woodblock_accents,
    )),
}

@Serializable
data class BeatMixerState(
    val accentEnabled: Boolean    = true,
    val accentVolume: Float       = 1.0f,
    val quarterEnabled: Boolean   = true,
    val quarterVolume: Float      = 0.85f,
    val eighthEnabled: Boolean    = false,
    val eighthVolume: Float       = 0.55f,
    val sixteenthEnabled: Boolean = false,
    val sixteenthVolume: Float    = 0.35f,
    val masterVolume: Float       = 0.8f,
)

enum class VoiceCue(val displayName: String, val rawResId: Int) {
    VC_1("1", R.raw.vc_1),
    VC_2("2", R.raw.vc_2),
    VC_3("3", R.raw.vc_3),
    VC_4("4", R.raw.vc_4),
    VC_5("5", R.raw.vc_5),
    VC_6("6", R.raw.vc_6),
    VC_7("7", R.raw.vc_7),
    VC_ACAPELLA("Acapella", R.raw.vc_acapella),
    VC_AD_LIB("Ad Lib", R.raw.vc_ad_lib),
    VC_ALL_IN("All In", R.raw.vc_all_in),
    VC_BASS("Bass", R.raw.vc_bass),
    VC_BIG_ENDING("Big Ending", R.raw.vc_big_ending),
    VC_BREAK("Break", R.raw.vc_break),
    VC_BREAKDOWN("Breakdown", R.raw.vc_breakdown),
    VC_BRIDGE("Bridge", R.raw.vc_bridge),
    VC_BRIDGE_1("Bridge 1", R.raw.vc_bridge_1),
    VC_BRIDGE_2("Bridge 2", R.raw.vc_bridge_2),
    VC_BRIDGE_3("Bridge 3", R.raw.vc_bridge_3),
    VC_BRIDGE_4("Bridge 4", R.raw.vc_bridge_4),
    VC_BUILD("Build", R.raw.vc_build),
    VC_CHORUS("Chorus", R.raw.vc_chorus),
    VC_CHORUS_1("Chorus 1", R.raw.vc_chorus_1),
    VC_CHORUS_2("Chorus 2", R.raw.vc_chorus_2),
    VC_CHORUS_3("Chorus 3", R.raw.vc_chorus_3),
    VC_CHORUS_4("Chorus 4", R.raw.vc_chorus_4),
    VC_DRUMS("Drums", R.raw.vc_drums),
    VC_DRUMS_IN("Drums In", R.raw.vc_drums_in),
    VC_ENDING("Ending", R.raw.vc_ending),
    VC_EXHORTATION("Exhortation", R.raw.vc_exhortation),
    VC_HITS("Hits", R.raw.vc_hits),
    VC_HOLD("Hold", R.raw.vc_hold),
    VC_INSTRUMENTAL("Instrumental", R.raw.vc_instrumental),
    VC_INTERLUDE("Interlude", R.raw.vc_interlude),
    VC_INTRO("Intro", R.raw.vc_intro),
    VC_KEYS("Keys", R.raw.vc_keys),
    VC_KEY_CHANGE_DOWN("Key Change Down", R.raw.vc_key_change_down),
    VC_KEY_CHANGE_UP("Key Change Up", R.raw.vc_key_change_up),
    VC_LAST_TIME("Last Time", R.raw.vc_last_time),
    VC_OUTRO("Outro", R.raw.vc_outro),
    VC_POST_CHORUS("Post Chorus", R.raw.vc_post_chorus),
    VC_PRE_CHORUS("Pre Chorus", R.raw.vc_pre_chorus),
    VC_PRE_CHORUS_1("Pre Chorus 1", R.raw.vc_pre_chorus_1),
    VC_PRE_CHORUS_2("Pre Chorus 2", R.raw.vc_pre_chorus_2),
    VC_PRE_CHORUS_3("Pre Chorus 3", R.raw.vc_pre_chorus_3),
    VC_PRE_CHORUS_4("Pre Chorus 4", R.raw.vc_pre_chorus_4),
    VC_RAP("Rap", R.raw.vc_rap),
    VC_REFRAIN("Refrain", R.raw.vc_refrain),
    VC_SLOWLY_BUILD("Slowly Build", R.raw.vc_slowly_build),
    VC_SOFTLY("Softly", R.raw.vc_softly),
    VC_SOLO("Solo", R.raw.vc_solo),
    VC_SWELL("Swell", R.raw.vc_swell),
    VC_TAG("Tag", R.raw.vc_tag),
    VC_TURNAROUND("Turnaround", R.raw.vc_turnaround),
    VC_VAMP("Vamp", R.raw.vc_vamp),
    VC_VERSE("Verse", R.raw.vc_verse),
    VC_VERSE_1("Verse 1", R.raw.vc_verse_1),
    VC_VERSE_2("Verse 2", R.raw.vc_verse_2),
    VC_VERSE_3("Verse 3", R.raw.vc_verse_3),
    VC_VERSE_4("Verse 4", R.raw.vc_verse_4),
    VC_VERSE_5("Verse 5", R.raw.vc_verse_5),
    VC_VERSE_6("Verse 6", R.raw.vc_verse_6),
    VC_WORSHIP_FREELY("Worship Freely", R.raw.vc_worship_freely),
}

data class VoiceCueEvent(
    val id: String = UUID.randomUUID().toString(),
    val voiceCue: VoiceCue,
    val triggerBar: Int,
    val label: String = voiceCue.displayName,
)

package com.stageclix.data

import com.example.stageclix.R
import java.util.UUID

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

data class BeatMixerState(
    val accentEnabled: Boolean = true,
    val accentVolume: Float    = 1.0f,
    val beatEnabled: Boolean   = true,
    val beatVolume: Float      = 0.85f,
    val masterVolume: Float    = 1.0f,
)

data class Song(
    val id: String              = UUID.randomUUID().toString(),
    val bpm: Double             = 120.0,
    val timeSigNumerator: Int   = 4,
    val timeSigDenominator: Int = 4,
    val clickType: ClickType    = ClickType.CLASSIC,
    val beatMode: BeatMode      = BeatMode.QUARTER,
    val clickEnabled: Boolean   = true,
    val accentEnabled: Boolean  = true,
    val accentVolume: Float     = 1.0f,
    val beatEnabled: Boolean    = true,
    val beatVolume: Float       = 0.85f,
    val masterVolume: Float     = 1.0f,
)

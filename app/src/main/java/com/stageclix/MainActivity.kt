package com.stageclix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stageclix.data.BeatMode
import com.stageclix.data.ClickType
import com.stageclix.data.Song
import com.stageclix.viewmodel.PlayerViewModel
import kotlin.math.roundToInt

// ── Palette ───────────────────────────────────────────────────────────────────

private val Bg          = Color(0xFF1E1E1E)
private val BgSurface   = Color(0xFF252525)
private val BgDark      = Color(0xFF1A1A1A)
private val BgPlay      = Color(0xFF1A5C1A)
private val BgTap       = Color(0xFF2A2A3A)
private val BgChipOff   = Color(0xFF2A2A2A)
private val BgChipGreen = Color(0xFF1A3A1A)
private val BgChipBlue  = Color(0xFF1A2A3A)
private val BdrDark     = Color(0xFF111111)
private val BdrMid      = Color(0xFF2A2A2A)
private val BdrLight    = Color(0xFF3A3A3A)
private val BdrMuted    = Color(0xFF444444)
private val BdrGreen    = Color(0xFF2AB02A)
private val BdrBlue     = Color(0xFF3A7BD5)
private val BdrTap      = Color(0xFF3A3AD5)
private val TextAmber   = Color(0xFFE8A020)
private val TextGreen   = Color(0xFF40E040)
private val TextBlue    = Color(0xFF7AB3F5)
private val TextMuted   = Color(0xFF888888)
private val TextSection = Color(0xFF666666)

private val TIME_SIGS = listOf(2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 8, 7 to 8)

// ── Activity ──────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MetronomeScreen(onBack = { finish() })
        }
    }
}

// ── Root screen ───────────────────────────────────────────────────────────────

@Composable
fun MetronomeScreen(
    vm: PlayerViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    val song          by vm.currentSong.collectAsState()
    val isPlaying     by vm.isPlaying.collectAsState()
    val positionBeats by vm.positionBeats.collectAsState()

    var showBpmDialog by remember { mutableStateOf(false) }

    if (showBpmDialog) {
        BpmInputDialog(
            current   = song.bpm,
            onConfirm = { vm.setBpm(it); showBpmDialog = false },
            onDismiss = { showBpmDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        TransportBar(
            song          = song,
            isPlaying     = isPlaying,
            positionBeats = positionBeats,
            onBack        = onBack,
            onPlayPause   = { if (isPlaying) vm.pause() else vm.play() },
            onStop        = { vm.pause(); vm.rewind() },
            onTap         = { vm.onTap() },
            onBpmClick    = { showBpmDialog = true },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ClickToggleRow(
                enabled  = song.clickEnabled,
                onToggle = { vm.setClickEnabled(it) },
            )

            Column(
                modifier = Modifier.alpha(if (song.clickEnabled) 1f else 0.3f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ClickTypeSection(
                    selected = song.clickType,
                    onSelect = { vm.setClickType(it) },
                )
                BeatModeSection(
                    selected = song.beatMode,
                    onSelect = { vm.setBeatMode(it) },
                )
                TimeSigSection(
                    numerator   = song.timeSigNumerator,
                    denominator = song.timeSigDenominator,
                    onSelect    = { n, d -> vm.setTimeSignature(n, d) },
                )
                BeatMixerSection(
                    song           = song,
                    clickEnabled   = song.clickEnabled,
                    onAccentToggle = { vm.setAccentEnabled(it) },
                    onAccentVolume = { vm.setAccentVolume(it) },
                    onBeatToggle   = { vm.setBeatEnabled(it) },
                    onBeatVolume   = { vm.setBeatVolume(it) },
                    onMasterVolume = { vm.setMasterVolume(it) },
                )
            }
        }
    }
}

// ── Transport Bar ─────────────────────────────────────────────────────────────

@Composable
private fun TransportBar(
    song: Song,
    isPlaying: Boolean,
    positionBeats: Double,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onTap: () -> Unit,
    onBpmClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(BgSurface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back  32 × 48
            TapCell(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight()
                    .background(BdrLight),
                onClick = onBack,
            ) {
                Icon(
                    imageVector        = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint               = TextMuted,
                    modifier           = Modifier.size(16.dp),
                )
            }

            // Play / Pause  52 × 48
            TapCell(
                modifier = Modifier
                    .width(52.dp)
                    .fillMaxHeight()
                    .background(BgPlay)
                    .border(1.5.dp, BdrGreen),
                onClick = onPlayPause,
            ) {
                Icon(
                    imageVector        = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint               = TextGreen,
                    modifier           = Modifier.size(24.dp),
                )
            }

            // Stop (rewind)  44 × 48
            TapCell(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(BdrLight)
                    .border(1.dp, Color(0xFF555555)),
                onClick = onStop,
            ) {
                Icon(
                    imageVector        = Icons.Filled.Stop,
                    contentDescription = "Stop",
                    tint               = Color.White,
                    modifier           = Modifier.size(20.dp),
                )
            }

            // TAP  48 × 48
            TapCell(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(BgTap)
                    .border(1.dp, BdrTap),
                onClick = onTap,
            ) {
                Text("TAP", color = Color(0xFF8888DD), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            // BPM — tappable, opens dialog
            TapCell(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .background(BgDark)
                    .border(1.dp, BdrMuted),
                onClick = onBpmClick,
            ) {
                Text(
                    text       = "%.1f".format(song.bpm),
                    color      = TextAmber,
                    fontSize   = 16.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Time signature
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .background(BgDark)
                    .border(1.dp, BdrMuted),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = "${song.timeSigNumerator} / ${song.timeSigDenominator}",
                    color      = Color.White,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Position
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .background(BgDark)
                    .border(1.dp, BdrMuted),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = formatPosition(positionBeats, song.timeSigNumerator),
                    color      = TextAmber,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = BdrDark)
    }
}

@Composable
private fun TapCell(
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) = Box(
    modifier         = modifier.clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
    content          = content,
)

// ── Click Toggle Row ──────────────────────────────────────────────────────────

@Composable
private fun ClickToggleRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text          = "CLICK",
            color         = if (enabled) TextGreen else TextMuted,
            fontSize      = 12.sp,
            letterSpacing = 1.5.sp,
        )
        Switch(
            checked         = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = TextGreen,
                checkedTrackColor   = Color(0xFF1A4A1A),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = BdrLight,
            ),
        )
    }
}

// ── Click Type ────────────────────────────────────────────────────────────────

@Composable
private fun ClickTypeSection(selected: ClickType, onSelect: (ClickType) -> Unit) {
    SectionLabel("CLICK TYPE")
    Spacer(Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(ClickType.entries) { type ->
            val sel = type == selected
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(34.dp)
                    .background(if (sel) BgChipGreen else BgChipOff, RoundedCornerShape(4.dp))
                    .border(1.dp, if (sel) BdrGreen else BdrLight, RoundedCornerShape(4.dp))
                    .clickable { onSelect(type) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text     = type.displayName,
                    color    = if (sel) TextGreen else TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ── Beat Mode ─────────────────────────────────────────────────────────────────

@Composable
private fun BeatModeSection(selected: BeatMode, onSelect: (BeatMode) -> Unit) {
    SectionLabel("BEAT MODE")
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        BeatMode.entries.forEach { mode ->
            val sel = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(if (sel) BgChipBlue else BgChipOff, RoundedCornerShape(4.dp))
                    .border(1.dp, if (sel) BdrBlue else BdrLight, RoundedCornerShape(4.dp))
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text     = mode.displayName,
                    color    = if (sel) TextBlue else TextMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

// ── Time Signature ────────────────────────────────────────────────────────────

@Composable
private fun TimeSigSection(numerator: Int, denominator: Int, onSelect: (Int, Int) -> Unit) {
    SectionLabel("TIME SIGNATURE")
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        TIME_SIGS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { (n, d) ->
                    val sel = n == numerator && d == denominator
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(if (sel) BgChipBlue else BgChipOff, RoundedCornerShape(4.dp))
                            .border(1.dp, if (sel) BdrBlue else BdrLight, RoundedCornerShape(4.dp))
                            .clickable { onSelect(n, d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text       = "$n / $d",
                            color      = if (sel) TextBlue else TextMuted,
                            fontSize   = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

// ── Beat Mixer ────────────────────────────────────────────────────────────────

@Composable
private fun BeatMixerSection(
    song: Song,
    clickEnabled: Boolean,
    onAccentToggle: (Boolean) -> Unit,
    onAccentVolume: (Float) -> Unit,
    onBeatToggle: (Boolean) -> Unit,
    onBeatVolume: (Float) -> Unit,
    onMasterVolume: (Float) -> Unit,
) {
    SectionLabel("BEAT MIXER")
    Spacer(Modifier.height(6.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = BgSurface),
        border   = BorderStroke(1.dp, BdrMid),
        shape    = RoundedCornerShape(6.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MixerRow(
                label        = "Accent",
                labelColor   = TextGreen,
                toggleState  = song.accentEnabled,
                onToggle     = onAccentToggle,
                volume       = song.accentVolume,
                onVolume     = onAccentVolume,
                rowEnabled   = clickEnabled,
            )

            HorizontalDivider(thickness = 0.5.dp, color = BdrMid)

            MixerRow(
                label       = "Beat",
                labelColor  = TextBlue,
                toggleState = song.beatEnabled,
                onToggle    = onBeatToggle,
                volume      = song.beatVolume,
                onVolume    = onBeatVolume,
                rowEnabled  = clickEnabled && song.beatMode != BeatMode.ACCENTS,
                alpha       = if (song.beatMode == BeatMode.ACCENTS) 0.3f else 1f,
            )

            HorizontalDivider(thickness = 0.5.dp, color = BdrMid)

            MixerRow(
                label       = "Master",
                labelColor  = TextMuted,
                toggleState = null,
                onToggle    = null,
                volume      = song.masterVolume,
                onVolume    = onMasterVolume,
                rowEnabled  = clickEnabled,
                valueColor  = Color.White,
            )
        }
    }
}

@Composable
private fun MixerRow(
    label: String,
    labelColor: Color,
    toggleState: Boolean?,
    onToggle: ((Boolean) -> Unit)?,
    volume: Float,
    onVolume: (Float) -> Unit,
    rowEnabled: Boolean,
    alpha: Float = 1f,
    valueColor: Color = labelColor,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            color    = labelColor,
            fontSize = 12.sp,
            modifier = Modifier.width(48.dp),
        )

        if (toggleState != null && onToggle != null) {
            Switch(
                checked         = toggleState,
                onCheckedChange = onToggle,
                enabled         = rowEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = labelColor,
                    checkedTrackColor   = labelColor.copy(alpha = 0.25f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = BdrLight,
                ),
            )
        } else {
            // Spacer keeps the slider start-edge aligned with the toggled rows
            Spacer(Modifier.width(52.dp))
        }

        Slider(
            value         = volume,
            onValueChange = onVolume,
            enabled       = rowEnabled && (toggleState != false),
            modifier      = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor       = labelColor,
                activeTrackColor = labelColor,
            ),
        )

        Text(
            text       = "${(volume * 100).roundToInt()}%",
            color      = valueColor,
            fontSize   = 11.sp,
            fontFamily = FontFamily.Monospace,
            textAlign  = TextAlign.End,
            modifier   = Modifier.width(36.dp),
        )
    }
}

// ── BPM dialog ────────────────────────────────────────────────────────────────

@Composable
private fun BpmInputDialog(
    current: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("%.1f".format(current)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgSurface,
        title = { Text("Set BPM", color = Color.White) },
        text  = {
            OutlinedTextField(
                value           = text,
                onValueChange   = { text = it },
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = TextAmber,
                    unfocusedBorderColor = BdrMuted,
                    focusedTextColor     = TextAmber,
                    unfocusedTextColor   = TextAmber,
                    cursorColor          = TextAmber,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                text.toDoubleOrNull()?.coerceIn(20.0, 400.0)?.let(onConfirm) ?: onDismiss()
            }) { Text("OK", color = TextAmber) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
    )
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text          = text,
        color         = TextSection,
        fontSize      = 9.sp,
        letterSpacing = 1.sp,
    )
}

private fun formatPosition(beats: Double, numerator: Int): String {
    if (numerator <= 0) return "1 : 1 : 00"
    val bar       = (beats / numerator).toInt() + 1
    val beatInBar = (beats % numerator).toInt() + 1
    val sub       = ((beats % 1.0) * 100).roundToInt().coerceIn(0, 99)
    return "%d : %d : %02d".format(bar, beatInBar, sub)
}

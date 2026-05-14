@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.stageclix

import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    ControlDropdownRow(
                        song        = song,
                        onClickType = { vm.setClickType(it) },
                        onBeatMode  = { vm.setBeatMode(it) },
                        onTimeSig   = { n, d -> vm.setTimeSignature(n, d) },
                    )
                    BeatMixerSection(
                        song              = song,
                        onAccentToggle    = { vm.setAccentEnabled(it) },
                        onAccentVolume    = { vm.setAccentVolume(it) },
                        onQuarterToggle   = { vm.setQuarterEnabled(it) },
                        onQuarterVolume   = { vm.setQuarterVolume(it) },
                        onEighthToggle    = { vm.setEighthEnabled(it) },
                        onEighthVolume    = { vm.setEighthVolume(it) },
                        onSixteenthToggle = { vm.setSixteenthEnabled(it) },
                        onSixteenthVolume = { vm.setSixteenthVolume(it) },
                        onMasterVolume    = { vm.setMasterVolume(it) },
                    )
                }
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

// ── Control Dropdown Row ──────────────────────────────────────────────────────

@Composable
private fun ControlDropdownRow(
    song: Song,
    onClickType: (ClickType) -> Unit,
    onBeatMode: (BeatMode) -> Unit,
    onTimeSig: (Int, Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        var clickExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded          = clickExpanded,
            onExpandedChange  = { clickExpanded = it },
            modifier          = Modifier.weight(1f),
        ) {
            DropdownAnchor(
                label    = "CLICK TYPE",
                value    = song.clickType.displayName,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded          = clickExpanded,
                onDismissRequest  = { clickExpanded = false },
                modifier          = Modifier.background(BgSurface),
            ) {
                ClickType.entries.forEach { type ->
                    val isSel = type == song.clickType
                    DropdownMenuItem(
                        text = {
                            Text(
                                text     = type.displayName,
                                fontSize = 11.sp,
                                color    = if (isSel) TextGreen else Color(0xFFCCCCCC),
                            )
                        },
                        onClick  = { onClickType(type); clickExpanded = false },
                        modifier = Modifier.background(if (isSel) Color(0xFF1A3A1A) else Color.Transparent),
                    )
                }
            }
        }

        var beatExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded         = beatExpanded,
            onExpandedChange = { beatExpanded = it },
            modifier         = Modifier.weight(1f),
        ) {
            DropdownAnchor(
                label    = "BEAT MODE",
                value    = song.beatMode.displayName,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded         = beatExpanded,
                onDismissRequest = { beatExpanded = false },
                modifier         = Modifier.background(BgSurface),
            ) {
                BeatMode.entries.forEach { mode ->
                    val isSel = mode == song.beatMode
                    DropdownMenuItem(
                        text = {
                            Text(
                                text     = mode.displayName,
                                fontSize = 11.sp,
                                color    = if (isSel) TextGreen else Color(0xFFCCCCCC),
                            )
                        },
                        onClick  = { onBeatMode(mode); beatExpanded = false },
                        modifier = Modifier.background(if (isSel) Color(0xFF1A3A1A) else Color.Transparent),
                    )
                }
            }
        }

        var timeSigExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded         = timeSigExpanded,
            onExpandedChange = { timeSigExpanded = it },
            modifier         = Modifier.weight(1f),
        ) {
            DropdownAnchor(
                label    = "TIME SIG",
                value    = "${song.timeSigNumerator}/${song.timeSigDenominator}",
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded         = timeSigExpanded,
                onDismissRequest = { timeSigExpanded = false },
                modifier         = Modifier.background(BgSurface),
            ) {
                TIME_SIGS.forEach { (n, d) ->
                    val isSel = n == song.timeSigNumerator && d == song.timeSigDenominator
                    DropdownMenuItem(
                        text = {
                            Text(
                                text     = "$n/$d",
                                fontSize = 11.sp,
                                color    = if (isSel) TextGreen else Color(0xFFCCCCCC),
                            )
                        },
                        onClick  = { onTimeSig(n, d); timeSigExpanded = false },
                        modifier = Modifier.background(if (isSel) Color(0xFF1A3A1A) else Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownAnchor(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(BgSurface, RoundedCornerShape(4.dp))
            .border(1.dp, BdrMid, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column {
            Text(text = label, fontSize = 8.sp, color = Color(0xFF555555), letterSpacing = 0.5.sp)
            Text(text = value, fontSize = 11.sp, color = Color(0xFFCCCCCC))
        }
    }
}

// ── Beat Mixer ────────────────────────────────────────────────────────────────

@Composable
private fun BeatMixerSection(
    song: Song,
    onAccentToggle: (Boolean) -> Unit,
    onAccentVolume: (Float) -> Unit,
    onQuarterToggle: (Boolean) -> Unit,
    onQuarterVolume: (Float) -> Unit,
    onEighthToggle: (Boolean) -> Unit,
    onEighthVolume: (Float) -> Unit,
    onSixteenthToggle: (Boolean) -> Unit,
    onSixteenthVolume: (Float) -> Unit,
    onMasterVolume: (Float) -> Unit,
) {
    SectionLabel("BEAT MIXER")
    Spacer(Modifier.height(6.dp))

    val beatMode       = song.beatMode
    val eighthActive   = beatMode == BeatMode.EIGHTH || beatMode == BeatMode.SIXTEENTH
    val sixteenthActive = beatMode == BeatMode.SIXTEENTH

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        FaderChannel(
            label        = "ACC",
            sublabel     = "Beat 1",
            enabled      = song.accentEnabled,
            onToggle     = onAccentToggle,
            volume       = song.accentVolume,
            onVolume     = onAccentVolume,
            channelAlpha = 1f,
            isInteractive = true,
            onBg         = Color(0xFF1A5C1A),
            onBorder     = Color(0xFF2AB02A),
            onText       = Color(0xFF40E040),
            sliderColor  = Color(0xFF2AB02A),
            modifier     = Modifier.weight(1f),
        )

        FaderChannel(
            label        = "QTR",
            sublabel     = "2,3,4",
            enabled      = song.beatMixer.quarterEnabled,
            onToggle     = onQuarterToggle,
            volume       = song.beatMixer.quarterVolume,
            onVolume     = onQuarterVolume,
            channelAlpha = 1f,
            isInteractive = true,
            onBg         = Color(0xFF1A2A4A),
            onBorder     = Color(0xFF3A7BD5),
            onText       = Color(0xFF7AB3F5),
            sliderColor  = Color(0xFF3A7BD5),
            modifier     = Modifier.weight(1f),
        )

        FaderChannel(
            label        = "8TH",
            sublabel     = "+ands",
            enabled      = song.beatMixer.eighthEnabled,
            onToggle     = onEighthToggle,
            volume       = song.beatMixer.eighthVolume,
            onVolume     = onEighthVolume,
            channelAlpha = if (eighthActive) 1f else 0.2f,
            isInteractive = eighthActive,
            onBg         = Color(0xFF2A1A3A),
            onBorder     = Color(0xFF7D3C98),
            onText       = Color(0xFFC39BD3),
            sliderColor  = Color(0xFF7D3C98),
            modifier     = Modifier.weight(1f),
        )

        FaderChannel(
            label        = "16TH",
            sublabel     = "e+a",
            enabled      = song.beatMixer.sixteenthEnabled,
            onToggle     = onSixteenthToggle,
            volume       = song.beatMixer.sixteenthVolume,
            onVolume     = onSixteenthVolume,
            channelAlpha = if (sixteenthActive) 1f else 0.2f,
            isInteractive = sixteenthActive,
            onBg         = Color(0xFF3A1A00),
            onBorder     = Color(0xFFC06020),
            onText       = Color(0xFFF0A070),
            sliderColor  = Color(0xFFC06020),
            modifier     = Modifier.weight(1f),
        )

        MasterFaderChannel(
            volume   = song.masterVolume,
            onVolume = onMasterVolume,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FaderChannel(
    label: String,
    sublabel: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    volume: Float,
    onVolume: (Float) -> Unit,
    channelAlpha: Float,
    isInteractive: Boolean,
    onBg: Color,
    onBorder: Color,
    onText: Color,
    sliderColor: Color,
    modifier: Modifier = Modifier,
) {
    val offBg     = Color(0xFF1A1A1A)
    val offBorder = BdrMid
    val offText   = Color(0xFF333333)

    Column(
        modifier = modifier
            .alpha(channelAlpha)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(if (enabled) onBg else offBg, RoundedCornerShape(4.dp))
                .border(1.dp, if (enabled) onBorder else offBorder, RoundedCornerShape(4.dp))
                .then(
                    if (isInteractive) Modifier.clickable { onToggle(!enabled) }
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = label,
                fontSize   = 9.sp,
                color      = if (enabled) onText else offText,
                fontFamily = FontFamily.Monospace,
            )
        }

        Text(
            text      = sublabel,
            fontSize  = 8.sp,
            color     = Color(0xFF555555),
            textAlign = TextAlign.Center,
        )

        VerticalSlider(
            value         = volume,
            onValueChange = onVolume,
            height        = 100.dp,
            enabled       = isInteractive && enabled,
            colors = SliderDefaults.colors(
                thumbColor          = sliderColor,
                activeTrackColor    = sliderColor,
                inactiveTrackColor  = sliderColor.copy(alpha = 0.25f),
            ),
        )

        Text(
            text       = "${(volume * 100).roundToInt()}%",
            fontSize   = 9.sp,
            color      = TextMuted,
            fontFamily = FontFamily.Monospace,
            textAlign  = TextAlign.Center,
        )
    }
}

@Composable
private fun MasterFaderChannel(
    volume: Float,
    onVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier         = Modifier.fillMaxWidth().height(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = "MST",
                fontSize   = 9.sp,
                color      = TextMuted,
                fontFamily = FontFamily.Monospace,
            )
        }

        Text(
            text     = "Out",
            fontSize = 8.sp,
            color    = Color(0xFF555555),
        )

        VerticalSlider(
            value         = volume,
            onValueChange = onVolume,
            height        = 100.dp,
            colors = SliderDefaults.colors(
                thumbColor         = TextMuted,
                activeTrackColor   = TextMuted,
                inactiveTrackColor = Color(0xFF444444),
            ),
        )

        Text(
            text       = "${(volume * 100).roundToInt()}%",
            fontSize   = 9.sp,
            color      = TextMuted,
            fontFamily = FontFamily.Monospace,
            textAlign  = TextAlign.Center,
        )
    }
}

// Rotates a horizontal Slider 270° and swaps layout constraints so the result
// occupies (fader-width × height) space with correct touch targets.
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
) {
    Slider(
        value         = value,
        onValueChange = onValueChange,
        enabled       = enabled,
        colors        = colors,
        modifier      = modifier
            .graphicsLayer { rotationZ = 270f }
            .layout { measurable, constraints ->
                val heightPx  = height.roundToPx()
                val placeable = measurable.measure(
                    Constraints(
                        minWidth  = constraints.minHeight,
                        maxWidth  = heightPx,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(
                        x = -(placeable.width - placeable.height) / 2,
                        y = -(placeable.height - placeable.width) / 2,
                    )
                }
            },
    )
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

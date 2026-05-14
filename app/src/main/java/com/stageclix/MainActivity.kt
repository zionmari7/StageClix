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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import com.example.stageclix.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stageclix.audio.UsbAudioDeviceManager
import com.stageclix.data.ClickType
import com.stageclix.data.Song
import com.stageclix.data.VoiceCue
import com.stageclix.data.VoiceCueEvent
import com.stageclix.ui.theme.StageclixFont
import com.stageclix.ui.theme.StageclixTheme
import com.stageclix.viewmodel.PlayerViewModel
import kotlin.math.roundToInt

// ── Palette ───────────────────────────────────────────────────────────────────

private val Bg          = Color(0xFF1E1E1E)
private val BgSurface   = Color(0xFF252525)
private val BgDark      = Color(0xFF1A1A1A)
private val BgPlay      = Color(0xFF1A5C1A)
private val BgTap       = Color(0xFF12122A)
private val BgBack      = Color(0xFF2A2A2A)
private val BdrDark     = Color(0xFF111111)
private val BdrMid      = Color(0xFF2A2A2A)
private val BdrLight    = Color(0xFF3A3A3A)
private val BdrMuted    = Color(0xFF444444)
private val BdrGreen    = Color(0xFF2AB02A)
private val BdrTap      = Color(0xFF3A3AD5)
private val TextAmber   = Color(0xFFE8A020)
private val TextGreen   = Color(0xFF40E040)
private val TextMuted   = Color(0xFF888888)
private val TextSection = Color(0xFF666666)
private val TextTap     = Color(0xFF7A7AF5)
private val TextTapDim  = Color(0xFF3A3A8A)

private val BgNavy        = Color(0xFF1A1A2E)
private val BdrNavy       = Color(0xFF2A2A5A)
private val BgNavyDark    = Color(0xFF12122A)
private val BgNavyButton  = Color(0xFF1A1A3A)
private val VcPurple      = Color(0xFF5A5AD5)
private val VcPurpleLight = Color(0xFF7A7AF5)

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
    val song               by vm.currentSong.collectAsState()
    val isPlaying          by vm.isPlaying.collectAsState()
    val positionBeats      by vm.positionBeats.collectAsState()
    val connectedUsbDevices by vm.connectedUsbDevices.collectAsState()
    val selectedUsb        by vm.selectedUsb.collectAsState()

    var showBpmDialog    by remember { mutableStateOf(false) }
    var showAddCueDialog by remember { mutableStateOf(false) }

    if (showBpmDialog) {
        BpmInputDialog(
            current   = song.bpm,
            onConfirm = { vm.setBpm(it); showBpmDialog = false },
            onDismiss = { showBpmDialog = false },
        )
    }
    if (showAddCueDialog) {
        AddVoiceCueDialog(
            onConfirm = { bar, cue ->
                vm.addVoiceCueEvent(bar, cue)
                showAddCueDialog = false
            },
            onDismiss = { showAddCueDialog = false },
        )
    }

    StageclixTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                InfoBar(
                    song                 = song,
                    positionBeats        = positionBeats,
                    onBpmClick           = { showBpmDialog = true },
                    connectedUsbDevices  = connectedUsbDevices,
                    selectedUsb          = selectedUsb,
                    onSelectUsb          = { vm.selectUsbDevice(it) },
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
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

                    VoiceCuesPanel(
                        voiceCueEvents = song.voiceCueEvents,
                        voiceCueVolume = song.voiceCueVolume,
                        voiceCueMuted  = song.voiceCueMuted,
                        onVolumeChange = { vm.setVoiceCueVolume(it) },
                        onMutedChange  = { vm.setVoiceCueMuted(it) },
                        onAddCue       = { showAddCueDialog = true },
                        onRemoveCue    = { vm.removeVoiceCueEvent(it) },
                    )
                }

                BottomTransportBar(
                    isPlaying   = isPlaying,
                    onBack      = onBack,
                    onPlayPause = { if (isPlaying) vm.pause() else vm.play() },
                    onStop      = { vm.pause(); vm.rewind() },
                    onTap       = { vm.onTap() },
                )
            }
        }
    }
}

// ── Info Bar (top) ────────────────────────────────────────────────────────────

@Composable
private fun InfoBar(
    song: Song,
    positionBeats: Double,
    onBpmClick: () -> Unit,
    connectedUsbDevices: List<UsbAudioDeviceManager.UsbAudioDevice>,
    selectedUsb: UsbAudioDeviceManager.UsbAudioDevice?,
    onSelectUsb: (UsbAudioDeviceManager.UsbAudioDevice?) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(BgSurface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter            = painterResource(id = R.drawable.appicon),
                contentDescription = "StageClix",
                modifier           = Modifier
                    .height(28.dp)
                    .width(28.dp)
                    .padding(end = 4.dp),
                contentScale       = ContentScale.Fit,
            )

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
                    fontFamily = StageclixFont,
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
                    fontFamily = StageclixFont,
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
                    fontFamily = StageclixFont,
                )
            }

            // USB device selector
            var usbExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded         = usbExpanded,
                onExpandedChange = { usbExpanded = it },
                modifier         = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxSize()
                        .background(BgDark)
                        .border(1.dp, BdrMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    val dotColor = if (selectedUsb != null) TextGreen else TextMuted
                    val label    = selectedUsb?.name ?: "No USB"
                    Text(
                        text     = "● $label",
                        color    = dotColor,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                ExposedDropdownMenu(
                    expanded         = usbExpanded,
                    onDismissRequest = { usbExpanded = false },
                    modifier         = Modifier.background(BgSurface),
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text     = "○ No USB",
                                fontSize = 11.sp,
                                color    = if (selectedUsb == null) TextGreen else TextMuted,
                            )
                        },
                        onClick = { onSelectUsb(null); usbExpanded = false },
                    )
                    connectedUsbDevices.forEach { device ->
                        val isSel = device.deviceId == selectedUsb?.deviceId
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text     = "● ${device.name}",
                                    fontSize = 11.sp,
                                    color    = if (isSel) TextGreen else Color(0xFFCCCCCC),
                                )
                            },
                            onClick  = { onSelectUsb(device); usbExpanded = false },
                            modifier = Modifier.background(if (isSel) Color(0xFF1A3A1A) else Color.Transparent),
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = BdrDark)
    }
}

// ── Bottom Transport Bar ──────────────────────────────────────────────────────

@Composable
private fun BottomTransportBar(
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onTap: () -> Unit,
) {
    HorizontalDivider(thickness = 1.5.dp, color = BdrDark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSurface)
            .navigationBarsPadding()
            .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Back
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(54.dp)
                .background(BgBack, RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Filled.SkipPrevious,
                contentDescription = "Back",
                tint               = Color(0xFF555555),
                modifier           = Modifier.size(20.dp),
            )
        }

        // Play / Pause
        Box(
            modifier = Modifier
                .width(84.dp)
                .height(64.dp)
                .background(BgPlay, RoundedCornerShape(6.dp))
                .border(2.5.dp, BdrGreen, RoundedCornerShape(6.dp))
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint               = TextGreen,
                modifier           = Modifier.size(32.dp),
            )
        }

        // Stop
        Box(
            modifier = Modifier
                .width(62.dp)
                .height(54.dp)
                .background(BgBack, RoundedCornerShape(5.dp))
                .border(1.5.dp, BdrMuted, RoundedCornerShape(5.dp))
                .clickable(onClick = onStop),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Filled.Stop,
                contentDescription = "Stop",
                tint               = Color(0xFFBBBBBB),
                modifier           = Modifier.size(26.dp),
            )
        }

        // TAP
        Box(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .background(BgTap, RoundedCornerShape(5.dp))
                .border(1.5.dp, BdrTap, RoundedCornerShape(5.dp))
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector        = Icons.Filled.TouchApp,
                    contentDescription = "Tap tempo",
                    tint               = TextTap,
                    modifier           = Modifier.size(22.dp),
                )
                Text(
                    text       = "TAP",
                    color      = TextTap,
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = "tempo",
                    color = TextTapDim,
                    fontSize = 7.sp,
                )
            }
        }
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
                                text       = type.displayName,
                                fontSize   = 11.sp,
                                color      = if (isSel) TextGreen else Color(0xFFCCCCCC),
                                fontFamily = StageclixFont,
                            )
                        },
                        onClick  = { onClickType(type); clickExpanded = false },
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
                                text       = "$n/$d",
                                fontSize   = 11.sp,
                                color      = if (isSel) TextGreen else Color(0xFFCCCCCC),
                                fontFamily = StageclixFont,
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
            Text(text = label, fontSize = 8.sp, color = Color(0xFF555555), letterSpacing = 0.5.sp, fontFamily = StageclixFont)
            Text(text = value, fontSize = 11.sp, color = Color(0xFFCCCCCC), fontFamily = StageclixFont)
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
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(if (enabled) onBg else offBg, RoundedCornerShape(4.dp))
                .border(1.dp, if (enabled) onBorder else offBorder, RoundedCornerShape(4.dp))
                .clickable { onToggle(!enabled) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = label,
                fontSize   = 9.sp,
                color      = if (enabled) onText else offText,
                fontFamily = StageclixFont,
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
            enabled       = enabled,
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
            fontFamily = StageclixFont,
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
                fontFamily = StageclixFont,
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
            fontFamily = StageclixFont,
            textAlign  = TextAlign.Center,
        )
    }
}

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

// ── Voice Cues Panel ──────────────────────────────────────────────────────────

@Composable
private fun VoiceCuesPanel(
    voiceCueEvents: List<VoiceCueEvent>,
    voiceCueVolume: Float,
    voiceCueMuted:  Boolean,
    onVolumeChange: (Float) -> Unit,
    onMutedChange:  (Boolean) -> Unit,
    onAddCue:       () -> Unit,
    onRemoveCue:    (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgNavy, RoundedCornerShape(5.dp))
            .border(1.dp, BdrNavy, RoundedCornerShape(5.dp))
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Row 1 — header
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text          = "VOICE CUES",
                    fontSize      = 8.sp,
                    color         = Color(0xFF888888),
                    letterSpacing = 1.sp,
                    fontFamily    = StageclixFont,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text     = "${voiceCueEvents.size} cues",
                    fontSize = 9.sp,
                    color    = Color(0xFF555555),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text     = "VOL",
                    fontSize = 8.sp,
                    color    = Color(0xFF555555),
                )
                Slider(
                    value         = voiceCueVolume,
                    onValueChange = onVolumeChange,
                    modifier      = Modifier.width(80.dp),
                    colors = SliderDefaults.colors(
                        thumbColor         = VcPurpleLight,
                        activeTrackColor   = VcPurple,
                        inactiveTrackColor = VcPurple.copy(alpha = 0.25f),
                    ),
                )
                Spacer(Modifier.width(4.dp))
                // Mute button: highlighted = VC active (not muted)
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(24.dp)
                        .background(
                            if (!voiceCueMuted) BgNavyButton else BgDark,
                            RoundedCornerShape(3.dp),
                        )
                        .border(
                            1.dp,
                            if (!voiceCueMuted) VcPurple else BgDark,
                            RoundedCornerShape(3.dp),
                        )
                        .clickable { onMutedChange(!voiceCueMuted) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = "VC",
                        fontSize   = 8.sp,
                        color      = if (!voiceCueMuted) VcPurpleLight else Color(0xFF333333),
                        fontFamily = StageclixFont,
                    )
                }
            }

            // Row 2 — add cue button (dashed border via drawBehind)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(BgNavyDark, RoundedCornerShape(4.dp))
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xFF3A3A8A),
                            style = Stroke(
                                width      = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f),
                            ),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                    }
                    .clickable(onClick = onAddCue),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.Add,
                        contentDescription = null,
                        tint               = VcPurple,
                        modifier           = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text     = "Add Voice Cue",
                        fontSize = 11.sp,
                        color    = VcPurple,
                    )
                }
            }

            // Row 3 — cue event list (max 120dp, inner scroll)
            if (voiceCueEvents.isNotEmpty()) {
                val sorted = voiceCueEvents.sortedBy { it.triggerBar }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    sorted.forEach { event ->
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .height(32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text     = "Bar ${event.triggerBar}",
                                fontSize = 9.sp,
                                color    = VcPurpleLight,
                                modifier = Modifier.widthIn(min = 48.dp),
                            )
                            Text(
                                text     = "→",
                                fontSize = 9.sp,
                                color    = Color(0xFF444444),
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            Text(
                                text     = event.label,
                                fontSize = 11.sp,
                                color    = Color(0xFFCCCCCC),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick  = { onRemoveCue(event.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint               = Color(0xFF444444),
                                    modifier           = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Add Voice Cue dialog ──────────────────────────────────────────────────────

@Composable
private fun AddVoiceCueDialog(
    onConfirm: (Int, VoiceCue) -> Unit,
    onDismiss: () -> Unit,
) {
    var barInput    by remember { mutableStateOf("") }
    var selectedCue by remember { mutableStateOf(VoiceCue.VC_CHORUS) }
    var searchQuery by remember { mutableStateOf(VoiceCue.VC_CHORUS.displayName) }
    var cueExpanded by remember { mutableStateOf(false) }

    val filteredCues = if (searchQuery.isBlank()) VoiceCue.entries.toList()
        else VoiceCue.entries.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
    val barInt = barInput.toIntOrNull()
    val canAdd = barInt != null && barInt > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgSurface,
        title            = { Text("Add Voice Cue", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Bar number", fontSize = 10.sp, color = Color(0xFF888888))
                OutlinedTextField(
                    value           = barInput,
                    onValueChange   = { barInput = it },
                    singleLine      = true,
                    label           = { Text("e.g. 8") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = VcPurple,
                        unfocusedBorderColor = BdrMuted,
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = VcPurple,
                        focusedLabelColor    = VcPurple,
                        unfocusedLabelColor  = TextMuted,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text("Voice cue", fontSize = 10.sp, color = Color(0xFF888888))
                ExposedDropdownMenuBox(
                    expanded         = cueExpanded,
                    onExpandedChange = { cueExpanded = it },
                ) {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = { searchQuery = it; cueExpanded = true },
                        singleLine    = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = VcPurple,
                            unfocusedBorderColor = BdrMuted,
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White,
                            cursorColor          = VcPurple,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true),
                    )
                    ExposedDropdownMenu(
                        expanded         = cueExpanded,
                        onDismissRequest = { cueExpanded = false },
                        modifier         = Modifier.background(BgSurface),
                    ) {
                        filteredCues.forEach { cue ->
                            val isSel = cue == selectedCue
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text     = cue.displayName,
                                        fontSize = 11.sp,
                                        color    = if (isSel) VcPurpleLight else Color(0xFFCCCCCC),
                                    )
                                },
                                onClick = {
                                    selectedCue = cue
                                    searchQuery = cue.displayName
                                    cueExpanded = false
                                },
                                modifier = Modifier.background(
                                    if (isSel) BgNavyButton else Color.Transparent
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (canAdd) onConfirm(barInt!!, selectedCue) },
                enabled  = canAdd,
            ) { Text("Add", color = if (canAdd) VcPurpleLight else TextMuted) }
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

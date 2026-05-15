@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.stageclix

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.stageclix.R
import com.stageclix.audio.UsbAudioDeviceManager
import com.stageclix.data.Song
import com.stageclix.ui.SetlistScreen
import com.stageclix.ui.SongListScreen
import com.stageclix.ui.TimelineScreen
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
private val BdrMuted    = Color(0xFF444444)
private val BdrGreen    = Color(0xFF2AB02A)
private val BdrTap      = Color(0xFF3A3AD5)
private val TextAmber   = Color(0xFFE8A020)
private val TextGreen   = Color(0xFF40E040)
private val TextMuted   = Color(0xFF888888)
private val TextSection = Color(0xFF666666)
private val TextTap     = Color(0xFF7A7AF5)
private val TextTapDim  = Color(0xFF3A3A8A)

private val TIME_SIGS = listOf(2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 8, 7 to 8)

// ── Screen ────────────────────────────────────────────────────────────────────

enum class Screen { TIMELINE, MIXER, SONGS, SETTINGS }

// ── Activity ──────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        setContent {
            StageclixTheme {
                MainApp()
            }
        }
    }
}

// ── Main App ──────────────────────────────────────────────────────────────────

@Composable
fun MainApp(viewModel: PlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }

    var currentScreen by remember { mutableStateOf(Screen.MIXER) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1a1a1a),
                tonalElevation = 0.dp,
            ) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF2ab02a),
                    selectedTextColor = Color(0xFF2ab02a),
                    indicatorColor = Color(0xFF1a3a1a),
                    unselectedIconColor = Color(0xFF555555),
                    unselectedTextColor = Color(0xFF555555),
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.TIMELINE,
                    onClick = { currentScreen = Screen.TIMELINE },
                    icon = { Icon(Icons.Default.Timeline, null) },
                    label = { Text("Timeline", fontSize = 9.sp) },
                    colors = itemColors,
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.MIXER,
                    onClick = { currentScreen = Screen.MIXER },
                    icon = { Icon(Icons.Default.Tune, null) },
                    label = { Text("Mixer", fontSize = 9.sp) },
                    colors = itemColors,
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.SONGS,
                    onClick = { currentScreen = Screen.SONGS },
                    icon = { Icon(Icons.Default.LibraryMusic, null) },
                    label = { Text("Songs", fontSize = 9.sp) },
                    colors = itemColors,
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.SETTINGS,
                    onClick = { currentScreen = Screen.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings", fontSize = 9.sp) },
                    colors = itemColors,
                )
            }
        },
    ) { padding ->
        when (currentScreen) {
            Screen.TIMELINE -> TimelineScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
            Screen.MIXER -> MixerScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
            Screen.SONGS -> SongsNavScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
            Screen.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
            )
        }
    }
}

// ── Tab screens ───────────────────────────────────────────────────────────────

@Composable
fun MixerScreen(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val song         by viewModel.activeSong.collectAsState()
    val isPlaying    by viewModel.isPlaying.collectAsState()
    val posBeats     by viewModel.positionBeats.collectAsState()
    val selectedUsb  by viewModel.selectedUsb.collectAsState()

    var showBpmDialog by remember { mutableStateOf(false) }

    if (showBpmDialog && song != null) {
        BpmInputDialog(
            current   = song!!.bpm,
            onConfirm = { viewModel.setBpm(it); showBpmDialog = false },
            onDismiss = { showBpmDialog = false },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            InfoBar(
                song          = song,
                positionBeats = posBeats,
                onBpmClick    = { showBpmDialog = true },
                onTimeSig     = { n, d -> viewModel.setTimeSignature(n, d) },
                selectedUsb   = selectedUsb,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                song?.let { s ->
                    BeatMixerSection(
                        song              = s,
                        onAccentToggle    = { viewModel.setAccentEnabled(it) },
                        onAccentVolume    = { viewModel.setAccentVolume(it) },
                        onQuarterToggle   = { viewModel.setQuarterEnabled(it) },
                        onQuarterVolume   = { viewModel.setQuarterVolume(it) },
                        onEighthToggle    = { viewModel.setEighthEnabled(it) },
                        onEighthVolume    = { viewModel.setEighthVolume(it) },
                        onSixteenthToggle = { viewModel.setSixteenthEnabled(it) },
                        onSixteenthVolume = { viewModel.setSixteenthVolume(it) },
                        onMasterVolume    = { viewModel.setMasterVolume(it) },
                    )
                }
            }

            BottomTransportBar(
                isPlaying   = isPlaying,
                onPlayPause = { if (isPlaying) viewModel.pause() else viewModel.play() },
                onStop      = { viewModel.pause(); viewModel.rewind() },
                onTap       = { viewModel.onTap() },
            )
        }
    }
}

@Composable
fun SongsNavScreen(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val appData      by viewModel.appData.collectAsState()
    val activeSetlist by viewModel.activeSetlist.collectAsState()
    var showSongList by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (showSongList && activeSetlist != null) {
            SongListScreen(
                setlist      = activeSetlist!!,
                activeSongId = appData.activeSongId,
                onBack       = { showSongList = false },
                onSelectSong = { viewModel.selectSong(it) },
                onAddSong    = { viewModel.addSong(it) },
                onDeleteSong = { viewModel.deleteSong(it) },
                onRenameSong = { id, name -> viewModel.renameSong(id, name) },
                onReorder    = { viewModel.reorderSongs(it) },
            )
        } else {
            SetlistScreen(
                appData          = appData,
                onSelectSetlist  = { id -> viewModel.selectSetlist(id); showSongList = true },
                onAddSetlist     = { viewModel.addSetlist(it) },
                onDeleteSetlist  = { viewModel.deleteSetlist(it) },
                onRenameSetlist  = { id, name -> viewModel.renameSetlist(id, name) },
            )
        }
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Bg),
        contentAlignment = Alignment.Center,
    ) {
        Text("Settings", color = Color.White)
    }
}

// ── Info Bar ──────────────────────────────────────────────────────────────────

@Composable
private fun InfoBar(
    song: Song?,
    positionBeats: Double,
    onBpmClick: () -> Unit,
    onTimeSig: (Int, Int) -> Unit,
    selectedUsb: UsbAudioDeviceManager.UsbAudioDevice?,
) {
    val bpm      = song?.bpm ?: 120.0
    val timeSigN = song?.timeSigNumerator ?: 4
    val timeSigD = song?.timeSigDenominator ?: 4

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(BgSurface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter            = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "StageClix",
                modifier           = Modifier.height(28.dp).width(28.dp).padding(end = 4.dp),
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
                    text       = "%.1f".format(bpm),
                    color      = TextAmber,
                    fontSize   = 16.sp,
                    fontFamily = StageclixFont,
                )
            }

            var timeSigExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded         = timeSigExpanded,
                onExpandedChange = { timeSigExpanded = it },
                modifier         = Modifier.weight(0.9f).fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxSize()
                        .background(BgDark)
                        .border(1.dp, BdrMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = "$timeSigN / $timeSigD",
                        color      = Color.White,
                        fontSize   = 12.sp,
                        fontFamily = StageclixFont,
                    )
                }
                ExposedDropdownMenu(
                    expanded         = timeSigExpanded,
                    onDismissRequest = { timeSigExpanded = false },
                    modifier         = Modifier.background(BgSurface),
                ) {
                    TIME_SIGS.forEach { (n, d) ->
                        val isSel = n == timeSigN && d == timeSigD
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text       = "$n / $d",
                                    fontSize   = 11.sp,
                                    color      = if (isSel) TextGreen else Color(0xFFCCCCCC),
                                    fontFamily = StageclixFont,
                                )
                            },
                            onClick = { onTimeSig(n, d); timeSigExpanded = false },
                            modifier = Modifier.background(
                                if (isSel) Color(0xFF1A3A1A) else Color.Transparent
                            ),
                        )
                    }
                }
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
                    text       = formatPosition(positionBeats, timeSigN),
                    color      = TextAmber,
                    fontSize   = 12.sp,
                    fontFamily = StageclixFont,
                )
            }

            Box(
                modifier         = Modifier.weight(1.7f).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val usbName = selectedUsb?.name?.takeIf { it.isNotBlank() } ?: "No USB"
                Row(
                    modifier = Modifier
                        .background(
                            if (selectedUsb != null) Color(0xFF0D1F0D) else Color(0xFF1A1A1A),
                            RoundedCornerShape(3.dp),
                        )
                        .border(
                            0.5.dp,
                            if (selectedUsb != null) Color(0xFF1E5C1E) else Color(0xFF2A2A2A),
                            RoundedCornerShape(3.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(
                                if (selectedUsb != null) Color(0xFF2AB02A) else Color(0xFF444444),
                                CircleShape,
                            ),
                    )
                    Text(
                        text     = usbName,
                        fontSize = 9.sp,
                        color    = if (selectedUsb != null) Color(0xFF2AB02A) else Color(0xFF555555),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp),
                    )
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
                Text(text = "TAP",   color = TextTap,    fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(text = "tempo", color = TextTapDim, fontSize = 7.sp)
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
            label       = "ACC",
            sublabel    = "Beat 1",
            enabled     = song.beatMixer.accentEnabled,
            onToggle    = onAccentToggle,
            volume      = song.beatMixer.accentVolume,
            onVolume    = onAccentVolume,
            onBg        = Color(0xFF1A5C1A),
            onBorder    = Color(0xFF2AB02A),
            onText      = Color(0xFF40E040),
            sliderColor = Color(0xFF2AB02A),
            modifier    = Modifier.weight(1f),
        )
        FaderChannel(
            label       = "QTR",
            sublabel    = "2,3,4",
            enabled     = song.beatMixer.quarterEnabled,
            onToggle    = onQuarterToggle,
            volume      = song.beatMixer.quarterVolume,
            onVolume    = onQuarterVolume,
            onBg        = Color(0xFF1A2A4A),
            onBorder    = Color(0xFF3A7BD5),
            onText      = Color(0xFF7AB3F5),
            sliderColor = Color(0xFF3A7BD5),
            modifier    = Modifier.weight(1f),
        )
        FaderChannel(
            label       = "8TH",
            sublabel    = "+ands",
            enabled     = song.beatMixer.eighthEnabled,
            onToggle    = onEighthToggle,
            volume      = song.beatMixer.eighthVolume,
            onVolume    = onEighthVolume,
            onBg        = Color(0xFF2A1A3A),
            onBorder    = Color(0xFF7D3C98),
            onText      = Color(0xFFC39BD3),
            sliderColor = Color(0xFF7D3C98),
            modifier    = Modifier.weight(1f),
        )
        FaderChannel(
            label       = "16TH",
            sublabel    = "e+a",
            enabled     = song.beatMixer.sixteenthEnabled,
            onToggle    = onSixteenthToggle,
            volume      = song.beatMixer.sixteenthVolume,
            onVolume    = onSixteenthVolume,
            onBg        = Color(0xFF3A1A00),
            onBorder    = Color(0xFFC06020),
            onText      = Color(0xFFF0A070),
            sliderColor = Color(0xFFC06020),
            modifier    = Modifier.weight(1f),
        )
        MasterFaderChannel(
            volume   = song.beatMixer.masterVolume,
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
        modifier            = modifier.padding(horizontal = 2.dp),
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
        Text(text = sublabel, fontSize = 8.sp, color = Color(0xFF555555), textAlign = TextAlign.Center)
        VerticalSlider(
            value         = volume,
            onValueChange = onVolume,
            height        = 100.dp,
            enabled       = enabled,
            colors        = SliderDefaults.colors(
                thumbColor         = sliderColor,
                activeTrackColor   = sliderColor,
                inactiveTrackColor = sliderColor.copy(alpha = 0.25f),
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
        modifier            = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier         = Modifier.fillMaxWidth().height(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "MST", fontSize = 9.sp, color = TextMuted, fontFamily = StageclixFont)
        }
        Text(text = "Out", fontSize = 8.sp, color = Color(0xFF555555))
        VerticalSlider(
            value         = volume,
            onValueChange = onVolume,
            height        = 100.dp,
            colors        = SliderDefaults.colors(
                thumbColor         = TextMuted,
                activeTrackColor   = TextMuted,
                inactiveTrackColor = Color(0xFF444444),
            ),
        )
        Text(
            text      = "${(volume * 100).roundToInt()}%",
            fontSize  = 9.sp,
            color     = TextMuted,
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
        title            = { Text("Set BPM", color = Color.White) },
        text = {
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
    Text(text = text, color = TextSection, fontSize = 9.sp, letterSpacing = 1.sp)
}

private fun formatPosition(beats: Double, numerator: Int): String {
    if (numerator <= 0) return "1 : 1 : 00"
    val bar       = (beats / numerator).toInt() + 1
    val beatInBar = (beats % numerator).toInt() + 1
    val sub       = ((beats % 1.0) * 100).roundToInt().coerceIn(0, 99)
    return "%d : %d : %02d".format(bar, beatInBar, sub)
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.stageclix.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stageclix.audio.UsbAudioDeviceManager
import com.stageclix.data.BackingClip
import com.stageclix.data.BeatCell
import com.stageclix.data.BeatPattern
import com.stageclix.data.ClickClip
import com.stageclix.data.ClickType
import com.stageclix.data.NoteRow
import com.stageclix.data.Song
import com.stageclix.data.Track
import com.stageclix.data.TrackKind
import com.stageclix.data.VoiceCueClip
import com.stageclix.viewmodel.PlayerViewModel

// ── Constants ─────────────────────────────────────────
private const val TRACK_HEADER_WIDTH_DP = 72
private const val RULER_HEIGHT_DP       = 40
private const val TRACK_HEIGHT_DP       = 56
private const val PX_PER_BAR            = 80f

// ── Main screen ───────────────────────────────────────

@Composable
fun TimelineScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val song          by viewModel.activeSong.collectAsState()
    val positionBeats by viewModel.positionBeats.collectAsState()
    val isPlaying     by viewModel.isPlaying.collectAsState()
    val setlist       by viewModel.activeSetlist.collectAsState()

    val currentSong = song ?: return

    val scrollState = rememberScrollState()
    var showBeatBuilder by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)),
    ) {
        TimelineTopBar(
            setlistName  = setlist?.name ?: "",
            songName     = currentSong.name,
            bpm          = currentSong.bpm,
            posBeats     = positionBeats,
            timeSigNum   = currentSong.timeSigNumerator,
            usbDevice    = viewModel.selectedUsb.collectAsState().value,
            songs        = setlist?.songs ?: emptyList(),
            activeSongId = currentSong.id,
            onSelectSong = viewModel::selectSong,
        )

        Row(modifier = Modifier.weight(1f)) {
            // Fixed track header column
            Column(
                modifier = Modifier
                    .width(TRACK_HEADER_WIDTH_DP.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF0D0D10))
                    .border(BorderStroke(0.5.dp, Color(0xFF1E1E1E))),
            ) {
                Box(
                    modifier = Modifier
                        .height(RULER_HEIGHT_DP.dp)
                        .fillMaxWidth()
                        .background(Color(0xFF181818))
                        .border(BorderStroke(0.5.dp, Color(0xFF111111))),
                ) {
                    Text(
                        "BARS",
                        fontSize = 8.sp,
                        color = Color(0xFF444444),
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
                        fontFamily = FontFamily.Monospace,
                    )
                }
                currentSong.tracks.forEach { track ->
                    TrackHeader(
                        track = track,
                        onAddClip = {
                            if (track.kind == TrackKind.CLICK) showBeatBuilder = true
                        },
                    )
                }
            }

            // Scrollable ruler + lanes
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState),
                ) {
                    val totalBars = currentSong.totalBars.coerceAtLeast(32)
                    val totalWidth = (totalBars * PX_PER_BAR).dp

                    TimelineRuler(
                        totalBars     = totalBars,
                        positionBeats = positionBeats,
                        beatsPerBar   = currentSong.timeSigNumerator,
                        modifier      = Modifier.width(totalWidth).height(RULER_HEIGHT_DP.dp),
                    )
                    currentSong.tracks.forEach { track ->
                        TrackLane(
                            track         = track,
                            totalBars     = totalBars,
                            positionBeats = positionBeats,
                            beatsPerBar   = currentSong.timeSigNumerator,
                            onClipResized = { viewModel.updateClickClip(it) },
                            modifier      = Modifier.width(totalWidth).height(TRACK_HEIGHT_DP.dp),
                        )
                    }
                }
            }
        }

        ZoomSnapBar()

        TimelineTransportBar(
            isPlaying = isPlaying,
            onPlay    = viewModel::play,
            onPause   = viewModel::pause,
            onRewind  = viewModel::rewind,
            onTap     = viewModel::onTap,
        )
    }

    if (showBeatBuilder) {
        BeatBuilderSheet(
            song        = currentSong,
            onAuditNote = { row -> viewModel.auditNote(row) },
            onDismiss   = { showBeatBuilder = false },
            onConfirm   = { clip ->
                viewModel.addClickClip(clip)
                showBeatBuilder = false
            },
        )
    }
}

// ── Top bar ───────────────────────────────────────────

@Composable
fun TimelineTopBar(
    setlistName: String,
    songName: String,
    bpm: Double,
    posBeats: Double,
    timeSigNum: Int,
    usbDevice: UsbAudioDeviceManager.UsbAudioDevice?,
    songs: List<Song>,
    activeSongId: String,
    onSelectSong: (String) -> Unit,
) {
    var showSongPicker by remember {
        mutableStateOf(false)
    }
    val safeTimeSigNum = timeSigNum.coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252525))
            .border(BorderStroke(1.dp, Color(0xFF111111)))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Setlist > Song breadcrumb — tappable
        Row(
            modifier = Modifier
                .clickable { showSongPicker = true }
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                setlistName,
                fontSize = 9.sp,
                color = Color(0xFF2ab02a),
                fontWeight = FontWeight.Bold,
            )
            Text(" › ", fontSize = 9.sp, color = Color(0xFF333333))
            Text(
                songName,
                fontSize = 11.sp,
                color = Color(0xFFCCCCCC),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }

        Spacer(Modifier.weight(1f))

        // BPM
        Box(
            modifier = Modifier
                .background(Color(0xFF1A1A1A), RoundedCornerShape(3.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "BPM",
                    fontSize = 6.sp,
                    color = Color(0xFF555555),
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    bpm.toInt().toString(),
                    fontSize = 14.sp,
                    color = Color(0xFFE8A020),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Time sig
        Text(
            "$safeTimeSigNum/4",
            fontSize = 11.sp,
            color = Color(0xFF888888),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(Color(0xFF1A1A1A), RoundedCornerShape(3.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )

        // Position
        val bar = (posBeats / safeTimeSigNum).toInt() + 1
        val beat = (posBeats % safeTimeSigNum).toInt() + 1
        Text(
            "%d:%d".format(bar, beat),
            fontSize = 11.sp,
            color = Color(0xFFE8A020),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(Color(0xFF1A1A1A), RoundedCornerShape(3.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )

        // USB dot
        Box(
            Modifier
                .size(6.dp)
                .background(
                    if (usbDevice != null) Color(0xFF2ab02a)
                    else Color(0xFF444444),
                    CircleShape,
                ),
        )
    }

    // Song picker dropdown
    if (showSongPicker && songs.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showSongPicker = false },
            containerColor = Color(0xFF252525),
            title = {
                Text(
                    "Switch Song",
                    fontSize = 13.sp,
                    color = Color(0xFFCCCCCC),
                )
            },
            text = {
                LazyColumn {
                    items(songs) { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectSong(s.id)
                                    showSongPicker = false
                                }
                                .background(
                                    if (s.id == activeSongId) Color(0xFF1A3A1A)
                                    else Color.Transparent,
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                s.name,
                                fontSize = 12.sp,
                                color = if (s.id == activeSongId) Color(0xFF40E040)
                                else Color(0xFFAAAAAA),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${s.bpm.toInt()} BPM",
                                fontSize = 10.sp,
                                color = Color(0xFF2ab02a),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSongPicker = false }) {
                    Text("Close", color = Color(0xFF2ab02a))
                }
            },
        )
    }
}

// ── Track header ──────────────────────────────────────

@Composable
fun TrackHeader(
    track: Track,
    onAddClip: () -> Unit,
) {
    val (labelColor, bgColor, borderColor) = when (track.kind) {
        TrackKind.CLICK -> Triple(
            Color(0xFF40E040), Color(0xFF0F1A0F),
            Color(0xFF1A3A1A),
        )
        TrackKind.VOICE_CUE -> Triple(
            Color(0xFF7A7AF5), Color(0xFF0F0F1A),
            Color(0xFF1A1A3A),
        )
        TrackKind.BACKING -> Triple(
            Color(0xFFC39BD3), Color(0xFF1A0F1A),
            Color(0xFF2A1A3A),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT_DP.dp)
            .background(bgColor)
            .border(BorderStroke(0.5.dp, Color(0xFF111111)))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Column {
            Text(
                track.name,
                fontSize = 9.sp,
                color = labelColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when (track.kind) {
                    TrackKind.CLICK -> "Ch 1"
                    TrackKind.VOICE_CUE -> "Ch 3"
                    TrackKind.BACKING -> "Ch 2"
                },
                fontSize = 7.sp,
                color = Color(0xFF444444),
            )
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomEnd)
                .background(bgColor, RoundedCornerShape(3.dp))
                .border(0.5.dp, borderColor, RoundedCornerShape(3.dp))
                .clickable(onClick = onAddClip),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 13.sp, color = labelColor)
        }
    }
}

// ── Ruler ─────────────────────────────────────────────

@Composable
fun TimelineRuler(
    totalBars: Int,
    positionBeats: Double,
    beatsPerBar: Int,
    modifier: Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val safeBars = beatsPerBar.coerceAtLeast(1)

    Canvas(modifier = modifier.background(Color(0xFF181818))) {
        val barW = size.width / totalBars.coerceAtLeast(1)

        for (bar in 0 until totalBars) {
            val x = bar * barW

            // Major bar line
            drawLine(Color(0xFF2A2A2A), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)

            // Bar number — always try, skip if too tight
            val label = "${bar + 1}"
            val measured = textMeasurer.measure(
                label,
                TextStyle(fontSize = 9.sp, color = Color(0xFF777777), fontFamily = FontFamily.Monospace),
            )
            if (barW > measured.size.width + 6f || bar % 4 == 0) {
                drawText(measured, topLeft = Offset(x + 3f, size.height / 2f - measured.size.height / 2f))
            }

            // Beat sub-lines
            for (beat in 1 until safeBars) {
                val beatX = x + beat * (barW / safeBars)
                drawLine(
                    Color(0xFF222222),
                    Offset(beatX, size.height * 0.55f),
                    Offset(beatX, size.height),
                    strokeWidth = 0.5f,
                )
            }
        }

        // Playhead
        val phX = (positionBeats / safeBars * barW).toFloat()
        drawLine(Color(0xFFE8A020), Offset(phX, 0f), Offset(phX, size.height), strokeWidth = 1.5f)
        drawRect(
            color = Color(0xFFE8A020),
            topLeft = Offset(phX - 5f, 0f),
            size = Size(10f, 8f),
        )
    }
}

// ── Track lane ────────────────────────────────────────

@Composable
fun TrackLane(
    track: Track,
    totalBars: Int,
    positionBeats: Double,
    beatsPerBar: Int,
    onClipResized: (ClickClip) -> Unit,
    modifier: Modifier,
) {
    val bgColor = when (track.kind) {
        TrackKind.CLICK     -> Color(0xFF111814)
        TrackKind.VOICE_CUE -> Color(0xFF0F0F16)
        TrackKind.BACKING   -> Color(0xFF150F15)
    }

    Box(modifier = modifier.background(bgColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pxPerBar  = PX_PER_BAR
            val pxPerBeat = pxPerBar / beatsPerBar

            for (bar in 0..totalBars) {
                val x = bar * pxPerBar
                drawLine(
                    color = Color(0xFF1E1E1E),
                    start = Offset(x, 0f),
                    end   = Offset(x, size.height),
                    strokeWidth = 1f,
                )
                for (beat in 1 until beatsPerBar) {
                    val bx = bar * pxPerBar + beat * pxPerBeat
                    drawLine(
                        color = Color(0xFF181818),
                        start = Offset(bx, 0f),
                        end   = Offset(bx, size.height),
                        strokeWidth = 0.5f,
                    )
                }
            }

            val px = (positionBeats / beatsPerBar) * pxPerBar
            drawLine(
                color       = Color(0xFFE8A020),
                start       = Offset(px.toFloat(), 0f),
                end         = Offset(px.toFloat(), size.height),
                strokeWidth = 1.5f,
            )
        }

        if (track.kind == TrackKind.CLICK) {
            Box(modifier = Modifier.fillMaxSize()) {
                track.clickClips.forEach { clip ->
                    ClickClipBlock(
                        clip        = clip,
                        beatsPerBar = beatsPerBar,
                        onResized   = onClipResized,
                    )
                }
            }
        }

        if (track.kind == TrackKind.VOICE_CUE) {
            Box(modifier = Modifier.fillMaxSize()) {
                track.voiceCueClips.forEach { clip ->
                    VoiceCueClipBlock(clip = clip)
                }
            }
        }
    }
}

@Composable
private fun ClickClipBlock(
    clip: ClickClip,
    beatsPerBar: Int,
    onResized: (ClickClip) -> Unit,
) {
    val pxPerBar = PX_PER_BAR
    var durationBars by remember(clip.id) { mutableStateOf(clip.durationBars.toFloat()) }
    var dragAccumulator by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .padding(top = 4.dp, bottom = 4.dp)
            .offset { IntOffset(x = (clip.startBar * PX_PER_BAR).toInt(), y = 0) }
            .width((durationBars * PX_PER_BAR).dp)
            .fillMaxHeight()
            .background(Color(0xFF1A3A1A), RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF2AB02A), RoundedCornerShape(3.dp)),
    ) {
        Text(
            text       = "${clip.pattern.clickType.displayName} · ${clip.pattern.timeSigNumerator}/4",
            fontSize   = 8.sp,
            color      = Color(0xFF40E040),
            maxLines   = 1,
            modifier   = Modifier.padding(start = 4.dp, top = 4.dp),
            fontFamily = FontFamily.Monospace,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .align(Alignment.BottomStart)
                .padding(start = 3.dp, end = 3.dp, bottom = 2.dp),
        ) {
            val cells      = clip.pattern.cells
            val totalCells = clip.pattern.timeSigNumerator * 4
            val cellW      = size.width / totalCells.toFloat()
            cells.forEach { cell ->
                val offset = cell.beatIndex * 4 + cell.subIndex
                val x      = offset * cellW
                val h      = when (cell.row) {
                    NoteRow.ACC       -> size.height
                    NoteRow.QTR       -> size.height * 0.7f
                    NoteRow.EIGHTH    -> size.height * 0.5f
                    NoteRow.SIXTEENTH -> size.height * 0.35f
                }
                val color = when (cell.row) {
                    NoteRow.ACC       -> Color(0xFF2AB02A)
                    NoteRow.QTR       -> Color(0xFF3A7BD5)
                    NoteRow.EIGHTH    -> Color(0xFF7D3C98)
                    NoteRow.SIXTEENTH -> Color(0xFFC06020)
                }
                drawRect(
                    color   = color,
                    topLeft = Offset(x, size.height - h),
                    size    = Size(cellW - 1f, h),
                )
            }
        }

        Box(
            modifier = Modifier
                .width(12.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .background(Color(0x882AB02A), RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                .pointerInput(clip.id) {
                    detectDragGestures(
                        onDragEnd = {
                            dragAccumulator = 0f
                            onResized(clip.copy(
                                durationBars = durationBars.toInt().coerceAtLeast(1),
                            ))
                        },
                        onDragCancel = { dragAccumulator = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulator += dragAmount.x
                            val barsMoved = (dragAccumulator / PX_PER_BAR).toInt()
                            if (barsMoved != 0) {
                                durationBars = (durationBars + barsMoved).coerceAtLeast(1f)
                                dragAccumulator -= barsMoved * PX_PER_BAR
                            }
                        },
                    )
                },
        )
    }
}

@Composable
private fun VoiceCueClipBlock(clip: VoiceCueClip) {
    val pxPerBar = PX_PER_BAR
    Box(
        modifier = Modifier
            .padding(top = 5.dp, bottom = 5.dp)
            .offset { IntOffset(x = (clip.startBar * pxPerBar).toInt(), y = 0) }
            .width((pxPerBar * 1.5f).dp)
            .fillMaxHeight()
            .background(Color(0xFF1A1A3A), RoundedCornerShape(3.dp))
            .border(0.5.dp, Color(0xFF3A3AD5), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text       = clip.label.ifEmpty { clip.voiceCue.displayName },
            fontSize   = 8.sp,
            color      = Color(0xFF7A7AF5),
            maxLines   = 1,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ── Zoom / snap bar ───────────────────────────────────

@Composable
fun ZoomSnapBar() {
    var snapMode by remember { mutableStateOf("Beat") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF181818))
            .border(BorderStroke(0.5.dp, Color(0xFF111111)))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "SNAP",
            fontSize = 7.sp,
            color = Color(0xFF444444),
            fontFamily = FontFamily.Monospace,
        )
        listOf("Bar", "Beat", "1/2", "Off").forEach { mode ->
            Box(
                modifier = Modifier
                    .background(
                        if (snapMode == mode) Color(0xFF1A2A3A)
                        else Color(0xFF1A1A1A),
                        RoundedCornerShape(2.dp),
                    )
                    .border(
                        0.5.dp,
                        if (snapMode == mode) Color(0xFF3A7BD5)
                        else Color(0xFF222222),
                        RoundedCornerShape(2.dp),
                    )
                    .clickable { snapMode = mode }
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    mode,
                    fontSize = 8.sp,
                    color = if (snapMode == mode) Color(0xFF7AB3F5)
                    else Color(0xFF444444),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "ZOOM",
            fontSize = 7.sp,
            color = Color(0xFF444444),
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ── Transport bar ─────────────────────────────────────

@Composable
fun TimelineTransportBar(
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRewind: () -> Unit,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252525))
            .border(BorderStroke(1.5.dp, Color(0xFF111111)))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Back
        Box(
            modifier = Modifier
                .width(36.dp).height(52.dp)
                .background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                .clickable(onClick = onRewind),
            contentAlignment = Alignment.Center,
        ) {
            Text("⏮", fontSize = 16.sp, color = Color(0xFF555555))
        }
        // Play/Pause
        Box(
            modifier = Modifier
                .width(80.dp).height(60.dp)
                .background(Color(0xFF1A5C1A), RoundedCornerShape(6.dp))
                .border(2.dp, Color(0xFF2AB02A), RoundedCornerShape(6.dp))
                .clickable(onClick = if (isPlaying) onPause else onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isPlaying) "⏸" else "▶",
                fontSize = 28.sp,
                color = Color(0xFF40E040),
            )
        }
        // Stop
        Box(
            modifier = Modifier
                .width(60.dp).height(52.dp)
                .background(Color(0xFF2A2A2A), RoundedCornerShape(5.dp))
                .border(1.5.dp, Color(0xFF444444), RoundedCornerShape(5.dp))
                .clickable(onClick = onRewind),
            contentAlignment = Alignment.Center,
        ) {
            Text("⏹", fontSize = 22.sp, color = Color(0xFFBBBBBB))
        }
        // TAP
        Box(
            modifier = Modifier
                .weight(1f).height(52.dp)
                .background(Color(0xFF12122A), RoundedCornerShape(5.dp))
                .border(1.5.dp, Color(0xFF3A3AD5), RoundedCornerShape(5.dp))
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("👆", fontSize = 18.sp)
                Text(
                    "TAP",
                    fontSize = 9.sp,
                    color = Color(0xFF7A7AF5),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun TransportBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color,
    border: Color,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(5.dp))
            .border(1.5.dp, border, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// ── Beat builder sheet ────────────────────────────────

@Composable
private fun LegacyBeatBuilderSheet(
    song: Song,
    onDismiss: () -> Unit,
    onConfirm: (ClickClip) -> Unit,
) {
    var cells by remember { mutableStateOf(buildDefaultCells(song.timeSigNumerator)) }
    var clickType by remember { mutableStateOf(ClickType.WOODBLOCK) }
    var startBar by remember { mutableStateOf(0) }
    var durationBars by remember { mutableStateOf(4) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFF444444), RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Beat Builder", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCCCCCC))

            // Start / Duration
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Start Bar", fontSize = 9.sp, color = Color(0xFF666666))
                    NumberStepper(
                        value = startBar + 1,
                        min = 1,
                        max = song.totalBars,
                        onChange = { startBar = it - 1 },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Duration (bars)", fontSize = 9.sp, color = Color(0xFF666666))
                    NumberStepper(value = durationBars, min = 1, max = 64, onChange = { durationBars = it })
                }
            }

            // Click type
            Text("Click Type", fontSize = 9.sp, color = Color(0xFF666666))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(ClickType.entries) { type ->
                    val sel = type == clickType
                    Box(
                        modifier = Modifier
                            .background(
                                if (sel) Color(0xFF1A5C1A) else Color(0xFF252525),
                                RoundedCornerShape(4.dp),
                            )
                            .border(1.dp, if (sel) Color(0xFF2AB02A) else Color(0xFF333333), RoundedCornerShape(4.dp))
                            .clickable { clickType = type }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(type.displayName, fontSize = 10.sp, color = if (sel) Color(0xFF2AB02A) else Color(0xFF888888))
                    }
                }
            }

            // Beat grid
            Text("Pattern (${song.timeSigNumerator}/${song.timeSigDenominator})", fontSize = 9.sp, color = Color(0xFF666666))
            BeatGrid(
                timeSigNum = song.timeSigNumerator,
                cells = cells,
                onToggle = { toggled ->
                    cells = cells.map { c ->
                        if (c.beatIndex == toggled.beatIndex && c.row == toggled.row && c.subIndex == toggled.subIndex)
                            c.copy(enabled = !c.enabled)
                        else c
                    }
                },
            )

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF888888)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConfirm(
                            ClickClip(
                                startBar = startBar,
                                durationBars = durationBars,
                                pattern = BeatPattern(
                                    cells = cells.filter { it.enabled },
                                    clickType = clickType,
                                    timeSigNumerator = song.timeSigNumerator,
                                    timeSigDenominator = song.timeSigDenominator,
                                    bpm = song.bpm,
                                ),
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A5C1A)),
                ) {
                    Text("Add Clip", color = Color(0xFF2AB02A))
                }
            }
        }
    }
}

private fun buildDefaultCells(timeSigNum: Int): List<BeatCell> = buildList {
    for (beat in 0 until timeSigNum) {
        add(BeatCell(beat, NoteRow.ACC,       0, beat == 0))
        add(BeatCell(beat, NoteRow.QTR,       0, true))
        add(BeatCell(beat, NoteRow.EIGHTH,    0, false))
        add(BeatCell(beat, NoteRow.EIGHTH,    1, false))
        add(BeatCell(beat, NoteRow.SIXTEENTH, 0, false))
        add(BeatCell(beat, NoteRow.SIXTEENTH, 1, false))
        add(BeatCell(beat, NoteRow.SIXTEENTH, 2, false))
        add(BeatCell(beat, NoteRow.SIXTEENTH, 3, false))
    }
}

@Composable
private fun BeatGrid(
    timeSigNum: Int,
    cells: List<BeatCell>,
    onToggle: (BeatCell) -> Unit,
) {
    val rows = listOf(
        NoteRow.ACC       to Triple("ACC",  1, Color(0xFF2AB02A)),
        NoteRow.QTR       to Triple("QTR",  1, Color(0xFF3A7BD5)),
        NoteRow.EIGHTH    to Triple("8TH",  2, Color(0xFF7D3C98)),
        NoteRow.SIXTEENTH to Triple("16TH", 4, Color(0xFFC06020)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (row, cfg) ->
            val (label, subdivisions, color) = cfg
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    label,
                    fontSize = 8.sp,
                    color = Color(0xFF555555),
                    modifier = Modifier.width(32.dp),
                    fontFamily = FontFamily.Monospace,
                )
                for (beat in 0 until timeSigNum) {
                    for (sub in 0 until subdivisions) {
                        val cell = cells.find {
                            it.row == row && it.beatIndex == beat && it.subIndex == sub
                        } ?: BeatCell(beat, row, sub, false)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .background(
                                    if (cell.enabled) color.copy(alpha = 0.6f) else Color(0xFF252525),
                                    RoundedCornerShape(2.dp),
                                )
                                .border(
                                    0.5.dp,
                                    if (cell.enabled) color else Color(0xFF333333),
                                    RoundedCornerShape(2.dp),
                                )
                                .clickable { onToggle(cell) },
                        )
                    }
                    if (beat < timeSigNum - 1) Spacer(Modifier.width(2.dp))
                }
            }
        }
    }
}

@Composable
private fun NumberStepper(
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (value > min) onChange(value - 1) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Remove, null, tint = Color(0xFF888888), modifier = Modifier.size(14.dp))
        }
        Text(
            "$value",
            fontSize = 13.sp,
            color = Color(0xFFCCCCCC),
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        IconButton(onClick = { if (value < max) onChange(value + 1) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Add, null, tint = Color(0xFF888888), modifier = Modifier.size(14.dp))
        }
    }
}

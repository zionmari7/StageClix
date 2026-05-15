package com.stageclix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stageclix.data.Setlist
import com.stageclix.data.Song
import com.stageclix.data.TrackKind
import kotlin.math.roundToInt

@Composable
fun SongListScreen(
    setlist: Setlist,
    activeSongId: String,
    onBack: () -> Unit,
    onSelectSong: (String) -> Unit,
    onAddSong: (String) -> Unit,
    onDeleteSong: (String) -> Unit,
    onRenameSong: (String, String) -> Unit,
    onReorder: (List<Song>) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<Song?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    var songs by remember(setlist.songs) { mutableStateOf(setlist.songs) }
    LaunchedEffect(setlist.songs) { songs = setlist.songs }

    val itemHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    if (showAddDialog) {
        SongNameDialog(
            title = "New Song",
            confirmLabel = "Add",
            onConfirm = { onAddSong(it); showAddDialog = false },
            onDismiss = { showAddDialog = false },
        )
    }

    if (showRenameDialog && actionTarget != null) {
        SongNameDialog(
            title = "Rename Song",
            confirmLabel = "Rename",
            initial = actionTarget!!.name,
            onConfirm = { onRenameSong(actionTarget!!.id, it); showRenameDialog = false; actionTarget = null },
            onDismiss = { showRenameDialog = false; actionTarget = null },
        )
    }

    actionTarget?.let { target ->
        if (!showRenameDialog) {
            AlertDialog(
                onDismissRequest = { actionTarget = null },
                containerColor = Color(0xFF252525),
                title = { Text(target.name, color = Color(0xFFCCCCCC), fontSize = 13.sp) },
                text = null,
                confirmButton = {
                    Column {
                        TextButton(onClick = { showRenameDialog = true }) {
                            Text("Rename", color = Color(0xFF2ab02a))
                        }
                        TextButton(onClick = { onDeleteSong(target.id); actionTarget = null }) {
                            Text("Delete", color = Color(0xFFCC4444))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { actionTarget = null }) {
                        Text("Cancel", color = Color(0xFF888888))
                    }
                },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1e1e1e)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFCCCCCC))
            }
            Text(
                text = setlist.name,
                fontSize = 13.sp,
                color = Color(0xFFCCCCCC),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Song", tint = Color(0xFFCCCCCC))
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                val isActive = song.id == activeSongId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(if (isActive) Color(0xFF1A3A1A) else Color.Transparent)
                        .combinedClickable(
                            onClick = { onSelectSong(song.id) },
                            onLongClick = { actionTarget = song },
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 9.sp,
                        color = Color(0xFF444444),
                        modifier = Modifier.widthIn(min = 24.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            text = song.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color(0xFF2ab02a) else Color(0xFFCCCCCC),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SongChip(
                                label = "${song.bpm.toInt()} BPM",
                                bg = Color(0xFF1a3a1a),
                                fg = Color(0xFF2ab02a),
                            )
                            SongChip(
                                label = "${song.timeSigNumerator}/${song.timeSigDenominator}",
                                bg = Color(0xFF1a2a3a),
                                fg = Color(0xFF3a7bd5),
                            )
                            val clickTypeName = song.tracks
                                .find { it.kind == TrackKind.CLICK }
                                ?.clickClips?.firstOrNull()
                                ?.pattern?.clickType?.displayName
                                ?: "No click"
                            SongChip(
                                label = clickTypeName,
                                bg = Color(0xFF2a1a3a),
                                fg = Color(0xFF7d3c98),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .pointerInput(index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragStartIndex = index
                                        totalDragY = 0f
                                    },
                                    onDrag = { _, dragAmount ->
                                        totalDragY += dragAmount.y
                                    },
                                    onDragEnd = {
                                        if (dragStartIndex >= 0) {
                                            val steps = (totalDragY / itemHeightPx).roundToInt()
                                            val targetIdx = (dragStartIndex + steps)
                                                .coerceIn(0, songs.lastIndex)
                                            if (targetIdx != dragStartIndex) {
                                                val newList = songs.toMutableList()
                                                val item = newList.removeAt(dragStartIndex)
                                                newList.add(targetIdx, item)
                                                songs = newList
                                                onReorder(newList)
                                            }
                                        }
                                        dragStartIndex = -1
                                        totalDragY = 0f
                                    },
                                    onDragCancel = {
                                        dragStartIndex = -1
                                        totalDragY = 0f
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Reorder",
                            tint = Color(0xFF2a2a2a),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF222222))
            }
        }
    }
}

@Composable
private fun SongChip(label: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(text = label, fontSize = 8.sp, color = fg, maxLines = 1)
    }
}

@Composable
private fun SongNameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF252525),
        title = { Text(title, color = Color.White) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2ab02a),
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF2ab02a),
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text(confirmLabel, color = Color(0xFF2ab02a)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF888888)) }
        },
    )
}

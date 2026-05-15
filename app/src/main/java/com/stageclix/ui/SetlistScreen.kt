package com.stageclix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stageclix.data.AppData
import com.stageclix.data.Setlist

private val setlistAccentColors = listOf(
    Color(0xFF1A5C1A),
    Color(0xFF1A2A4A),
    Color(0xFF2A1A3A),
    Color(0xFF3A2A00),
)

@Composable
fun SetlistScreen(
    appData: AppData,
    onSelectSetlist: (String) -> Unit,
    onAddSetlist: (String) -> Unit,
    onDeleteSetlist: (String) -> Unit,
    onRenameSetlist: (String, String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<Setlist?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        SetlistNameDialog(
            title = "New Setlist",
            confirmLabel = "Create",
            onConfirm = { onAddSetlist(it); showAddDialog = false },
            onDismiss = { showAddDialog = false },
        )
    }

    if (showRenameDialog && actionTarget != null) {
        SetlistNameDialog(
            title = "Rename",
            confirmLabel = "Rename",
            initial = actionTarget!!.name,
            onConfirm = { onRenameSetlist(actionTarget!!.id, it); showRenameDialog = false; actionTarget = null },
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
                        TextButton(onClick = { onDeleteSetlist(target.id); actionTarget = null }) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "StageClix",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCCCCCC),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Setlist", tint = Color(0xFFCCCCCC))
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(appData.setlists) { index, setlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .combinedClickable(
                            onClick = { onSelectSetlist(setlist.id) },
                            onLongClick = { actionTarget = setlist },
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                setlistAccentColors[index % setlistAccentColors.size],
                                RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF2ab02a),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = setlist.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCCCCCC),
                        )
                        Text(
                            text = "${setlist.songs.size} songs",
                            fontSize = 9.sp,
                            color = Color(0xFF555555),
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = null,
                        tint = Color(0xFF333333),
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF2A2A2A))
            }
        }
    }
}

@Composable
private fun SetlistNameDialog(
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

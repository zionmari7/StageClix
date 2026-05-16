package com.stageclix.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stageclix.data.BeatCell
import com.stageclix.data.BeatPattern
import com.stageclix.data.ClickClip
import com.stageclix.data.ClickType
import com.stageclix.data.NoteRow
import com.stageclix.data.Song

@Composable
fun BeatBuilderSheet(
    song: Song,
    onAuditNote: (row: Int) -> Unit,
    onClickTypeSelected: (ClickType) -> Unit = {},
    onTimeSigChanged: (numerator: Int) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (ClickClip) -> Unit,
) {
    var selectedClickType by remember {
        mutableStateOf(ClickType.WOODBLOCK)
    }
    var timeSigNum by remember {
        mutableStateOf(song.timeSigNumerator)
    }
    var bpm by remember {
        mutableStateOf(song.bpm)
    }

    val enabledCells = remember {
        mutableStateMapOf<String, Boolean>()
    }

    fun cellKey(beat: Int, row: NoteRow, sub: Int) =
        "${beat}_${row.name}_$sub"

    fun toggleCell(beat: Int, row: NoteRow, sub: Int) {
        val key = cellKey(beat, row, sub)
        enabledCells[key] = !(enabledCells[key] ?: false)
    }

    fun isCellOn(beat: Int, row: NoteRow, sub: Int) =
        enabledCells[cellKey(beat, row, sub)] ?: false

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color.Transparent)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF252525),
                        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                    )
                    .border(
                        BorderStroke(1.dp, Color(0xFF333333)),
                        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                    )
                    .padding(14.dp)
                    .clickable(enabled = false) {},
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Beat Builder",
                        fontSize = 13.sp,
                        color = Color(0xFFCCCCCC),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "✕",
                        fontSize = 16.sp,
                        color = Color(0xFF555555),
                        modifier = Modifier.clickable(onClick = onDismiss),
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    "CLICK TYPE",
                    fontSize = 8.sp,
                    color = Color(0xFF555555),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.08.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ClickType.entries.forEach { ct ->
                        val sel = ct == selectedClickType
                        Box(
                            modifier = Modifier
                                .background(
                                    if (sel) Color(0xFF1A3A1A) else Color(0xFF1A1A1A),
                                    RoundedCornerShape(3.dp),
                                )
                                .border(
                                    1.dp,
                                    if (sel) Color(0xFF2AB02A) else Color(0xFF222222),
                                    RoundedCornerShape(3.dp),
                                )
                                .clickable {
                                    selectedClickType = ct
                                    onClickTypeSelected(ct)
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                ct.displayName,
                                fontSize = 9.sp,
                                color = if (sel) Color(0xFF40E040) else Color(0xFF555555),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "SIG",
                        fontSize = 8.sp,
                        color = Color(0xFF555555),
                        fontFamily = FontFamily.Monospace,
                    )
                    listOf(2, 3, 4, 5, 6, 7).forEach { n ->
                        val sel = n == timeSigNum
                        Box(
                            modifier = Modifier
                                .background(
                                    if (sel) Color(0xFF1A2A3A) else Color(0xFF1A1A1A),
                                    RoundedCornerShape(3.dp),
                                )
                                .border(
                                    1.dp,
                                    if (sel) Color(0xFF3A7BD5) else Color(0xFF222222),
                                    RoundedCornerShape(3.dp),
                                )
                                .clickable {
                                    timeSigNum = n
                                    onTimeSigChanged(n)
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "$n/4",
                                fontSize = 9.sp,
                                color = if (sel) Color(0xFF7AB3F5) else Color(0xFF555555),
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        "BPM",
                        fontSize = 8.sp,
                        color = Color(0xFF555555),
                        fontFamily = FontFamily.Monospace,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1A1A1A), RoundedCornerShape(3.dp))
                                .border(1.dp, Color(0xFF444444), RoundedCornerShape(3.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                bpm.toInt().toString(),
                                fontSize = 14.sp,
                                color = Color(0xFFE8A020),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column {
                            Text(
                                "▲",
                                fontSize = 9.sp,
                                color = Color(0xFF666666),
                                modifier = Modifier.clickable {
                                    bpm = (bpm + 1).coerceAtMost(300.0)
                                },
                            )
                            Text(
                                "▼",
                                fontSize = 9.sp,
                                color = Color(0xFF666666),
                                modifier = Modifier.clickable {
                                    bpm = (bpm - 1).coerceAtLeast(20.0)
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    "BEAT GRID · TAP TO PLACE · TAP AGAIN TO REMOVE",
                    fontSize = 8.sp,
                    color = Color(0xFF555555),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.04.sp,
                )

                Spacer(Modifier.height(5.dp))

                Row {
                    Box(Modifier.width(44.dp))
                    for (beat in 0 until timeSigNum) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp)
                                .background(Color(0xFF1E1E1E))
                                .border(BorderStroke(0.5.dp, Color(0xFF222222))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (beat + 1).toString(),
                                fontSize = 10.sp,
                                color = if (beat == 0) Color(0xFFE8A020) else Color(0xFF666666),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                data class RowDef(
                    val row: NoteRow,
                    val label: String,
                    val color: Color,
                    val bgColor: Color,
                    val borderColor: Color,
                    val subCount: Int,
                )

                val rows = listOf(
                    RowDef(NoteRow.ACC, "ACC", Color(0xFF40E040), Color(0xFF1A5C1A), Color(0xFF2AB02A), 1),
                    RowDef(NoteRow.QTR, "QTR", Color(0xFF7AB3F5), Color(0xFF1A2A4A), Color(0xFF3A7BD5), 1),
                    RowDef(NoteRow.EIGHTH, "8TH", Color(0xFFC39BD3), Color(0xFF2A1A3A), Color(0xFF7D3C98), 2),
                    RowDef(NoteRow.SIXTEENTH, "16TH", Color(0xFFF0A070), Color(0xFF3A1A00), Color(0xFFC06020), 4),
                )

                rows.forEach { rowDef ->
                    Row(modifier = Modifier.height(40.dp)) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF1A1A1A))
                                .border(BorderStroke(0.5.dp, Color(0xFF222222))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                rowDef.label,
                                fontSize = 9.sp,
                                color = rowDef.color,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }

                        for (beat in 0 until timeSigNum) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(BorderStroke(0.5.dp, Color(0xFF2A2A2A))),
                            ) {
                                for (sub in 0 until rowDef.subCount) {
                                    val isOn = isCellOn(beat, rowDef.row, sub)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(
                                                if (isOn) rowDef.bgColor
                                                else Color(0xFF1A1A1A),
                                            )
                                            .border(
                                                BorderStroke(
                                                    0.5.dp,
                                                    if (isOn) rowDef.borderColor else Color(0xFF222222),
                                                ),
                                            )
                                            .clickable {
                                                toggleCell(beat, rowDef.row, sub)
                                                if (isCellOn(beat, rowDef.row, sub)) {
                                                    onAuditNote(rowDef.row.ordinal)
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isOn) {
                                            Box(
                                                Modifier
                                                    .size(8.dp)
                                                    .background(rowDef.color, RoundedCornerShape(2.dp)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Color(0xFF1A5C1A), RoundedCornerShape(5.dp))
                        .border(1.5.dp, Color(0xFF2AB02A), RoundedCornerShape(5.dp))
                        .clickable {
                            val cells = mutableListOf<BeatCell>()
                            for (beat in 0 until timeSigNum) {
                                rows.forEach { rowDef ->
                                    for (sub in 0 until rowDef.subCount) {
                                        if (isCellOn(beat, rowDef.row, sub)) {
                                            cells.add(
                                                BeatCell(
                                                    beatIndex = beat,
                                                    row = rowDef.row,
                                                    subIndex = sub,
                                                    enabled = true,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                            val pattern = BeatPattern(
                                cells = cells,
                                clickType = selectedClickType,
                                timeSigNumerator = timeSigNum,
                                timeSigDenominator = 4,
                                bpm = bpm,
                            )
                            val clip = ClickClip(
                                startBar = 0,
                                durationBars = 1,
                                pattern = pattern,
                            )
                            onConfirm(clip)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "OK — Add to Timeline",
                        fontSize = 12.sp,
                        color = Color(0xFF40E040),
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

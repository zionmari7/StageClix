package com.example.stageclix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clickcue.audio.AudioEngineJni

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val jni = remember { AudioEngineJni() }
            val handle = remember { jni.create() }

            DisposableEffect(Unit) {
                jni.start(handle)
                onDispose {
                    jni.stop(handle)
                    jni.destroy(handle)
                }
            }

            var isPlaying by remember { mutableStateOf(false) }
            var bpm by remember { mutableFloatStateOf(120f) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${bpm.toInt()} BPM",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )

                Slider(
                    value = bpm,
                    onValueChange = { value ->
                        bpm = value
                        jni.setBpm(handle, value.toDouble())
                    },
                    valueRange = 60f..200f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )

                Button(onClick = {
                    if (isPlaying) jni.pause(handle) else jni.play(handle)
                    isPlaying = !isPlaying
                }) {
                    Text(
                        text = if (isPlaying) "Stop" else "Play",
                        fontSize = 20.sp
                    )
                }
            }
        }
    }
}

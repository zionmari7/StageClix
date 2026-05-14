package com.stageclix.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UsbAudioDeviceManager(private val context: Context) {

    data class UsbAudioDevice(
        val deviceId: Int,
        val name: String,
        val channelCount: Int,
        val sampleRate: Int,
    )

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _connectedDevices = MutableStateFlow<List<UsbAudioDevice>>(emptyList())
    val connectedDevices: StateFlow<List<UsbAudioDevice>> = _connectedDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<UsbAudioDevice?>(null)
    val selectedDevice: StateFlow<UsbAudioDevice?> = _selectedDevice.asStateFlow()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshDevices()
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(usbReceiver, filter)
        }
        refreshDevices()
    }

    fun stop() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: IllegalArgumentException) {}
    }

    fun selectDevice(device: UsbAudioDevice?) {
        _selectedDevice.value = device
    }

    private fun refreshDevices() {
        val usbTypes = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)
        val devices = audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in usbTypes }
            .map { info ->
                val name = info.productName?.toString()?.takeIf { it.isNotBlank() }
                    ?: "USB Device"
                val channels = info.channelCounts.maxOrNull() ?: 2
                val sampleRate = info.sampleRates.maxOrNull() ?: 48000
                UsbAudioDevice(
                    deviceId     = info.id,
                    name         = name,
                    channelCount = channels,
                    sampleRate   = sampleRate,
                )
            }

        _connectedDevices.value = devices

        val current = _selectedDevice.value
        if (current != null && devices.none { it.deviceId == current.deviceId }) {
            _selectedDevice.value = devices.firstOrNull()
        } else if (current == null && devices.isNotEmpty()) {
            _selectedDevice.value = devices.first()
        }
    }
}

package com.stageclix.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
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

    private val audioManager = context
        .getSystemService(Context.AUDIO_SERVICE)
        as AudioManager

    private val usbManager = context
        .getSystemService(Context.USB_SERVICE)
        as UsbManager

    private val _devices =
        MutableStateFlow<List<UsbAudioDevice>>(emptyList())
    val connectedDevices: StateFlow<List<UsbAudioDevice>>
        = _devices.asStateFlow()

    private val _selected =
        MutableStateFlow<UsbAudioDevice?>(null)
    val selectedDevice: StateFlow<UsbAudioDevice?>
        = _selected.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d("StageClix",
                        "USB state changed: ${intent.action}")
                    refreshDevices()
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(receiver, filter)
        refreshDevices()
        Log.d("StageClix", "UsbAudioDeviceManager started")
    }

    fun stop() {
        runCatching {
            context.unregisterReceiver(receiver)
        }
        Log.d("StageClix", "UsbAudioDeviceManager stopped")
    }

    fun selectDevice(device: UsbAudioDevice?) {
        _selected.value = device
        Log.d("StageClix",
            "Selected USB device: ${device?.name ?: "none"}")
    }

    private fun refreshDevices() {
        val outputs = audioManager.getDevices(
            AudioManager.GET_DEVICES_OUTPUTS)
        val usbHardwareNames = usbManager.deviceList
            .values
            .filter { it.hasAudioInterface() }
            .map { it.displayName() }

        val usbDevices = outputs
            .filter {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            .mapIndexed { index, info ->
                UsbAudioDevice(
                    deviceId     = info.id,
                    name         = usbHardwareNames.getOrNull(index)
                                     ?: info.productName?.toString()
                                     ?: "USB Audio",
                    channelCount = info.channelCounts
                                     .maxOrNull() ?: 2,
                    sampleRate   = info.sampleRates
                                     .maxOrNull() ?: 48000,
                )
            }

        Log.d("StageClix",
            "USB audio devices found: " +
            "${usbDevices.map { "${it.name}(ch${it.channelCount})" }}")

        _devices.value = usbDevices

        // Auto-select first device if nothing selected
        if (_selected.value == null &&
            usbDevices.isNotEmpty()) {
            _selected.value = usbDevices.first()
            Log.d("StageClix",
                "Auto-selected: ${usbDevices.first().name}")
        }

        // Clear selection if device disconnected
        val currentSelected = _selected.value
        if (currentSelected != null &&
            usbDevices.none {
                it.deviceId == currentSelected.deviceId
            }) {
            Log.d("StageClix",
                "Device disconnected: ${currentSelected.name}")
            _selected.value = usbDevices.firstOrNull()
        }
    }

    private fun UsbDevice.hasAudioInterface(): Boolean {
        repeat(interfaceCount) { index ->
            if (getInterface(index).interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                return true
            }
        }
        return false
    }

    private fun UsbDevice.displayName(): String {
        knownDeviceName()?.let { return it }

        val parts = listOfNotNull(
            manufacturerName?.trim()?.takeIf { it.isNotBlank() },
            productName?.trim()?.takeIf { it.isNotBlank() },
        )
        return parts
            .distinct()
            .joinToString(" ")
            .ifBlank { "USB Audio" }
    }

    private fun UsbDevice.knownDeviceName(): String? =
        when (vendorId to productId) {
            2235 to 10688 -> "M-Audio M-Track Duo"
            else -> null
        }
}

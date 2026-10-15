package com.example.service.audio

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Watches which output device the media is routed to (phone speaker, wired headset, Bluetooth
 * speaker / headphones / neckband, USB …) and tells the controller whenever it changes.
 */
object AudioRouteMonitor {
    private const val TAG = "AudioRouteMonitor"

    private var started = false
    private lateinit var app: Context
    private lateinit var audioManager: AudioManager
    private var listener: ((current: AudioRoute?, connected: List<AudioRoute>) -> Unit)? = null

    fun start(context: Context, onChange: (current: AudioRoute?, connected: List<AudioRoute>) -> Unit) {
        if (started) return
        started = true
        app = context.applicationContext
        audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        listener = onChange

        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = publish()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = publish()
        }
        try {
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        } catch (t: Throwable) {
            Log.w(TAG, "registerAudioDeviceCallback failed: ${t.message}")
        }
        publish()
    }

    /** Re-evaluate now, e.g. after the Bluetooth permission was granted. */
    fun refresh() {
        if (started) publish()
    }

    /** True if we may read the Bluetooth device class (needed to tell speakers from headphones). */
    fun canClassifyBluetooth(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun publish() {
        try {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
            val connected = outputs.mapNotNull { toRoute(it) }.distinctBy { it.key }
            val active = pickActive(outputs)?.let { toRoute(it) }
            listener?.invoke(active, connected)
        } catch (t: Throwable) {
            Log.w(TAG, "publish failed: ${t.message}")
        }
    }

    /** The device media is really going to. Exact on Android 13+, a sensible guess before that. */
    private fun pickActive(outputs: List<AudioDeviceInfo>): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val routed = audioManager.getAudioDevicesForAttributes(attrs).firstOrNull { it.isSink }
                if (routed != null) return routed
            } catch (t: Throwable) {
                Log.w(TAG, "getAudioDevicesForAttributes failed: ${t.message}")
            }
        }
        return outputs.filter { it.isSink && toRoute(it) != null }.minByOrNull { rank(it.type) }
    }

    /** Lower = more likely to be where media plays when connected. */
    private fun rank(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID -> 0
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> 1
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE -> 2
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_DOCK -> 3
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 4
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 5
        else -> 6 // e.g. Bluetooth SCO (calls only)
    }

    private fun toRoute(d: AudioDeviceInfo): AudioRoute? {
        if (!d.isSink) return null
        val category = when (d.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> RouteCategory.PHONE_SPEAKER
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE -> RouteCategory.WIRED
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> RouteCategory.USB
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET ->
                classifyBluetooth(d.address)
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> RouteCategory.BT_SPEAKER
            AudioDeviceInfo.TYPE_HEARING_AID -> RouteCategory.BT_HEADSET
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_DOCK -> RouteCategory.OTHER
            else -> return null // telephony, remote submix and similar are not real outputs
        }

        val address = if (Build.VERSION.SDK_INT >= 28) { try { d.address } catch (_: Throwable) { "" } } else ""
        val name = try { d.productName?.toString()?.trim().orEmpty() } catch (_: Throwable) { "" }

        val key = when {
            category == RouteCategory.PHONE_SPEAKER -> "dev:speaker"
            !address.isNullOrBlank() -> "dev:" + address.lowercase(Locale.US)
            else -> "dev:t${d.type}:$name"
        }
        val label = when {
            category == RouteCategory.PHONE_SPEAKER -> RouteCategory.PHONE_SPEAKER.title
            name.isNotEmpty() -> name
            else -> category.title
        }
        return AudioRoute(key, label, category)
    }

    /** Speaker, or headphones/neckband/earbuds? Needs the Bluetooth permission on Android 12+. */
    private fun classifyBluetooth(address: String?): RouteCategory {
        if (address.isNullOrBlank() || !canClassifyBluetooth(app)) return RouteCategory.BT_ANY
        return try {
            val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: return RouteCategory.BT_ANY
            val cls = adapter.getRemoteDevice(address).bluetoothClass ?: return RouteCategory.BT_ANY
            when (cls.deviceClass) {
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER -> RouteCategory.BT_SPEAKER

                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> RouteCategory.BT_HEADSET

                else -> RouteCategory.BT_ANY
            }
        } catch (t: Throwable) {
            // SecurityException (permission), IllegalArgumentException (bad address) …
            RouteCategory.BT_ANY
        }
    }
}

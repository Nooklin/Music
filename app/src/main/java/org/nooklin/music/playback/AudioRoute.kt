package org.nooklin.music.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the sound goes when AirPlay is off: the phone speaker or one Bluetooth device. The choice is the id of an
 * [AudioDeviceInfo]; null means Android decides (the state at the start of the app). The players and the Auto Mix
 * engine are told in PlaybackService.
 */
object AudioRoute {
    private val _selectedId = MutableStateFlow<Int?>(null)
    val selectedId: StateFlow<Int?> = _selectedId.asStateFlow()

    private var watching = false

    fun select(id: Int?) {
        _selectedId.value = id
    }

    /** Forgets the choice when the chosen device is unplugged or switched off. Call once. */
    @Synchronized
    fun watch(context: Context) {
        if (watching) return
        watching = true
        val manager = context.applicationContext.getSystemService(AudioManager::class.java)
        manager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                val id = _selectedId.value ?: return
                if (removedDevices.any { it.id == id }) _selectedId.value = null
            }
        }, null)
    }

    fun outputs(context: Context): List<AudioDeviceInfo> {
        val manager = context.applicationContext.getSystemService(AudioManager::class.java)
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
    }

    fun isBluetooth(d: AudioDeviceInfo): Boolean = d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
        d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
        d.type == AudioDeviceInfo.TYPE_HEARING_AID

    fun speaker(devices: List<AudioDeviceInfo>): AudioDeviceInfo? =
        devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    /** The device the player has to use: the chosen one if it is still there, otherwise null (Android decides). */
    fun device(context: Context): AudioDeviceInfo? {
        val id = _selectedId.value ?: return null
        return outputs(context).firstOrNull { it.id == id }
    }

    /**
     * The device the sound goes to now: the chosen one, or else the one Android routes music to. Null when it is not
     * one of [devices] (a wired headset, say).
     */
    fun activeId(context: Context, devices: List<AudioDeviceInfo>): Int? {
        val chosen = _selectedId.value
        if (chosen != null && devices.any { it.id == chosen }) return chosen
        val manager = context.applicationContext.getSystemService(AudioManager::class.java)
        val music = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        return manager.getAudioDevicesForAttributes(music).firstOrNull { r -> devices.any { it.id == r.id } }?.id
    }
}

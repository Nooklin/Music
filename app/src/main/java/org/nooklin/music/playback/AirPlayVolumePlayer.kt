@file:OptIn(UnstableApi::class)

package org.nooklin.music.playback

import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArraySet
import org.nooklin.music.airplay.AirPlayController

/**
 * The player as the media session sees it. While AirPlay is on it presents itself as a remote device with its own
 * volume (0 to 100), so the volume keys of the phone move the volume of the speakers. Without AirPlay everything is
 * passed to the real player.
 */
class AirPlayVolumePlayer(player: Player, private val airPlay: AirPlayController) : ForwardingPlayer(player) {
    private val listeners = CopyOnWriteArraySet<Player.Listener>()
    private var lastActive = false
    private var lastVolume = -1

    private val active get() = airPlay.state.value.active
    private val master get() = airPlay.state.value.master

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
    }

    /** Called when AirPlay is switched on or off or its volume changes. */
    fun refresh() {
        val nowActive = active
        val nowVolume = if (nowActive) master else wrappedPlayer.deviceVolume
        val activeChanged = nowActive != lastActive
        val volumeChanged = nowVolume != lastVolume
        lastActive = nowActive
        lastVolume = nowVolume
        if (!activeChanged && !volumeChanged) return
        for (listener in listeners) {
            if (activeChanged) {
                listener.onDeviceInfoChanged(deviceInfo)
                listener.onAvailableCommandsChanged(availableCommands)
            }
            listener.onDeviceVolumeChanged(deviceVolume, isDeviceMuted)
        }
    }

    override fun getDeviceInfo(): DeviceInfo =
        if (active) {
            DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMinVolume(0).setMaxVolume(100).build()
        } else {
            super.getDeviceInfo()
        }

    override fun getDeviceVolume(): Int = if (active) master else super.getDeviceVolume()

    override fun isDeviceMuted(): Boolean = if (active) master == 0 else super.isDeviceMuted()

    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands()
        if (!active) return commands
        return commands.buildUpon()
            .addAll(
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_ADJUST_DEVICE_VOLUME,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS
            )
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int) {
        if (active) airPlay.setAll(volume) else super.setDeviceVolume(volume)
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        if (active) airPlay.setAll(volume) else super.setDeviceVolume(volume, flags)
    }

    @Deprecated("Deprecated in Java")
    override fun increaseDeviceVolume() {
        if (active) airPlay.adjustAll(STEP) else super.increaseDeviceVolume()
    }

    override fun increaseDeviceVolume(flags: Int) {
        if (active) airPlay.adjustAll(STEP) else super.increaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Java")
    override fun decreaseDeviceVolume() {
        if (active) airPlay.adjustAll(-STEP) else super.decreaseDeviceVolume()
    }

    override fun decreaseDeviceVolume(flags: Int) {
        if (active) airPlay.adjustAll(-STEP) else super.decreaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceMuted(muted: Boolean) {
        if (active) { if (muted) airPlay.setAll(0) } else super.setDeviceMuted(muted)
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        if (active) { if (muted) airPlay.setAll(0) } else super.setDeviceMuted(muted, flags)
    }

    private companion object {
        const val STEP = 5
    }
}

package com.mohdshayan.cropmark.camera

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Hardware shutter keys. MainActivity forwards key presses here; only while the capture screen is
 * resumed and the volume shutter is on are they consumed, so volume keys work normally elsewhere.
 * Bluetooth selfie remotes send volume up or enter, which need no permission.
 */
class ShutterBus {
    @Volatile
    var active: Boolean = false

    private val _presses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val presses: SharedFlow<Unit> = _presses

    fun onKeyDown(keyCode: Int): Boolean {
        if (!active || keyCode !in KEYS) return false
        _presses.tryEmit(Unit)
        return true
    }

    fun consumesKeyUp(keyCode: Int): Boolean = active && keyCode in KEYS

    private companion object {
        val KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        )
    }
}

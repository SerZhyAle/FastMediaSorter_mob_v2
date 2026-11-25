package com.sza.fastmediasorter.ui.common.input

import android.view.InputDevice

/**
 * Answers "is a physical alphabetic keyboard attached" from the input-device list instead of the window's
 * touch mode: the window leaves touch mode on any directional key and a gamepad cannot press a digit, so
 * neither says that digit hotkeys are usable. A virtual device (the injector behind the soft keyboard) is
 * never a physical keyboard. Gates the hotkey prefixes on the Copy / Move buttons. S4638.
 */
object PhysicalKeyboardDetector {

    /** Pure decision for one device, unit-tested without a device. */
    fun isAlphabeticKeyboard(isVirtual: Boolean, sources: Int, keyboardType: Int): Boolean =
        !isVirtual &&
            (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
            keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC

    fun isPresent(): Boolean = InputDevice.getDeviceIds().any { id ->
        InputDevice.getDevice(id)?.let { device ->
            isAlphabeticKeyboard(device.isVirtual, device.sources, device.keyboardType)
        } == true
    }
}

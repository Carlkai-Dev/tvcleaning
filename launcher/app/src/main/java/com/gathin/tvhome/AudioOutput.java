package com.gathin.tvhome;

import android.media.AudioDeviceInfo;

/**
 * show_audio_output: which connected output the line above the top buttons names. Apps cannot ask Android where
 * media is routed, so this follows Android's media priority over the connected outputs: a Bluetooth device, then
 * wired or USB headphones, then an HDMI ARC/eARC receiver, then the TV speakers. A TV set to its own speakers in
 * Sound settings while an ARC receiver stays plugged in still reads as the receiver.
 */
final class AudioOutput {

    static final int SPEAKERS = 0, ARC = 1, WIRED = 2, BLUETOOTH = 3;

    // Types added in Android 12, as numbers so they compile against minSdk 26 without a lint warning.
    private static final int TYPE_BLE_HEADSET = 26, TYPE_BLE_SPEAKER = 27, TYPE_HDMI_EARC = 29;

    private AudioOutput() { }

    /** The kind of an output device type; -1 for one this line does not name (built-in earpiece, telephony). */
    static int kind(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case TYPE_BLE_HEADSET:
            case TYPE_BLE_SPEAKER:
                return BLUETOOTH;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return WIRED;
            case AudioDeviceInfo.TYPE_HDMI_ARC:
            case TYPE_HDMI_EARC:
                return ARC;
            default:
                return -1;
        }
    }

    /** Index of the output Android plays media on among these device types, or -1 for the TV speakers. */
    static int pick(int[] types) {
        int best = -1, bestKind = SPEAKERS;
        for (int i = 0; i < types.length; i++) {
            int k = kind(types[i]);
            if (k > bestKind) {
                best = i;
                bestKind = k;
            }
        }
        return best;
    }
}

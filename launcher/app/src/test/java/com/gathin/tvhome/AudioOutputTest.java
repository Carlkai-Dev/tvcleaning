package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import android.media.AudioDeviceInfo;

import org.junit.Test;

/** show_audio_output names the output Android plays media on: Bluetooth, then wired, then ARC, then the speakers. */
public class AudioOutputTest {
    private static final int SPEAKER = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
    private static final int A2DP = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
    private static final int ARC = AudioDeviceInfo.TYPE_HDMI_ARC;
    private static final int EARC = 29;
    private static final int WIRED = AudioDeviceInfo.TYPE_WIRED_HEADPHONES;

    @Test
    public void speakersWhenNothingElseIsConnected() {
        assertEquals(-1, AudioOutput.pick(new int[]{SPEAKER}));
        assertEquals(-1, AudioOutput.pick(new int[]{}));
        assertEquals(-1, AudioOutput.pick(new int[]{SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE}));
    }

    @Test
    public void anArcReceiverOutranksTheSpeakers() {
        assertEquals(1, AudioOutput.pick(new int[]{SPEAKER, ARC}));
        assertEquals(1, AudioOutput.pick(new int[]{SPEAKER, EARC}));
    }

    @Test
    public void bluetoothOutranksEverythingElse() {
        // The Hisense 75U75N case: speakers, Bluetooth headphones and an eARC soundbar all connected.
        assertEquals(1, AudioOutput.pick(new int[]{SPEAKER, A2DP, EARC}));
        assertEquals(2, AudioOutput.pick(new int[]{SPEAKER, WIRED, A2DP}));
    }

    @Test
    public void wiredHeadphonesOutrankArc() {
        assertEquals(2, AudioOutput.pick(new int[]{ARC, SPEAKER, WIRED}));
    }

    @Test
    public void kindsOfTypes() {
        assertEquals(AudioOutput.BLUETOOTH, AudioOutput.kind(A2DP));
        assertEquals(AudioOutput.BLUETOOTH, AudioOutput.kind(26));   // BLE headset
        assertEquals(AudioOutput.WIRED, AudioOutput.kind(AudioDeviceInfo.TYPE_USB_HEADSET));
        assertEquals(AudioOutput.ARC, AudioOutput.kind(EARC));
        assertEquals(-1, AudioOutput.kind(SPEAKER));
    }
}

package eu.siacs.conversations.services;

import java.util.Set;

/** Explicit phone routing must survive Bluetooth audio/device callbacks. */
final class CallAudioRoutePolicy {
    static CallIntegration.AudioDevice choose(
            Set<CallIntegration.AudioDevice> available,
            CallIntegration.AudioDevice requested,
            CallIntegration.AudioDevice defaultDevice) {
        if (requested != CallIntegration.AudioDevice.NONE && available.contains(requested)) {
            return requested;
        }
        if (available.contains(CallIntegration.AudioDevice.BLUETOOTH)) {
            return CallIntegration.AudioDevice.BLUETOOTH;
        }
        if (available.contains(CallIntegration.AudioDevice.WIRED_HEADSET)) {
            return CallIntegration.AudioDevice.WIRED_HEADSET;
        }
        if (available.contains(defaultDevice)) return defaultDevice;
        if (available.contains(CallIntegration.AudioDevice.SPEAKER_PHONE)) {
            return CallIntegration.AudioDevice.SPEAKER_PHONE;
        }
        return available.stream().findFirst().orElse(CallIntegration.AudioDevice.NONE);
    }
}

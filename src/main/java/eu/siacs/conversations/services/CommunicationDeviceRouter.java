package eu.siacs.conversations.services;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import java.util.LinkedHashMap;

/** AudioManager routing for calls that aren't owned by Telecom, Android 12+. */
@RequiresApi(31)
final class CommunicationDeviceRouter {
    private final Context context;
    private final AudioManager manager;
    private final AppRTCAudioManager.AudioManagerEvents events;
    private final AudioManager.OnCommunicationDeviceChangedListener routeListener = this::onRouteChanged;
    private final AudioDeviceCallback devicesListener = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) { refresh(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) { refresh(); }
    };
    private CallIntegration.AudioDevice requested = CallIntegration.AudioDevice.NONE;
    private CallIntegration.AudioDevice pending = CallIntegration.AudioDevice.NONE;
    private CallIntegration.AudioDevice defaultDevice;
    private boolean running;

    CommunicationDeviceRouter(Context context, AudioManager manager,
            CallIntegration.AudioDevice defaultDevice, AppRTCAudioManager.AudioManagerEvents events) {
        this.context = context;
        this.manager = manager;
        this.defaultDevice = defaultDevice;
        this.events = events;
    }

    void start() {
        running = true;
        manager.addOnCommunicationDeviceChangedListener(context.getMainExecutor(), routeListener);
        manager.registerAudioDeviceCallback(devicesListener, new Handler(Looper.getMainLooper()));
        refresh();
    }

    void stop() {
        running = false;
        manager.removeOnCommunicationDeviceChangedListener(routeListener);
        manager.unregisterAudioDeviceCallback(devicesListener);
        manager.clearCommunicationDevice();
    }

    void setDefault(CallIntegration.AudioDevice device) {
        defaultDevice = device;
        refresh();
    }

    void select(CallIntegration.AudioDevice device) {
        requested = device;
        if (device == CallIntegration.AudioDevice.EARPIECE
                || device == CallIntegration.AudioDevice.SPEAKER_PHONE) defaultDevice = device;
        refresh();
    }

    private void onRouteChanged(AudioDeviceInfo device) {
        if (!running) return;
        // Device callbacks can be queued behind a newer transfer. Read the
        // actual route instead of letting an older Bluetooth event reclaim it.
        final var current = manager.getCommunicationDevice();
        final var route = current == null ? CallIntegration.AudioDevice.NONE : classify(current.getType());
        if (route == pending) {
            pending = CallIntegration.AudioDevice.NONE;
        } else if (pending == CallIntegration.AudioDevice.NONE && route != CallIntegration.AudioDevice.NONE) {
            // Respect a transfer made by the headset or Android's own route picker.
            requested = route;
        }
        refresh();
    }

    private void refresh() {
        if (!running) return;
        final var devices = new LinkedHashMap<CallIntegration.AudioDevice, AudioDeviceInfo>();
        for (final var device : manager.getAvailableCommunicationDevices()) {
            final var type = classify(device.getType());
            if (type != CallIntegration.AudioDevice.NONE) devices.putIfAbsent(type, device);
        }
        if (!devices.containsKey(requested)) requested = CallIntegration.AudioDevice.NONE;
        final var target = CallAudioRoutePolicy.choose(devices.keySet(), requested, defaultDevice);
        final var current = manager.getCommunicationDevice();
        final var actual = current == null ? CallIntegration.AudioDevice.NONE : classify(current.getType());
        final var destination = devices.get(target);
        if (destination != null && actual != target) {
            pending = target;
            try {
                if (!manager.setCommunicationDevice(destination)) {
                    pending = CallIntegration.AudioDevice.NONE;
                    routeFailed(target, null);
                }
            } catch (IllegalArgumentException | SecurityException e) {
                pending = CallIntegration.AudioDevice.NONE;
                routeFailed(target, e);
            }
        }
        final var confirmed = manager.getCommunicationDevice();
        if (confirmed != null && classify(confirmed.getType()) == pending) {
            pending = CallIntegration.AudioDevice.NONE;
        }
        events.onAudioDeviceChanged(confirmed == null ? CallIntegration.AudioDevice.NONE
                : classify(confirmed.getType()), devices.keySet());
    }

    private void routeFailed(CallIntegration.AudioDevice target, Exception error) {
        Log.w(Config.LOGTAG, "Could not route communication audio to " + target, error);
        if (requested != CallIntegration.AudioDevice.NONE) {
            Toast.makeText(context, R.string.call_audio_route_failed, Toast.LENGTH_SHORT).show();
        }
    }

    static CallIntegration.AudioDevice classify(int type) {
        return switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> CallIntegration.AudioDevice.EARPIECE;
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> CallIntegration.AudioDevice.SPEAKER_PHONE;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_HEARING_AID -> CallIntegration.AudioDevice.BLUETOOTH;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> CallIntegration.AudioDevice.WIRED_HEADSET;
            default -> CallIntegration.AudioDevice.NONE;
        };
    }
}

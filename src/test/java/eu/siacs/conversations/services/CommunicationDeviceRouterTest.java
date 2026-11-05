package eu.siacs.conversations.services;

import static org.junit.Assert.*;
import static eu.siacs.conversations.services.CallIntegration.AudioDevice.*;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Looper;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.AudioDeviceInfoBuilder;
import org.robolectric.shadows.ShadowAudioManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class CommunicationDeviceRouterTest {
    private AudioManager manager;
    private ShadowAudioManager shadow;
    private CommunicationDeviceRouter router;
    private CallIntegration.AudioDevice reported;
    private final AudioDeviceInfo speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    private final AudioDeviceInfo earpiece = device(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
    private final AudioDeviceInfo bluetooth = device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO);

    private static AudioDeviceInfo device(int type) {
        return AudioDeviceInfoBuilder.newBuilder().setType(type).build();
    }

    @Before public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        manager = context.getSystemService(AudioManager.class);
        shadow = Shadows.shadowOf(manager);
        shadow.setAvailableCommunicationDevices(List.of(speaker, earpiece, bluetooth));
        router = new CommunicationDeviceRouter(context, manager, EARPIECE,
                (selected, available) -> reported = selected);
        router.start();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void speakerAndEarpieceOverrideConnectedBluetoothAndCanSwitchBack() {
        assertSame(bluetooth, manager.getCommunicationDevice());
        router.select(SPEAKER_PHONE);
        assertSame(speaker, manager.getCommunicationDevice());
        shadow.callOnCommunicationDeviceChangedListeners(speaker);
        assertSame(speaker, manager.getCommunicationDevice());
        router.select(EARPIECE);
        assertSame(earpiece, manager.getCommunicationDevice());
        router.select(BLUETOOTH);
        assertSame(bluetooth, manager.getCommunicationDevice());
        router.stop();
        assertNull(manager.getCommunicationDevice());
    }

    @Test public void bluetoothDeviceUpdatesDoNotStealExplicitPhoneRoute() {
        router.select(EARPIECE);
        shadow.removeAvailableCommunicationDevice(bluetooth, true);
        shadow.addAvailableCommunicationDevice(bluetooth, true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertSame(earpiece, manager.getCommunicationDevice());
        router.stop();
    }

    @Test public void disconnectingSelectedBluetoothFallsBackToPhone() {
        shadow.removeAvailableCommunicationDevice(bluetooth, true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertSame(earpiece, manager.getCommunicationDevice());
        router.stop();
    }

    @Test public void rejectedRouteIsNotReportedAsSuccessful() {
        shadow.lockCommunicationDevice(true);
        router.select(SPEAKER_PHONE);
        assertSame(bluetooth, manager.getCommunicationDevice());
        assertEquals(BLUETOOTH, reported);
        shadow.lockCommunicationDevice(false);
        router.stop();
    }

    @Test public void externalTransferToPhoneDoesNotImmediatelyReopenBluetooth() {
        shadow.callOnCommunicationDeviceChangedListeners(bluetooth);
        manager.setCommunicationDevice(earpiece);
        shadow.callOnCommunicationDeviceChangedListeners(earpiece);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertSame(earpiece, manager.getCommunicationDevice());
        router.stop();
    }

    @Test public void explicitPhoneSelectionWinsPolicyWithoutBluetoothDisconnect() {
        Set<CallIntegration.AudioDevice> available = Set.of(EARPIECE, SPEAKER_PHONE, BLUETOOTH);
        assertEquals(SPEAKER_PHONE, CallAudioRoutePolicy.choose(available, SPEAKER_PHONE, EARPIECE));
        assertEquals(EARPIECE, CallAudioRoutePolicy.choose(available, EARPIECE, EARPIECE));
        assertEquals(BLUETOOTH, CallAudioRoutePolicy.choose(available, NONE, EARPIECE));
        assertEquals(BLUETOOTH, CommunicationDeviceRouter.classify(AudioDeviceInfo.TYPE_BLE_HEADSET));
    }

    @Test public void disconnectFallsBackToLastExplicitPhoneOutput() {
        router.select(SPEAKER_PHONE);
        router.select(BLUETOOTH);
        shadow.removeAvailableCommunicationDevice(bluetooth, true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertSame(speaker, manager.getCommunicationDevice());
        router.stop();
    }
}

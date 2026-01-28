package eu.siacs.conversations.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import de.gultsch.common.MiniUri;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.activity.result.ScanQrCode;
import eu.siacs.conversations.utils.ScanResultProcessor;
import eu.siacs.conversations.utils.XmppUriLauncher;

public abstract class QrCodeScanningXmppActivity extends XmppActivity {

    private final ActivityResultLauncher<Void> scanQrCode =
            registerForActivityResult(
                    new ScanQrCode(),
                    result -> {
                        if (result == null) {
                            return;
                        }
                        onQrCodeScanned(result);
                    });
    private final ActivityResultLauncher<String> requestPermissionScanQr =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    result -> {
                        if (Boolean.TRUE.equals(result)) {
                            scanQrCode.launch(null);
                        } else {
                            Toast.makeText(
                                            this,
                                            R.string.qr_code_scanner_needs_access_to_camera,
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    });

    protected void requestPermissionAndScanQrCode() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            this.scanQrCode.launch(null);
        } else {
            this.requestPermissionScanQr.launch(Manifest.permission.CAMERA);
        }
    }

    private void onQrCodeScanned(final String code) {
        final var scanResultProcessor = new ScanResultProcessor(this);
        final var future = scanResultProcessor.process(code);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final MiniUri result) {
                        onMiniUriScanned(result);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "error: ", t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void onMiniUriScanned(final MiniUri miniUri) {
        if (miniUri instanceof MiniUri.Xmpp xmpp) {
            final var uriLauncher = new XmppUriLauncher(this, true);
            uriLauncher.launch(xmpp);
        } else {
            Log.d(Config.LOGTAG, "mini uri result: " + miniUri);
        }
    }
}

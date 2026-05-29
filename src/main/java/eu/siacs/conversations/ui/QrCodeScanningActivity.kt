package eu.siacs.conversations.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.activity.result.ScanQrCode

abstract class QrCodeScanningActivity : XmppActivity() {

    private val scanQrCode: ActivityResultLauncher<Void?> =
        registerForActivityResult(ScanQrCode()) { result ->
            if (result == null) {
                return@registerForActivityResult
            }
            onQrCodeScanned(result)
        }

    private val requestPermissionScanQr: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            if (java.lang.Boolean.TRUE == result) {
                scanQrCode.launch(null)
            } else {
                Toast.makeText(
                    this,
                    R.string.qr_code_scanner_needs_access_to_camera,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    protected fun requestPermissionAndScanQrCode() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            this.scanQrCode.launch(null)
        } else {
            this.requestPermissionScanQr.launch(Manifest.permission.CAMERA)
        }
    }

    abstract fun onQrCodeScanned(code: String)
}

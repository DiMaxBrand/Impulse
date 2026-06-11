package eu.siacs.conversations.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import de.gultsch.common.MiniUri
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.DialogOpenBrowserBinding
import eu.siacs.conversations.utils.ScanResultProcessor
import eu.siacs.conversations.utils.XmppUriLauncher

abstract class QrCodeProcessingActivity : QrCodeScanningActivity() {

    override fun onQrCodeScanned(code: String) {
        val scanResultProcessor = ScanResultProcessor(this)
        val future = scanResultProcessor.process(code)
        Futures.addCallback(
            future,
            object : FutureCallback<MiniUri> {
                override fun onSuccess(result: MiniUri) {
                    onMiniUriScanned(result)
                }

                override fun onFailure(t: Throwable) {
                    Log.d(Config.LOGTAG, "did not recognize qr code content", t)
                    Toast.makeText(
                        this@QrCodeProcessingActivity,
                        R.string.invalid_barcode,
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun onMiniUriScanned(miniUri: MiniUri) {
        if (miniUri is MiniUri.Xmpp) {
            val uriLauncher = XmppUriLauncher(this, true)
            uriLauncher.launch(miniUri)
        } else if (miniUri is MiniUri.Http) {
            val builder = MaterialAlertDialogBuilder(this)
            builder.setTitle(R.string.qr_code_contains_link)
            val binding: DialogOpenBrowserBinding =
                DataBindingUtil.inflate(
                    layoutInflater, R.layout.dialog_open_browser, null, false
                )
            binding.uri.setText(miniUri.asUri().toString())
            builder.setView(binding.root)
            builder.setNegativeButton(R.string.cancel, null)
            builder.setPositiveButton(R.string.open_browser) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, miniUri.asUri())
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        this,
                        R.string.no_application_found_to_open_link,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            builder.create().show()
        } else {
            Log.d(Config.LOGTAG, "mini uri result: $miniUri")
            Toast.makeText(
                this@QrCodeProcessingActivity,
                R.string.invalid_barcode,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

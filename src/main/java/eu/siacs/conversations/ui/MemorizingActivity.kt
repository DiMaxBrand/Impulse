package eu.siacs.conversations.ui

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.MTMDecision
import eu.siacs.conversations.services.MemorizingTrustManager
import eu.siacs.conversations.ui.util.SettingsUtils
import java.util.logging.Level
import java.util.logging.Logger

class MemorizingActivity : AppCompatActivity(), DialogInterface.OnClickListener, DialogInterface.OnCancelListener {

    private var decisionId: Int = 0
    private lateinit var dialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        LOGGER.log(Level.FINE, "onCreate")
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        SettingsUtils.applyScreenshotSetting(this)

        val i: Intent = intent
        decisionId = i.getIntExtra(MemorizingTrustManager.DECISION_INTENT_ID, MTMDecision.DECISION_INVALID)
        val titleId = i.getIntExtra(MemorizingTrustManager.DECISION_TITLE_ID, R.string.mtm_accept_cert)
        val cert = i.getStringExtra(MemorizingTrustManager.DECISION_INTENT_CERT)
        LOGGER.log(Level.FINE, "onResume with ${i.extras} decId=$decisionId data: ${i.data}")
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(titleId)
            .setMessage(cert)
            .setPositiveButton(R.string.always, this)
            .setNeutralButton(R.string.once, this)
            .setNegativeButton(R.string.cancel, this)
            .setOnCancelListener(this)
            .create()
        dialog.show()
    }

    override fun onPause() {
        if (dialog.isShowing) dialog.dismiss()
        super.onPause()
    }

    private fun sendDecision(decision: Int) {
        LOGGER.log(Level.FINE, "Sending decision: $decision")
        MemorizingTrustManager.interactResult(decisionId, decision)
        finish()
    }

    // react on AlertDialog button press
    override fun onClick(dialog: DialogInterface, btnId: Int) {
        val decision: Int
        dialog.dismiss()
        decision = when (btnId) {
            DialogInterface.BUTTON_POSITIVE -> MTMDecision.DECISION_ALWAYS
            DialogInterface.BUTTON_NEUTRAL -> MTMDecision.DECISION_ONCE
            else -> MTMDecision.DECISION_ABORT
        }
        sendDecision(decision)
    }

    override fun onCancel(dialog: DialogInterface) {
        sendDecision(MTMDecision.DECISION_ABORT)
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(MemorizingActivity::class.java.name)
    }
}

package eu.siacs.conversations.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.material.textfield.TextInputLayout
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ActivityChangePasswordBinding
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.ui.widget.DisabledActionModeCallback

class ChangePasswordActivity : XmppActivity() {

    private lateinit var binding: ActivityChangePasswordBinding

    private val passwordChangedCallback: FutureCallback<Void?> = object : FutureCallback<Void?> {
        override fun onSuccess(result: Void?) {
            Toast.makeText(
                this@ChangePasswordActivity,
                R.string.password_changed,
                Toast.LENGTH_LONG
            ).show()
            finish()
        }

        override fun onFailure(t: Throwable) {
            Log.d(Config.LOGTAG, "could not change password", t)
            binding.newPasswordLayout.error = getString(R.string.could_not_change_password)
            binding.changePasswordButton.isEnabled = true
            binding.changePasswordButton.setText(R.string.change_password)
        }
    }

    private val mOnChangePasswordButtonClicked = View.OnClickListener { _ ->
        val account = mAccount ?: return@OnClickListener
        val currentPassword = binding.currentPassword.text.toString()
        val newPassword = binding.newPassword.text.toString()
        if (!account.isOptionSet(Account.OPTION_MAGIC_CREATE)
            && currentPassword != account.password
        ) {
            binding.currentPassword.requestFocus()
            binding.currentPasswordLayout.error = getString(R.string.account_status_unauthorized)
            removeErrorsOnAllBut(binding.currentPasswordLayout)
        } else if (newPassword.trim().isEmpty()) {
            binding.newPassword.requestFocus()
            binding.newPasswordLayout.error = getString(R.string.password_should_not_be_empty)
            removeErrorsOnAllBut(binding.newPasswordLayout)
        } else {
            binding.currentPasswordLayout.error = null
            binding.newPasswordLayout.error = null
            val future = xmppConnectionService.updateAccountPasswordOnServer(account, newPassword)
            Futures.addCallback(
                future,
                this@ChangePasswordActivity.passwordChangedCallback,
                ContextCompat.getMainExecutor(application)
            )
            binding.changePasswordButton.isEnabled = false
            binding.changePasswordButton.setText(R.string.updating)
        }
    }

    private var mAccount: Account? = null

    override fun onBackendConnected() {
        this.mAccount = extractAccount(intent)
        if (this.mAccount != null && this.mAccount!!.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
            this.binding.currentPasswordLayout.visibility = View.GONE
        } else {
            this.binding.currentPasswordLayout.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_change_password)
        Activities.setStatusAndNavigationBarColors(this, binding.root)
        setSupportActionBar(binding.toolbar)
        configureActionBar(supportActionBar)
        binding.cancelButton.setOnClickListener { finish() }
        binding.changePasswordButton.setOnClickListener(this.mOnChangePasswordButtonClicked)
        binding.currentPassword.setCustomSelectionActionModeCallback(DisabledActionModeCallback())
        binding.newPassword.setCustomSelectionActionModeCallback(DisabledActionModeCallback())
    }

    override fun onStart() {
        super.onStart()
        val intent: Intent? = intent
        val password = intent?.getStringExtra("password")
        if (password != null) {
            binding.newPassword.editableText.clear()
            binding.newPassword.editableText.append(password)
        }
    }

    private fun removeErrorsOnAllBut(exception: TextInputLayout) {
        if (this.binding.currentPasswordLayout !== exception) {
            this.binding.currentPasswordLayout.isErrorEnabled = false
            this.binding.currentPasswordLayout.error = null
        }
        if (this.binding.newPasswordLayout !== exception) {
            this.binding.newPasswordLayout.isErrorEnabled = false
            this.binding.newPasswordLayout.error = null
        }
    }

    override fun refreshUiReal() {}
}

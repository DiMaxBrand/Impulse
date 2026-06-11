package eu.siacs.conversations.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ActivityManageAccountsBinding
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.ui.adapter.AccountAdapter
import java.util.ArrayList

class ChooseAccountForProfilePictureActivity : XmppActivity() {

    protected val accountList: MutableList<Account> = ArrayList()
    protected lateinit var mAccountAdapter: AccountAdapter

    override fun refreshUiReal() {
        loadEnabledAccounts()
        mAccountAdapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityManageAccountsBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_manage_accounts)
        Activities.setStatusAndNavigationBarColors(this, binding.root)
        setSupportActionBar(binding.toolbar)
        configureActionBar(supportActionBar, false)
        this.mAccountAdapter = AccountAdapter(this, accountList, false)
        binding.accountList.adapter = this.mAccountAdapter
        binding.accountList.setOnItemClickListener { _, _, position, _ ->
            val account = accountList[position]
            goToProfilePictureActivity(account)
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onBackendConnected() {
        loadEnabledAccounts()
        if (accountList.size == 1) {
            goToProfilePictureActivity(accountList[0])
            return
        }
        mAccountAdapter.notifyDataSetChanged()
    }

    private fun loadEnabledAccounts() {
        accountList.clear()
        for (account in xmppConnectionService.getAccounts()) {
            if (account.isEnabled) {
                accountList.add(account)
            }
        }
    }

    private fun goToProfilePictureActivity(account: Account) {
        val startIntent = intent
        val uri: Uri? = startIntent?.data
        if (uri != null) {
            val intent = Intent(this, PublishProfilePictureActivity::class.java)
            intent.action = Intent.ACTION_ATTACH_DATA
            intent.putExtra(EXTRA_ACCOUNT, account.jid.asBareJid().toString())
            intent.data = uri
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                startActivity(intent)
            } catch (e: SecurityException) {
                Toast.makeText(
                    this,
                    R.string.sharing_application_not_grant_permission,
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
        finish()
    }
}

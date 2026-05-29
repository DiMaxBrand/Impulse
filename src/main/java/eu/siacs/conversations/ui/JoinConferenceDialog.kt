package eu.siacs.conversations.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.DialogJoinConferenceBinding
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter
import eu.siacs.conversations.ui.interfaces.OnBackendConnected
import eu.siacs.conversations.ui.util.DelayedHintHelper
import java.util.ArrayList

class JoinConferenceDialog : DialogFragment(), OnBackendConnected {

    private var mListener: JoinConferenceDialogListener? = null
    private var knownHostsAdapter: KnownHostsAdapter? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        builder.setTitle(R.string.join_public_channel)
        val binding: DialogJoinConferenceBinding =
            DataBindingUtil.inflate(
                requireActivity().layoutInflater,
                R.layout.dialog_join_conference,
                null,
                false
            )
        DelayedHintHelper.setHint(R.string.channel_full_jid_example, binding.jid)
        this.knownHostsAdapter = KnownHostsAdapter(requireActivity(), R.layout.item_autocomplete)
        binding.jid.setAdapter(knownHostsAdapter)
        val prefilledJid = requireArguments().getString(PREFILLED_JID_KEY)
        if (prefilledJid != null) {
            binding.jid.append(prefilledJid)
        }
        StartConversationActivity.populateAccountSpinner(
            requireActivity(),
            requireArguments().getStringArrayList(ACCOUNTS_LIST_KEY),
            binding.account
        )
        builder.setView(binding.root)
        builder.setPositiveButton(R.string.join, null)
        builder.setNegativeButton(R.string.cancel, null)
        val dialog: AlertDialog = builder.create()
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            mListener?.onJoinDialogPositiveClick(
                dialog,
                binding.account,
                binding.accountJidLayout,
                binding.jid
            )
        }
        binding.jid.setOnEditorActionListener { _, _, _ ->
            mListener?.onJoinDialogPositiveClick(
                dialog, binding.account, binding.accountJidLayout, binding.jid
            )
            true
        }
        return dialog
    }

    override fun onBackendConnected() {
        refreshKnownHosts()
    }

    private fun refreshKnownHosts() {
        val activity: Activity? = activity
        if (activity is XmppActivity) {
            val hosts = activity.xmppConnectionService.getKnownConferenceHosts()
            this.knownHostsAdapter?.refresh(hosts)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            mListener = context as JoinConferenceDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement JoinConferenceDialogListener")
        }
    }

    override fun onDestroyView() {
        val dialog = dialog
        if (dialog != null && retainInstance) {
            dialog.setDismissMessage(null)
        }
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        val activity: Activity? = activity
        if (activity is XmppActivity && activity.xmppConnectionService != null) {
            refreshKnownHosts()
        }
    }

    interface JoinConferenceDialogListener {
        fun onJoinDialogPositiveClick(
            dialog: Dialog,
            spinner: AutoCompleteTextView,
            jidLayout: TextInputLayout,
            jid: AutoCompleteTextView
        )
    }

    companion object {
        private const val PREFILLED_JID_KEY = "prefilled_jid"
        private const val ACCOUNTS_LIST_KEY = "activated_accounts_list"

        @JvmStatic
        fun newInstance(prefilledJid: String?, accounts: List<String>): JoinConferenceDialog {
            val dialog = JoinConferenceDialog()
            val bundle = Bundle()
            bundle.putString(PREFILLED_JID_KEY, prefilledJid)
            bundle.putStringArrayList(ACCOUNTS_LIST_KEY, ArrayList(accounts))
            dialog.arguments = bundle
            return dialog
        }
    }
}

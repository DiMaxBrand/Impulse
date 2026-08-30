package eu.siacs.conversations.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.widget.AutoCompleteTextView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.DialogCreateConferenceBinding
import eu.siacs.conversations.ui.util.DelayedHintHelper
import java.util.ArrayList

class CreatePrivateGroupChatDialog : DialogFragment() {

    private var mListener: CreateConferenceDialogListener? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        builder.setTitle(R.string.create_private_group_chat)
        val binding: DialogCreateConferenceBinding =
            DataBindingUtil.inflate(
                requireActivity().layoutInflater,
                R.layout.dialog_create_conference,
                null,
                false
            )
        val mActivatedAccounts = requireArguments().getStringArrayList(ACCOUNTS_LIST_KEY)
        StartConversationActivity.populateAccountSpinner(
            requireActivity(), mActivatedAccounts, binding.account
        )
        builder.setView(binding.root)
        builder.setPositiveButton(R.string.choose_participants, null)
        builder.setNegativeButton(R.string.cancel, null)
        DelayedHintHelper.setHint(R.string.providing_a_name_is_optional, binding.groupChatName)
        val dialog = builder.create()
        binding.everyoneCanJoin.setOnCheckedChangeListener { _, isChecked ->
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setText(
                if (isChecked) R.string.create else R.string.choose_participants
            )
        }
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                val membersOnly = !binding.everyoneCanJoin.isChecked
                mListener?.onCreateDialogPositiveClick(
                    binding.account,
                    binding.groupChatName.text.toString().trim(),
                    membersOnly
                )
                dialog.dismiss()
            }
        }
        binding.groupChatName.setOnEditorActionListener { _, _, _ ->
            val membersOnly = !binding.everyoneCanJoin.isChecked
            mListener?.onCreateDialogPositiveClick(
                binding.account,
                binding.groupChatName.text.toString().trim(),
                membersOnly
            )
            dialog.dismiss()
            true
        }
        return dialog
    }

    interface CreateConferenceDialogListener {
        fun onCreateDialogPositiveClick(spinner: AutoCompleteTextView, subject: String, membersOnly: Boolean)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            mListener = context as CreateConferenceDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement CreateConferenceDialogListener")
        }
    }

    override fun onDestroyView() {
        val dialog = dialog
        if (dialog != null && retainInstance) {
            dialog.setDismissMessage(null)
        }
        super.onDestroyView()
    }

    companion object {
        private const val ACCOUNTS_LIST_KEY = "activated_accounts_list"

        @JvmStatic
        fun newInstance(accounts: List<String>): CreatePrivateGroupChatDialog {
            val dialog = CreatePrivateGroupChatDialog()
            val bundle = Bundle()
            bundle.putStringArrayList(ACCOUNTS_LIST_KEY, ArrayList(accounts))
            dialog.arguments = bundle
            return dialog
        }
    }
}

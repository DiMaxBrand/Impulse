package eu.siacs.conversations.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.BottomSheetDeleteMessageBinding
import eu.siacs.conversations.entities.Message

class DeleteMessageBottomSheet(
    private val message: Message,
    private val onDeleteForEveryone: Runnable,
    private val onDeleteForMyself: Runnable,
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDeleteMessageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetDeleteMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (message.isDeleted) {
            binding.title.setText(R.string.delete_leftover_message_title)
            binding.description.setText(R.string.delete_leftover_message_explanation)
            binding.description.visibility = View.VISIBLE
        }
        val canRetract = message.status != Message.STATUS_RECEIVED
        binding.btnDeleteEveryone.isEnabled = canRetract
        binding.btnDeleteEveryone.setOnClickListener {
            dismiss()
            onDeleteForEveryone.run()
        }
        binding.btnDeleteMyself.setOnClickListener {
            dismiss()
            onDeleteForMyself.run()
        }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

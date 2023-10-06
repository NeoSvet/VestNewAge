package ru.neosvet.vestnewage.view.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import ru.neosvet.vestnewage.databinding.PromptDialogBinding
import ru.neosvet.vestnewage.utils.Const

sealed class PromptResult {
    data object Yes : PromptResult()
    data object No : PromptResult()
    data object Cancel : PromptResult()
}

class PromptDialog : BottomSheetDialogFragment() {
    companion object {
        fun newInstance(message: String) = PromptDialog().apply {
            arguments = Bundle().apply {
                putString(Const.DIALOG, message)
            }
        }

        private val resultChannel = Channel<PromptResult>()
        val result = resultChannel.receiveAsFlow()
    }

    private var binding: PromptDialogBinding? = null
    private var isSend = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = PromptDialogBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (binding != null && !isSend)
            resultChannel.trySend(PromptResult.Cancel)
        super.onDismiss(dialog)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.let {
            val sheet = it as BottomSheetDialog
            sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        arguments?.let {
            val message = it.getString(Const.DIALOG) ?: ""
            binding?.tvMessage?.text = message
        }
        setButtons()
    }

    private fun setButtons() = binding?.run {
        btnNo.setOnClickListener {
            isSend = true
            resultChannel.trySend(PromptResult.No)
            dismiss()
        }
        btnYes.setOnClickListener {
            isSend = true
            resultChannel.trySend(PromptResult.Yes)
            dismiss()
        }
    }
}
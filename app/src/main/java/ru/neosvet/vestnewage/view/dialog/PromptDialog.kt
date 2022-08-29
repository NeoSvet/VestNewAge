package ru.neosvet.vestnewage.view.dialog

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

class PromptDialog : BottomSheetDialogFragment() {
    companion object {
        fun newInstance(message: String) = PromptDialog().apply {
            arguments = Bundle().apply {
                putString(Const.DIALOG, message)
            }
        }

        private val mresult = Channel<Boolean>()
        val result = mresult.receiveAsFlow()
    }

    private var binding: PromptDialogBinding? = null

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.let {
            val sheet = it as BottomSheetDialog
            sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        arguments?.let {
            val message = it.getString(Const.DIALOG) ?: ""
            binding?.tvMessage?.setText(message)
        }
        setButtons()
    }

    private fun setButtons() = binding?.run {
        btnNo.setOnClickListener {
            mresult.trySend(false)
            dialog?.dismiss()
        }
        btnYes.setOnClickListener {
            mresult.trySend(true)
            dismiss()
        }
    }
}
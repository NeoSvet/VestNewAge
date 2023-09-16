package ru.neosvet.vestnewage.view.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import ru.neosvet.vestnewage.databinding.InputDialogBinding
import ru.neosvet.vestnewage.utils.Const

class InputDialog : BottomSheetDialogFragment() {
    companion object {
        fun newInstance(message: String, value: String) = InputDialog().apply {
            arguments = Bundle().apply {
                putString(Const.TITLE, message)
                putString(Const.DIALOG, value)
            }
        }

        private val resultChannel = Channel<String?>()
        val result = resultChannel.receiveAsFlow()
    }

    private var binding: InputDialogBinding? = null
    private var value = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = InputDialogBinding.inflate(inflater, container, false).also {
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
            val message = it.getString(Const.TITLE) ?: ""
            value = it.getString(Const.DIALOG) ?: ""
            binding?.run {
                tilInput.hint = message
                etInput.setText(value)
                etInput.selectAll()
            }
            arguments = null
        }
        setViews()
    }

    private fun setViews() = binding?.run {
        etInput.doAfterTextChanged {
            val s = it.toString()
            btnOk.isEnabled = s.isNotEmpty() && s != value
        }
        btnCancel.setOnClickListener {
            resultChannel.trySend(null)
            dialog?.dismiss()
        }
        btnOk.setOnClickListener {
            resultChannel.trySend(binding?.etInput?.text?.toString()?.trim())
            dismiss()
        }
    }
}
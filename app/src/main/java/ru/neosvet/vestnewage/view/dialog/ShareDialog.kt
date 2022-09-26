package ru.neosvet.vestnewage.view.dialog

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CheckItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.ShareDialogBinding
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.utils.date
import ru.neosvet.vestnewage.utils.noHasDate
import ru.neosvet.vestnewage.view.list.CheckAdapter

class ShareDialog : BottomSheetDialogFragment() {
    companion object {
        fun newInstance(link: String) = ShareDialog().apply {
            arguments = Bundle().apply { putString(Const.LINK, link) }
        }
    }

    private var link = ""
    private lateinit var adapter: CheckAdapter
    private var binding: ShareDialogBinding? = null
    private var options = BooleanArray(1)
    private val selectedTitle: Boolean
        get() = options[0]
    private val selectedContent: Boolean
        get() = options[1]
    private val selectedLink: Boolean
        get() = options[2]

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ShareDialogBinding.inflate(inflater, container, false).also {
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
            link = it.getString(Const.LINK) ?: ""
        }
        options = savedInstanceState?.getBooleanArray(Const.SEARCH)
            ?: BooleanArray(3) { i -> i % 2 != 1 }
        setViews()
        initOptions()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBooleanArray(Const.SEARCH, options)
        super.onSaveInstanceState(outState)
    }

    private fun setViews() = binding?.run {
        bShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, getContent())
            val intent = Intent.createChooser(shareIntent, getString(R.string.share))
            startActivity(intent)
            dismiss()
        }
        bCopy.setOnClickListener {
            val ctx = requireContext()
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.app_name), getContent())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ctx, getString(R.string.copied), Toast.LENGTH_LONG).show()
            dismiss()
        }
        val enabled = options.contains(true)
        bShare.isEnabled = enabled
        bCopy.isEnabled = enabled
    }

    private fun getContent(): String {
        val storage = PageStorage()
        storage.open(link)
        val sb = StringBuilder()
        if (selectedTitle || selectedContent) {
            sb.append(storage.getContentPage(link, !selectedContent))
            sb.append(if (selectedContent) Const.NN else Const.N)
        }

        if (selectedLink) when {
            storage.isDoctrine -> {
                sb.append(getString(R.string.doctrine_pages))
                sb.appendLine(link.substring(Const.DOCTRINE.length))
                sb.append(NetConst.DOCTRINE_SITE)
            }
            !storage.isOldBook -> sb.append(NetConst.SITE + link)
        }

        return sb.toString().trimEnd()
    }

    private fun initOptions() {
        val list = mutableListOf<CheckItem>()
        var i = 0
        val d = if (link.noHasDate) DateUnit.initToday()
        else DateUnit.parse(link.date)
        if (d.year < 2016) //no share link
            options[2] = false
        resources.getStringArray(R.array.share_list).forEach {
            list.add(CheckItem(title = it, isChecked = options[i]))
            i++
        }
        adapter = CheckAdapter(list, false, this::checkOption)
        if (d.year < 2016) //no share link
            adapter.sizeCorrector = 1
        val span = if (ScreenUtils.isLand || ScreenUtils.isWide) 3 else 1
        binding?.run {
            rvOptions.layoutManager = GridLayoutManager(requireContext(), span)
            rvOptions.adapter = adapter
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun checkOption(index: Int, checked: Boolean): Int {
        options[index] = checked
        val enabled = options.contains(true)
        binding?.run {
            bShare.isEnabled = enabled
            bCopy.isEnabled = enabled
        }
        return index
    }
}
package ru.neosvet.vestnewage.view.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CheckItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.ShareDialogBinding
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.date
import ru.neosvet.vestnewage.view.list.CheckAdapter

class ShareDialog(
    act: Activity,
    private val link: String
) : Dialog(act) {
    private lateinit var adapter: CheckAdapter
    private val binding: ShareDialogBinding by lazy {
        ShareDialogBinding.inflate(layoutInflater)
    }
    private val options = mutableListOf(true, false, true)
    private val selectedTitle: Boolean
        get() = options[0]
    private val selectedContent: Boolean
        get() = options[1]
    private val selectedLink: Boolean
        get() = options[2]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setViews()
        initOptions()
    }

    private fun setViews() = binding.run {
        bShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, getContent())
            val intent = Intent.createChooser(shareIntent, context.getString(R.string.share))
            context.startActivity(intent)
        }
        bCopy.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(context.getString(R.string.app_name), getContent())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_LONG).show()
        }
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
                sb.append(context.getString(R.string.doctrine_pages))
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
        val d = DateUnit.parse(link.date)
        if (d.year < 2016) //no share link
            options[2] = false
        context.resources.getStringArray(R.array.share_list).forEach {
            list.add(CheckItem(title = it, isChecked = options[i]))
            i++
        }
        adapter = CheckAdapter(list, false, this::checkOption)
        if (d.year < 2016) //no share link
            adapter.sizeCorrector = 1
        val rv = findViewById<RecyclerView>(R.id.rvOptions)
        rv.layoutManager = GridLayoutManager(context, 1)
        rv.adapter = adapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun checkOption(index: Int, checked: Boolean): Int {
        options[index] = checked
        return index
    }

    override fun onRestoreInstanceState(state: Bundle) {
        state.getBooleanArray(Const.SEARCH)?.let {
            options.clear()
            options.addAll(it.toMutableList())
            initOptions()
        }
    }

    override fun onSaveInstanceState(): Bundle {
        val state = Bundle()
        state.putBooleanArray(Const.SEARCH, options.toBooleanArray())
        return state
    }
}
package ru.neosvet.vestnewage.view.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CheckItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.DownloadDialogBinding
import ru.neosvet.vestnewage.helper.BookHelper
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.list.CheckAdapter

class DownloadDialog(
    private val act: MainActivity,
    private val isOtkr: Boolean = false
) : Dialog(act) {
    companion object {
        private const val BASIC_SIZE = 165000
        private const val DOCTRINE_SIZE = 215984
        private val BOOK_SIZE = arrayOf(
            1133299, 1056687, 1057064, 1010414, 972633, 485471
        )
        private val OTRK_SIZE = arrayOf(
            669063, 714810, 643777, 500279, 547107, 541682,
            546371, 591472, 560708, 759755, 555979, 285863
        )
        private val list = mutableListOf<CheckItem>()
        private val KOEF_INDEX = BOOK_SIZE.size + OTRK_SIZE.size + 3
    }

    private val binding: DownloadDialogBinding by lazy {
        DownloadDialogBinding.inflate(layoutInflater)
    }
    private lateinit var adapter: CheckAdapter
    private var curYear = 0
    private var curYearInProc = 0f
    private var midSize: Int = 0
        get() {
            if (field == 0) calcMidSize()
            return field
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        if (ScreenUtils.isLand) window?.attributes?.let { params ->
            params.width = context.resources.getDimension(R.dimen.dialog_width_land).toInt()
            window?.attributes = params
        }
        initList()
        setViews()
    }

    private fun initList() {
        fillInList()
        adapter = CheckAdapter(list, false, this::onChecked)
        binding.rvList.layoutManager = GridLayoutManager(context, ScreenUtils.span)
        binding.rvList.adapter = adapter
        setAllLabel(calcSelected() == list.size)
        calcSize()
    }

    private fun onChecked(index: Int, checked: Boolean): Int {
        list[index].isChecked = checked
        setAllLabel(calcSelected() == list.size)
        calcSize()
        return -1
    }

    private fun setViews() = binding.run {
        bAll.setOnClickListener {
            val v = calcSelected() != list.size
            for (i in list.indices)
                adapter.setChecked(i, v)
            calcSize()
            setAllLabel(v)
        }
        bOk.setOnClickListener {
            val ids = mutableListOf<Int>()
            list.forEach {
                if (it.isChecked) ids.add(it.id)
            }
            act.download(ids)
            if (isOtkr) {
                val book = BookHelper()
                book.setLoadedOtkr(true)
            }
            dismiss()
        }
    }

    private fun fillInList() {
        if (isOtkr) {
            if (list.isNotEmpty() && list[0].id != 0) return
            list.clear()
            addOtkrList()
        } else {
            if (list.isNotEmpty() && list[0].id == 0) return
            list.clear()
            addBasicList()
            val book = BookHelper()
            if (book.isLoadedOtkr())
                addOtkrList()
        }
    }

    private fun addBasicList() {
        list.add(CheckItem(context.getString(R.string.summary_site), 0, true))
        list.add(CheckItem(context.getString(R.string.doctrine_creator), 1))
        val d = DateUnit.initToday()
        curYear = d.year - 2000
        var i = curYear
        curYearInProc = d.month / 12f
        while (i > 16) {
            list.add(CheckItem(context.getString(R.string.format_poems_year).format(i), i, true))
            i--
        }
        list.add(CheckItem(context.getString(R.string.book_2016), i, true))
    }

    private fun addOtkrList() {
        var i = 15
        while (i > 4) {
            list.add(CheckItem(context.getString(R.string.format_book_year).format(i), i, true))
            i--
        }
        list.add(CheckItem(context.getString(R.string.otkroveniya_year), i, true))
    }

    private fun setAllLabel(isReset: Boolean) {
        binding.bAll.text = context.getString(
            if (isReset) R.string.reset_all
            else R.string.select_all
        )
    }

    private fun calcSelected(): Int {
        var k = 0
        list.forEach {
            if (it.isChecked) k++
        }
        return k
    }

    private fun calcSize() {
        var size = 0
        if (isOtkr) {
            for (i in list.indices)
                if (list[i].isChecked)
                    size += OTRK_SIZE[i]
        } else list.forEach {
            if (it.isChecked)
                size += when (it.id) {
                    0 -> BASIC_SIZE
                    1 -> DOCTRINE_SIZE
                    curYear -> (midSize * curYearInProc).toInt()
                    else -> {
                        val i = KOEF_INDEX - it.id
                        if (i < 0) midSize
                        else if (i < BOOK_SIZE.size) BOOK_SIZE[i]
                        else OTRK_SIZE[i - BOOK_SIZE.size]
                    }
                }
        }
        binding.tvSize.text = context.getString(R.string.format_size).format(size / 1048576f)
    }

    private fun calcMidSize() {
        var n = 0
        for (i in BOOK_SIZE)
            n += i
        midSize = n / BOOK_SIZE.size
    }
}
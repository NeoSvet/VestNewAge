package ru.neosvet.vestnewage.view.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.CheckItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.SearchDialogBinding
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.utils.SearchEngine
import ru.neosvet.vestnewage.view.list.CheckAdapter

class SearchDialog(
    private val act: Activity,
    private val parent: Parent
) : Dialog(act), DateDialog.Result {
    interface Parent {
        val modes: ArrayAdapter<String>
        val helper: SearchHelper
        fun clearHistory()
    }

    private var dateDialog: DateDialog? = null
    private var isStartDate = true
    private var start = DateUnit.putDays(parent.helper.start.timeInDays)
    private var end = DateUnit.putDays(parent.helper.end.timeInDays)
    private val options = mutableListOf<Boolean>()
    private lateinit var adapter: CheckAdapter
    private val binding: SearchDialogBinding by lazy {
        SearchDialogBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        if (ScreenUtils.isLand) window?.attributes?.let { params ->
            params.width = act.resources.getDimension(R.dimen.dialog_width_land).toInt()
            window?.attributes = params
        }

        setViews()
        options.addAll(parent.helper.options)
        initOptions()
    }

    private fun setViews() = binding.run {
        bStartRange.text = formatDate(start)
        bEndRange.text = formatDate(end)
        sMode.adapter = parent.modes
        sMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                switchDatePickers()
                val b: Byte = when {
                    position == SearchEngine.MODE_LINKS -> 5
                    options[SearchHelper.I_BY_WORDS] -> 0
                    else -> 3
                }
                if (b != adapter.sizeCorrector) {
                    adapter.sizeCorrector = b
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
        sMode.setSelection(parent.helper.mode)

        bStartRange.setOnClickListener {
            showStartDatePicker(start)
        }
        bEndRange.setOnClickListener {
            showEndDatePicker(end)
        }
        bClearRequests.setOnClickListener {
            parent.clearHistory()
        }
        bOk.setOnClickListener {
            parent.helper.start = start
            parent.helper.end = end
            parent.helper.options.clear()
            parent.helper.options.addAll(options)
            parent.helper.savePerformance(sMode.selectedItemPosition)
            dismiss()
        }
    }

    private fun switchDatePickers() = binding.run {
        if (sMode.selectedItemPosition == SearchEngine.MODE_DOCTRINE) {
            label.isVisible = false
            bStartRange.isVisible = false
            bEndRange.isVisible = false
            div.isVisible = false
        } else if (label.isVisible.not()) {
            label.isVisible = true
            bStartRange.isVisible = true
            bEndRange.isVisible = true
            div.isVisible = true
        }
    }

    private fun initOptions() {
        val list = mutableListOf<CheckItem>()
        var i = 0
        context.resources.getStringArray(R.array.search_options).forEach {
            list.add(CheckItem(title = it, isChecked = options[i]))
            i++
        }
        adapter = CheckAdapter(list, false, this::checkOption)
        if (parent.helper.isByWords.not())
            adapter.sizeCorrector = 3
        val rv = findViewById<RecyclerView>(R.id.rvOptions)
        rv.layoutManager = GridLayoutManager(context, ScreenUtils.span)
        rv.adapter = adapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun checkOption(index: Int, checked: Boolean): Int {
        if (index == SearchHelper.I_BY_WORDS) if (checked)
            adapter.sizeCorrector = 0
        else
            adapter.sizeCorrector = 3
        adapter.notifyDataSetChanged()
        options[index] = checked
        return index
    }

    override fun onRestoreInstanceState(state: Bundle) {
        start = DateUnit.putDays(state.getInt(Const.START))
        end = DateUnit.putDays(state.getInt(Const.END))
        val d = state.getInt(Const.DIALOG)
        if (d > 0) {
            if (state.getBoolean(Const.SELECT))
                showStartDatePicker(DateUnit.putDays(d))
            else
                showEndDatePicker(DateUnit.putDays(d))
        }
        binding.run {
            bStartRange.text = formatDate(start)
            bEndRange.text = formatDate(end)
            sMode.setSelection(state.getInt(Const.MODE))
        }
        state.getBooleanArray(Const.SEARCH)?.let {
            options.clear()
            options.addAll(it.toMutableList())
            initOptions()
        }
        if (options[SearchHelper.I_BY_WORDS].not())
            adapter.sizeCorrector = 3
    }

    override fun onSaveInstanceState(): Bundle {
        val state = Bundle()
        state.putInt(Const.DIALOG, dateDialog?.date?.timeInDays ?: 0)
        state.putBoolean(Const.SELECT, isStartDate)
        state.putInt(Const.START, start.timeInDays)
        state.putInt(Const.END, end.timeInDays)
        state.putInt(Const.MODE, binding.sMode.selectedItemPosition)
        state.putBooleanArray(Const.SEARCH, options.toBooleanArray())
        return state
    }

    private fun formatDate(d: DateUnit): String {
        return act.resources.getStringArray(R.array.months_short)[d.month - 1].toString() + " " + d.year
    }

    private fun showStartDatePicker(d: DateUnit) {
        isStartDate = true
        dateDialog = DateDialog(act, d).apply {
            setMinMonth(parent.helper.minMonth)
            setMinYear(parent.helper.minYear)
            setMaxMonth(end.month)
            setMaxYear(end.year)
            setResult(this@SearchDialog)
        }
        dateDialog?.show()
    }

    private fun showEndDatePicker(d: DateUnit) {
        isStartDate = false
        dateDialog = DateDialog(act, d).apply {
            setMinMonth(start.month)
            setMinYear(start.year)
            setResult(this@SearchDialog)
        }
        dateDialog?.show()
    }

    override fun putDate(date: DateUnit?) {
        if (date == null) // cancel
            return
        if (isStartDate) {
            start = date
            binding.bStartRange.text = formatDate(start)
        } else {
            end = date
            binding.bEndRange.text = formatDate(end)
        }
        dateDialog = null
    }
}
package ru.neosvet.vestnewage.view.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.DialogDateBinding
import ru.neosvet.vestnewage.helper.DateHelper.isLoadedOtkr
import ru.neosvet.vestnewage.view.list.DateAdapter

class DateDialog(
    private val act: Activity, date: DateUnit, private val result: Result
) : Dialog(act), View.OnClickListener {
    interface Result {
        fun putDate(date: DateUnit?) // null for cancel
    }

    companion object {
        private var modeMonths = true
    }

    private val binding: DialogDateBinding by lazy {
        DialogDateBinding.inflate(layoutInflater)
    }
    val date: DateUnit = DateUnit.putDays(date.timeInDays)
    private lateinit var adDate: DateAdapter
    var minYear: Int
    var minMonth: Int
    var maxYear = 0
    var maxMonth = 0

    init {
        if (isLoadedOtkr()) {
            minYear = 2004
            minMonth = 8
        } else {
            minYear = 2016
            minMonth = 1
        }
        val d = DateUnit.initToday()
        if (maxYear == 0) maxYear = d.year
        if (maxMonth == 0) maxMonth = d.month
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        initButtons()
        initList()
    }

    private fun initList() {
        val layoutManager = GridLayoutManager(act, 3)
        adDate = DateAdapter(minYear, this)
        binding.run {
            rvDate.layoutManager = layoutManager
            rvDate.adapter = adDate
        }
        if (modeMonths)
            showMonths()
        else {
            binding.btnYear.text = date.year.toString()
            showYears()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showYears() {
        adDate.clear()
        var select = 0
        for (i in minYear..maxYear) {
            adDate.addItem(i.toString())
            if (i == date.year)
                select = adDate.itemCount - 1
        }
        adDate.notifyDataSetChanged()
        adDate.selected = select
        binding.run {
            rvDate.smoothScrollToPosition(select)
            btnEnd.isEnabled = false
            btnStart.isEnabled = false
            btnMinus.isEnabled = false
            btnPlus.isEnabled = false
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showMonths() {
        adDate.clear()
        for (i in 0..11)
            adDate.addItem(act.resources.getStringArray(R.array.months)[i])
        setCalendar()
        adDate.notifyDataSetChanged()
        binding.run {
            btnEnd.isEnabled = true
            btnStart.isEnabled = true
            btnMinus.isEnabled = true
            btnPlus.isEnabled = true
        }
    }

    private fun initButtons() = binding.run {
        btnMinus.setOnClickListener {
            if (date.year > minYear) {
                date.changeYear(-1)
                date.month = 12
                setCalendar()
            }
        }
        btnPlus.setOnClickListener {
            if (date.year < maxYear) {
                date.changeYear(1)
                date.month = 1
                setCalendar()
            }
        }
        btnStart.setOnClickListener {
            date.year = minYear
            date.month = minMonth
            setCalendar()
        }
        btnEnd.setOnClickListener {
            date.year = maxYear
            date.month = maxMonth
            setCalendar()
        }
        btnOk.setOnClickListener {
            result.putDate(date)
            modeMonths = true
            dismiss()
        }
        btnYear.setOnClickListener {
            modeMonths = !modeMonths
            if (modeMonths)
                showMonths()
            else
                showYears()
        }
    }

    private fun setCalendar() {
        binding.btnYear.text = date.year.toString()
        adDate.minPos = if (date.year == minYear) minMonth - 1 else -1
        adDate.maxPos = if (date.year == maxYear) maxMonth - 1 else 12
        adDate.selected = date.month - 1
    }

    override fun cancel() {
        modeMonths = true
        result.putDate(null)
        super.cancel()
    }

    override fun onClick(v: View) { //click month item
        val pos = v.tag as Int
        if (modeMonths) {
            date.month = pos + 1
            adDate.selected = pos
        } else if (pos != adDate.selected) {
            val tv = v as TextView
            date.year = tv.text.toString().toInt()
            modeMonths = true
            showMonths()
        }
    }
}
package ru.neosvet.vestnewage.view.dialog

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.databinding.DialogDateBinding
import ru.neosvet.vestnewage.helper.DateHelper.isLoadedOtkr

class DateDialog(
    private val act: Activity, date: DateUnit, private val result: Result
) : Dialog(act), View.OnClickListener {
    interface Result {
        fun putDate(date: DateUnit?) // null for cancel
    }

    companion object {
        const val NONE_MIN = -1
        const val NONE_MAX = 12
    }

    private val binding: DialogDateBinding by lazy {
        DialogDateBinding.inflate(layoutInflater)
    }
    val date: DateUnit = DateUnit.putDays(date.timeInDays)
    private lateinit var adMonth: MonthAdapter
    var minYear: Int
    var minMonth: Int
    var maxYear = 0
    var maxMonth = 0
    private var cancel = true

    init {
        if (isLoadedOtkr()) {
            minYear = 2004
            minMonth = 8
        } else {
            minYear = 2016
            minMonth = 1
        }
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
        adMonth = MonthAdapter(this@DateDialog)
        for (i in 0..11)
            adMonth.addItem(act.resources.getStringArray(R.array.months)[i])
        binding.run {
            rvMonth.layoutManager = layoutManager
            rvMonth.adapter = adMonth
        }
        val d = DateUnit.initToday()
        if (maxYear == 0) maxYear = d.year
        if (maxMonth == 0) maxMonth = d.month
        setCalendar()
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
            cancel = false
            dismiss()
        }
    }

    private fun setCalendar() {
        binding.tvYear.text = date.year.toString()
        if (date.year == minYear) adMonth.setMinPos(minMonth - 1)
        else adMonth.setMinPos(NONE_MIN)
        if (date.year == maxYear) adMonth.setMaxPos(maxMonth - 1)
        else adMonth.setMaxPos(NONE_MAX)
        adMonth.setSelect(date.month - 1)
    }

    override fun dismiss() {
        if (cancel) result.putDate(null)
        super.dismiss()
    }

    override fun onClick(v: View) { //click month item
        val pos = v.tag as Int
        date.month = pos + 1
        adMonth.setSelect(pos)
    }

    internal inner class MonthAdapter(
        private val click: View.OnClickListener
    ) : RecyclerView.Adapter<MonthAdapter.ViewHolder>() {
        private val data = mutableListOf<String>()
        private var select = 0
        private var minPos = 0
        private var maxPos = 11
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(act).inflate(R.layout.item_date, parent, false)
            return ViewHolder(view)
        }

        fun setMinPos(min_pos: Int) {
            val i = this.minPos
            this.minPos = min_pos
            notifyItemChanged(i)
            notifyItemChanged(min_pos)
        }

        fun setMaxPos(max_pos: Int) {
            val i = this.maxPos
            this.maxPos = max_pos
            notifyItemChanged(i)
            notifyItemChanged(max_pos)
        }

        fun setSelect(pos: Int) {
            if (pos in minPos..maxPos) {
                val s = select
                select = pos
                notifyItemChanged(s)
                notifyItemChanged(select)
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
            holder.tv.text = data[pos]
            when (pos) {
                in (maxPos + 1) until minYear -> {
                    holder.bg.setBackgroundResource(R.drawable.cell_bg_none)
                    holder.bg.isEnabled = false
                }
                maxPos, minPos ->
                    if (pos == select) holder.bg.setBackgroundResource(R.drawable.cell_bg_all)
                    else holder.bg.setBackgroundResource(R.drawable.cell_bg_epi)
                select ->
                    holder.bg.setBackgroundResource(R.drawable.cell_bg_poe)
                else ->
                    holder.bg.setBackgroundResource(R.drawable.cell_bg_none)
            }
            holder.tv.tag = pos
            holder.tv.setOnClickListener(click)
        }

        override fun getItemCount() = data.size

        fun addItem(s: String) {
            data.add(s)
        }

        internal inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val bg: View = itemView.findViewById(R.id.cell_bg)
            val tv: TextView = itemView.findViewById(R.id.cell_tv)
        }
    }
}
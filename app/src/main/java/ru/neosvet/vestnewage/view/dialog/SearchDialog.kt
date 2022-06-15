package ru.neosvet.vestnewage.view.dialog

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.google.android.material.button.MaterialButton
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.utils.Const

class SearchDialog(
    private val act: Activity,
    private val parent: Parent
) : Dialog(act), DateDialog.Result {
    interface Parent {
        val modes: ArrayAdapter<String>
        val helper: SearchHelper
        fun clearHistory()
    }

    private lateinit var sMode: Spinner
    private lateinit var bStartRange: MaterialButton
    private lateinit var bEndRange: MaterialButton
    private var dateDialog: DateDialog? = null
    private var isStartDate = true
    private var start = DateUnit.putDays(parent.helper.start.timeInDays)
    private var end = DateUnit.putDays(parent.helper.end.timeInDays)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_search)

        sMode = findViewById(R.id.sMode)
        bStartRange = findViewById(R.id.bStartRange)
        bEndRange = findViewById(R.id.bEndRange)

        sMode.adapter = parent.modes
        sMode.setSelection(parent.helper.mode)
        bStartRange.text = formatDate(start)
        bEndRange.text = formatDate(end)

        bStartRange.setOnClickListener {
            isStartDate = true
            showDatePicker(start)
        }
        bEndRange.setOnClickListener {
            isStartDate = false
            showDatePicker(end)
        }
        findViewById<View>(R.id.bChangeRange).setOnClickListener {
            val d = start
            start = end
            end = d
            bStartRange.text = formatDate(start)
            bEndRange.text = formatDate(end)
        }
        findViewById<View>(R.id.bClearSearch).setOnClickListener {
            parent.clearHistory()
        }
        findViewById<View>(R.id.bOk).setOnClickListener {
            parent.helper.start = start
            parent.helper.end = end
            parent.helper.savePerformance(sMode.selectedItemPosition)
            dismiss()
        }
    }

    override fun onRestoreInstanceState(state: Bundle) {
        start = DateUnit.putDays(state.getInt(Const.START))
        end = DateUnit.putDays(state.getInt(Const.END))
        val d = state.getInt(Const.DIALOG)
        if (d > 0) {
            isStartDate = state.getBoolean(Const.SELECT)
            showDatePicker(DateUnit.putDays(d))
        }
        bStartRange.text = formatDate(start)
        bEndRange.text = formatDate(end)
        sMode.setSelection(state.getInt(Const.MODE))
    }

    override fun onSaveInstanceState(): Bundle {
        val state = Bundle()
        state.putInt(Const.DIALOG, dateDialog?.date?.timeInDays ?: 0)
        state.putBoolean(Const.SELECT, isStartDate)
        state.putInt(Const.START, start.timeInDays)
        state.putInt(Const.END, end.timeInDays)
        state.putInt(Const.MODE, sMode.selectedItemPosition)
        return state
    }

    private fun formatDate(d: DateUnit): String {
        return act.resources.getStringArray(R.array.months_short)[d.month - 1].toString() + " " + d.year
    }

    private fun showDatePicker(d: DateUnit) {
        dateDialog = DateDialog(act, d).apply {
            setMinMonth(parent.helper.minMonth)
            setMinYear(parent.helper.minYear)
            setResult(this@SearchDialog)
        }
        dateDialog?.show()
    }

    override fun putDate(date: DateUnit?) {
        if (date == null) // cancel
            return
        if (isStartDate) {
            start = date
            bStartRange.text = formatDate(start)
        } else {
            end = date
            bEndRange.text = formatDate(end)
        }
        dateDialog = null
    }
}
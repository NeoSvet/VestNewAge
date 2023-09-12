package ru.neosvet.vestnewage.view.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.SettingsItem

class SettingsAdapter(
    private val twoColumns: Boolean
) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {
    private val data = mutableListOf<SettingsItem>()
    val visible = mutableListOf(true, false, false, false, false, false, false)

    fun addItem(item: SettingsItem) {
        data.add(item)
    }

    fun setVisible(list: List<Boolean>) {
        if (twoColumns) {
            var b: Int
            for (a in list.indices step 2) {
                b = a + 1
                visible[a] = list[a]
                if (b == visible.size) break
                visible[b] = list[b]
                if (visible[a])
                    visible[b] = true
                else if (visible[b])
                    visible[a] = true
            }
        } else for (i in list.indices)
            visible[i] = list[i]
    }

    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val id = when (data[viewType]) {
            is SettingsItem.CheckList ->
                R.layout.item_set_checklist

            is SettingsItem.CheckListButton ->
                R.layout.item_set_checklistbutton

            is SettingsItem.Message ->
                R.layout.item_set_message

            is SettingsItem.Notification ->
                R.layout.item_set_notification
        }
        return ViewHolder(LayoutInflater.from(parent.context).inflate(id, null))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setItem(data[position])
    }

    override fun getItemCount(): Int = data.size

    fun switchPanel(index: Int) {
        visible[index] = visible[index].not()
        notifyItemChanged(index)
        if (twoColumns) {
            val i = if (index % 2 == 0)
                index + 1
            else
                index - 1
            visible[i] = visible[i].not()
            notifyItemChanged(i)
        }
    }

    inner class ViewHolder(
        private val root: View
    ) : RecyclerView.ViewHolder(root) {

        fun setItem(item: SettingsItem) {
            when (item) {
                is SettingsItem.CheckList ->
                    initCheckList(item)

                is SettingsItem.CheckListButton ->
                    initCheckListButton(item)

                is SettingsItem.Message ->
                    initMessage(item)

                is SettingsItem.Notification ->
                    initNotification(item)
            }
        }

        private fun initTitle(title: String): Boolean {
            val tvTitle = root.findViewById(R.id.tvTitle) as TextView
            val btnTitle = root.findViewById(R.id.btnTitle) as View
            val imgTitle = root.findViewById(R.id.imgTitle) as ImageView
            val panel = root.findViewById(R.id.panel) as View
            tvTitle.text = title
            val index = layoutPosition
            btnTitle.setOnClickListener { switchPanel(index) }
            if (visible[index]) {
                panel.isVisible = true
                imgTitle.setImageResource(R.drawable.minus)
            } else {
                panel.isVisible = false
                imgTitle.setImageResource(R.drawable.plus)
                return true
            }
            return false
        }

        private fun initCheckList(item: SettingsItem.CheckList) {
            if (initTitle(item.title)) return

            val adapter = CheckAdapter(item.list, false)
            { index, checked ->
                if (item.isSingleSelect && checked.not()) {
                    item.list[index].isChecked = true
                    return@CheckAdapter index
                }
                item.onChecked.invoke(index, checked)
                return@CheckAdapter index
            }
            val list = root.findViewById(R.id.list) as RecyclerView
            list.layoutManager = GridLayoutManager(root.context, 1)
            list.adapter = adapter
        }

        private fun initCheckListButton(item: SettingsItem.CheckListButton) {
            if (initTitle(item.title)) return

            val button = root.findViewById(R.id.button) as Button
            button.text = item.buttonLabel
            button.isEnabled = false
            var checkedCount = 0
            val adapter = CheckAdapter(item.list, false)
            { index, checked ->
                item.list[index].isChecked = checked
                if (checked) checkedCount++ else checkedCount--
                button.isEnabled = checkedCount > 0
                return@CheckAdapter index
            }
            val list = root.findViewById(R.id.list) as RecyclerView
            list.layoutManager = GridLayoutManager(root.context, 1)
            list.adapter = adapter

            button.setOnClickListener {
                val checked = mutableListOf<Int>()
                item.list.forEach { item ->
                    if (item.isChecked) {
                        item.isChecked = false
                        checked.add(item.id)
                    }
                }
                item.onClick.invoke(checked)
                notifyItemChanged(layoutPosition)
            }
        }

        private fun initMessage(item: SettingsItem.Message) {
            if (initTitle(item.title)) return

            val text = root.findViewById(R.id.text) as TextView
            val button = root.findViewById(R.id.button) as Button
            text.text = item.text
            if (item.buttonLabel.isEmpty()) {
                button.isVisible = false
                return
            }
            button.text = item.buttonLabel
            button.setOnClickListener { item.onClick.invoke() }
        }

        private fun initNotification(item: SettingsItem.Notification) {
            if (initTitle(item.title)) return

            val tvOn = root.findViewById(R.id.tvOn) as TextView
            val tvOff = root.findViewById(R.id.tvOff) as TextView
            val labelOn = root.findViewById(R.id.labelOn) as TextView
            val labelOff = root.findViewById(R.id.labelOff) as TextView
            val label = root.findViewById(R.id.label) as TextView
            val button = root.findViewById(R.id.button) as Button
            val seekBar = root.findViewById(R.id.seekBar) as SeekBar
            val checkBox = root.findViewById(R.id.checkBox) as CheckBox
            labelOff.text = item.offLabel
            labelOn.text = item.onLabel
            checkBox.text = item.checkBoxLabel
            checkBox.isChecked = item.checkBoxValue
            seekBar.max = item.maxSeek
            if (item.valueSeek == 0)
                seekBar.progress = 1
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (progress == seekBar.max) {
                        tvOn.isVisible = true
                        tvOff.isVisible = false
                        button.isEnabled = false
                        checkBox.isEnabled = false
                    } else {
                        button.isEnabled = true
                        tvOn.isVisible = false
                        tvOff.isVisible = true
                        checkBox.isEnabled = true
                    }
                    item.changeValue.invoke(label, progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    item.fixValue.invoke(seekBar.progress, checkBox.isChecked)
                }
            })
            seekBar.progress = item.valueSeek
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.fixValue.invoke(-seekBar.progress, isChecked)
            }
            button.setOnClickListener { item.onClick.invoke() }
        }
    }
}
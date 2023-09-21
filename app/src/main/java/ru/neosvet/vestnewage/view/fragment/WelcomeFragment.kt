package ru.neosvet.vestnewage.view.fragment

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.basic.convertDpi
import ru.neosvet.vestnewage.view.list.RecyclerAdapter

class WelcomeFragment : BottomSheetDialogFragment() {
    interface ItemClicker {
        fun onItemClick(link: String)
    }

    companion object {
        fun newInstance(hasNew: Boolean, timediff: Int): WelcomeFragment {
            val fragment = WelcomeFragment()
            val args = Bundle()
            args.putBoolean(Const.ADS, hasNew)
            args.putInt(Const.TIMEDIFF, timediff)
            fragment.arguments = args
            return fragment
        }
    }

    private var clicker: ItemClicker? = null
    val list = mutableListOf<BasicItem>()
    private val adapter = RecyclerAdapter(this::onItemClick)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.welcome_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog!!.setOnShowListener { dialog: DialogInterface ->
            val d = dialog as BottomSheetDialog
            val sheetId = com.google.android.material.R.id.design_bottom_sheet
            d.findViewById<View>(sheetId)?.let { bottomSheet ->
                val height = requireContext().convertDpi(175)
                BottomSheetBehavior.from(bottomSheet).setPeekHeight(height)
            }
        }
        val rvBottom = view.findViewById(R.id.rvBottom) as RecyclerView
        rvBottom.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvBottom.adapter = adapter
        arguments?.let { parseArguments(it) }
        adapter.setItems(list)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ItemClicker)
            clicker = context
    }

    private fun parseArguments(args: Bundle) {
        val timeDiff = args.getInt(Const.TIMEDIFF)
        val item = BasicItem(getString(R.string.sync_time))
        item.des = if (timeDiff == 0)
            getString(R.string.matches)
        else
            String.format(getString(R.string.time_deviation), timeDiff)
        list.add(0, item)
        if (args.getBoolean(Const.ADS))
            list.add(0, BasicItem(getString(R.string.new_dev_ads), Const.ADS))
    }

    private fun onItemClick(index: Int, item: BasicItem) {
        clicker?.onItemClick(item.link)
        if (item.link == Const.ADS) dismiss()
    }
}
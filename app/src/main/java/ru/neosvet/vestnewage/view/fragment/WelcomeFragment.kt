package ru.neosvet.vestnewage.view.fragment

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.list.ListAdapter

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
    var pagesList: List<ListItem>? = null
    private val adapter: ListAdapter by lazy {
        ListAdapter(requireContext())
    }

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
                val height = (175 * resources.displayMetrics.density).toInt()
                BottomSheetBehavior.from(bottomSheet).setPeekHeight(height)
            }
        }
        val lvBottom = view.findViewById(R.id.lvBottom) as ListView
        lvBottom.adapter = adapter
        lvBottom.onItemClickListener = OnItemClickListener { _, _, pos: Int, _ ->
            clicker?.onItemClick(adapter.getItem(pos).link)
        }
        arguments?.let {
            parseArguments(it)
        }
        pagesList?.forEach { item ->
            adapter.addItem(item)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ItemClicker)
            clicker = context
    }

    private fun parseArguments(args: Bundle) {
        if (args.getBoolean(Const.ADS))
            adapter.addItem(ListItem(getString(R.string.new_dev_ads), Const.ADS))

        val timeDiff = args.getInt(Const.TIMEDIFF)
        val item = ListItem(getString(R.string.sync_time))
        item.des = if (timeDiff == 0)
            getString(R.string.matches)
        else
            String.format(
                getString(R.string.time_deviation),
                timeDiff
            )
        adapter.addItem(item)
        adapter.notifyDataSetChanged()
    }
}
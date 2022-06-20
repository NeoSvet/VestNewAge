package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.databinding.SummaryFragmentBinding
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.SummaryToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class SummaryFragment : NeoFragment() {
    private var binding: SummaryFragmentBinding? = null
    private val adapter: RecyclerAdapter = RecyclerAdapter(this::onItemClick, this::onItemLongClick)
    private val toiler: SummaryToiler
        get() = neotoiler as SummaryToiler
    override val title: String
        get() = getString(R.string.summary)
    private var openedReader = false

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(SummaryToiler::class.java).apply { init(requireContext()) }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = SummaryFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(savedInstanceState: Bundle?) {
        setViews()
        toiler.openList(savedInstanceState == null)
    }

    override fun onResume() {
        super.onResume()
        if (openedReader) {
            openedReader = false
            act?.updateNew()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        rvSummary.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvSummary.adapter = adapter
        setListEvents(rvSummary)
    }

    override fun onChangedState(state: NeoState) {
        setStatus(false)
        if (state is NeoState.ListValue) {
            val scroll = adapter.itemCount > 0
            adapter.setItems(state.list)
            if (scroll)
                binding?.rvSummary?.smoothScrollToPosition(0)
            act?.updateNew()
        }
    }

    private fun onItemClick(index: Int, item: ListItem) {
        if (toiler.isRun) return
        openedReader = true
        openReader(item.link, null)
    }

    override fun onAction(title: String) {
        startLoad()
    }

    private fun onItemLongClick(index: Int, item: ListItem): Boolean {
        MarkerActivity.addByPar(
            requireContext(),
            item.link, "", item.des.substring(item.des.indexOf(Const.N))
        )
        return true
    }
}
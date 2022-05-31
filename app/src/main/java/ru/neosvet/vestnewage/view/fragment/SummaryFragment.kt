package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.databinding.SummaryFragmentBinding
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.SummaryToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.SuccessList

class SummaryFragment : NeoFragment() {
    private var binding: SummaryFragmentBinding? = null
    private val adapter: RecyclerAdapter = RecyclerAdapter(this::onItemClick)
    private val toiler: SummaryToiler
        get() = neotoiler as SummaryToiler
    override val title: String
        get() = getString(R.string.rss)
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
        restoreState(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (openedReader) {
            openedReader = false
            act?.updateNew()
        }
    }

    override fun setStatus(load: Boolean) {
        super.setStatus(load)
        binding?.fabRefresh?.isVisible = !load
    }

    private fun restoreState(state: Bundle?) {
        if (state != null) {
            toiler.openList(false)
            return
        }
        val f = Lib.getFile(Const.RSS)
        if (f.exists()) {
            act?.run {
                val time = f.lastModified() / DateUnit.SEC_IN_MILLS
                binding?.fabRefresh?.isVisible = !status.checkTime(time)
            }
            toiler.openList(true)
        } else
            startLoad()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        rvSummary.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvSummary.adapter = adapter
        act?.setButton(fabRefresh, true)
        fabRefresh.setOnClickListener { startLoad() }
        setButtonsHider(rvSummary)
    }

    override fun onChangedState(state: NeoState) {
        if (state is SuccessList) {
            setStatus(false)
            adapter.setItems(state.list)
            binding?.rvSummary?.smoothScrollToPosition(0)
            act?.updateNew()
        }
    }

    private fun onItemClick(index: Int, item: ListItem) {
        if (toiler.isRun) return
        openedReader = true
        openReader(item.link, null)
    }
}
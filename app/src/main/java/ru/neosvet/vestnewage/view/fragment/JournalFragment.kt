package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.databinding.JournalFragmentBinding
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MarkerActivity.Companion.addByPar
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter
import ru.neosvet.vestnewage.viewmodel.JournalToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class JournalFragment : NeoFragment() {
    private val toiler: JournalToiler
        get() = neotoiler as JournalToiler
    private var binding: JournalFragmentBinding? = null
    private val adapter: PagingAdapter by lazy {
        PagingAdapter(this::onItemClick, this::onItemLongClick, this::finishedList)
    }

    override val title: String
        get() = getString(R.string.journal)

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(JournalToiler::class.java).apply { init(requireContext()) }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = JournalFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(savedInstanceState: Bundle?) {
        toiler.preparing()
        setViews()
        if (toiler.offset > 0)
            binding?.rvJournal?.smoothScrollToPosition(toiler.offset)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        rvJournal.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvJournal.adapter = adapter
        setListEvents(rvJournal)
        lifecycleScope.launch {
            toiler.paging().collect {
                adapter.submitData(lifecycle, it)
            }
        }
    }

    private fun onItemClick(index: Int, item: ListItem) {
        var s = item.des
        if (s.contains(getString(R.string.rnd_verse))) {
            val i = s.indexOf(Const.N, s.indexOf(getString(R.string.rnd_verse))) + 1
            s = s.substring(i)
        } else s = null
        openReader(item.link, s)
    }

    private fun onItemLongClick(index: Int, item: ListItem): Boolean {
        var des = item.des
        var par = ""
        var i = des.indexOf(getString(R.string.rnd_verse))
        if (i > -1 && i < des.lastIndexOf(Const.N)) {
            par = des.substring(des.indexOf(Const.N, i) + 1)
            i = des.indexOf("«")
            des = des.substring(i, des.indexOf(Const.N, i) - 1)
        } else if (des.contains("«")) {
            des = des.substring(des.indexOf("«"))
        } else des = des.substring(des.indexOf("(") + 1, des.indexOf(")"))
        addByPar(
            requireContext(),
            item.link, par, des
        )
        return true
    }

    private fun finishedList() {
        val msg = if (toiler.isLoading)
            getString(R.string.load)
        else getString(R.string.finish_list)
        act?.showToast(msg)
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            NeoState.Success ->
                act?.hideToast()
            NeoState.Ready -> binding?.run {
                act?.showStaticToast(getString(R.string.empty_journal))
                act?.setAction(0)
            }
            else -> {}
        }
    }

    override fun onAction(title: String) {
        toiler.clear()
        adapter.submitData(lifecycle, PagingData.empty())
    }
}
package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MarkerActivity.Companion.addByPar
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter
import ru.neosvet.vestnewage.viewmodel.JournalToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.JournalState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class JournalFragment : NeoFragment(), PagingAdapter.Parent {
    private val toiler: JournalToiler
        get() = neotoiler as JournalToiler
    private val adapter: PagingAdapter by lazy {
        PagingAdapter(this)
    }

    override val title: String
        get() = getString(R.string.journal)
    private var jobList: Job? = null
    private var isUserScroll = true
    private var firstPosition = 0

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[JournalToiler::class.java]

    override fun onDestroyView() {
        jobList?.cancel()
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.list_fragment, container, false).also {
        initView(it)
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
    }

    override fun onSaveInstanceState(outState: Bundle) {
        toiler.setStatus(JournalState.Status(adapter.firstPosition))
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView(container: View) {
        val rv = container.findViewById(R.id.rvList) as RecyclerView
        rv.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rv.adapter = adapter
        setListEvents(rv)
    }

    private fun startPaging(page: Int) {
        jobList?.cancel()
        onChangePage(page)
        jobList = lifecycleScope.launch {
            toiler.paging(page, adapter).collect {
                adapter.submitData(lifecycle, it)
            }
        }
    }

    override fun onItemClick(index: Int, item: BasicItem) {
        var s = item.des
        if (s.contains(getString(R.string.rnd_verse))) {
            val i = s.indexOf(Const.N, s.indexOf(getString(R.string.rnd_verse))) + 1
            s = s.substring(i)
            openReader(item.link, s)
        } else
            openReader(item.link, null)
    }

    override fun onItemLongClick(index: Int, item: BasicItem): Boolean {
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

    override fun onChangePage(page: Int) {
        if (page > 0)
            act?.lockHead()
        isUserScroll = false
        act?.setScrollBar(page)
        isUserScroll = true
    }

    override fun onFinishList() {
        if (toiler.isLoading)
            act?.showToast(getString(R.string.load))
        else act?.let {
            it.showToast(getString(R.string.finish_list))
            it.unlockHead()
        }
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            BasicState.Ready ->
                act?.hideToast()

            is ListState.Paging ->
                openList(state.max)

            is JournalState.Status ->
                restoreStatus(state)

            BasicState.Empty -> act?.run {
                showStaticToast(getString(R.string.empty_journal))
                setAction(0)
            }
        }
    }

    private fun restoreStatus(state: JournalState.Status) {
        firstPosition = state.firstPosition
    }

    private fun openList(max: Int) {
        if (max > NeoPaging.ON_PAGE)
            act?.initScrollBar(max / NeoPaging.ON_PAGE, this::onScroll)
        if (firstPosition == 0) startPaging(0)
        else startPaging(firstPosition / NeoPaging.ON_PAGE)
    }

    private fun onScroll(value: Int) {
        if (isUserScroll)
            startPaging(value)
    }

    override fun onAction(title: String) {
        toiler.clear()
        adapter.submitData(lifecycle, PagingData.empty())
    }
}
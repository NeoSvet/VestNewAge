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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MarkerActivity.Companion.addByPar
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter
import ru.neosvet.vestnewage.viewmodel.JournalToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

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

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[JournalToiler::class.java].apply { init(requireContext()) }

    override fun onDestroyView() {
        jobList?.cancel()
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.list_fragment, container, false)
        initView(view)
        return view
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        toiler.preparing()
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
        jobList = lifecycleScope.launch {
            toiler.paging(page, adapter).collect {
                adapter.submitData(lifecycle, it)
            }
        }
    }

    override fun onItemClick(index: Int, item: ListItem) {
        var s = item.des
        if (s.contains(getString(R.string.rnd_verse))) {
            val i = s.indexOf(Const.N, s.indexOf(getString(R.string.rnd_verse))) + 1
            s = s.substring(i)
            openReader(item.link, s)
        } else
            openReader(item.link, null)
    }

    override fun onItemLongClick(index: Int, item: ListItem): Boolean {
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
        isUserScroll = false
        act?.setScrollBar(page)
        isUserScroll = true
    }

    override fun onFinishList() {
        act?.temporaryBlockHead()
        if (toiler.isLoading)
            act?.showToast(getString(R.string.load))
        else
            act?.showToast(getString(R.string.finish_list))
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            NeoState.Success ->
                act?.hideToast()
            is NeoState.ListState -> {
                if (state.index > NeoPaging.ON_PAGE)
                    act?.initScrollBar(state.index / NeoPaging.ON_PAGE, this::onScroll)
                startPaging(toiler.page)
            }
            NeoState.Ready -> act?.run {
                showStaticToast(getString(R.string.empty_journal))
                setAction(0)
            }
            else -> {}
        }
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
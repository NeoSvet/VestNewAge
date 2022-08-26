package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.NewToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class NewFragment : NeoFragment() {
    private val adapter: RecyclerAdapter = RecyclerAdapter(this::onItemClick)
    private val toiler: NewToiler
        get() = neotoiler as NewToiler
    override val title: String
        get() = getString(R.string.new_section)

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(NewToiler::class.java).apply { init(requireActivity()) }

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
    }

    override fun onResume() {
        super.onResume()
        toiler.openList()
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is NeoState.Ready ->
                emptyList()
            is NeoState.ListValue -> {
                if (state.list.isEmpty())
                    emptyList()
                else
                    adapter.setItems(state.list)
            }
            else -> {}
        }
        act?.updateNew()
    }

    private fun emptyList() {
        adapter.clear()
        act?.run {
            showStaticToast(getString(R.string.empty_list))
            setAction(0)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView(container: View) {
        val rv = container.findViewById(R.id.rvList) as RecyclerView
        rv.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rv.adapter = adapter
        setListEvents(rv)
    }

    private fun onItemClick(index: Int, item: ListItem) {
        if (item.title.contains(getString(R.string.ad))) {
            toiler.openAd(item, index)
        } else if (item.link != "") {
            toiler.needOpen = true
            openReader(item.link, null)
            act?.updateNew()
        }
    }

    override fun onAction(title: String) {
        toiler.clearList()
    }
}
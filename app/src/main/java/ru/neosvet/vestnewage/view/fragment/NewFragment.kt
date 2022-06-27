package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.databinding.NewFragmentBinding
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.NewToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class NewFragment : NeoFragment() {
    private var binding: NewFragmentBinding? = null
    private val adapter: RecyclerAdapter = RecyclerAdapter(this::onItemClick)
    private val toiler: NewToiler
        get() = neotoiler as NewToiler
    override val title: String
        get() = getString(R.string.new_section)
    private var openedReader = false

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(NewToiler::class.java).apply { init(requireActivity()) }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = NewFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(savedInstanceState: Bundle?) {
        setView()
        if (savedInstanceState == null)
            toiler.openList()
    }

    override fun onResume() {
        super.onResume()
        if (openedReader) {
            openedReader = false
            toiler.openList()
        }
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
        act?.showStaticToast(getString(R.string.empty_list))
        act?.setAction(0)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setView() = binding?.run {
        rvNew.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvNew.adapter = adapter
        setListEvents(rvNew)
    }

    private fun onItemClick(index: Int, item: ListItem) {
        if (item.title.contains(getString(R.string.ad))) {
            toiler.openAd(item, index)
        } else if (item.link != "") {
            toiler.needOpen = true
            openedReader = true
            openReader(item.link, null)
        }
    }

    override fun onAction(title: String) {
        toiler.clearList()
    }
}
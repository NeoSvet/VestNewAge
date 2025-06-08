package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.storage.DevStorage
import ru.neosvet.vestnewage.utils.AdsUtils
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.list.BasicAdapter
import ru.neosvet.vestnewage.viewmodel.NewToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.NeoState
import ru.neosvet.vestnewage.viewmodel.state.NewState

class NewFragment : NeoFragment() {
    private val adapter: BasicAdapter = BasicAdapter(this::onItemClick)
    private val toiler: NewToiler
        get() = neotoiler as NewToiler
    override val title: String
        get() = getString(R.string.new_section)
    private var itemAds: BasicItem? = null
    private var openedReader = false
    private lateinit var rvList: RecyclerView
    private val ads: AdsUtils by lazy {
        AdsUtils(DevStorage(), requireContext())
    }

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[NewToiler::class.java]

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
        toiler.setStatus(NewState.Status(itemAds))
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (openedReader) {
            openedReader = false
            toiler.openList()
        }
    }

    override fun onChangedInsets(insets: android.graphics.Insets) {
        rvList.updatePadding(bottom = App.CONTENT_BOTTOM_INDENT)
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is BasicState.Empty ->
                emptyList()

            is ListState.Primary ->
                adapter.setItems(state.list)

            is NewState.Status ->
                restoreStatus(state)
        }
        act?.updateNew()
    }

    private fun restoreStatus(state: NewState.Status) {
        itemAds = state.itemAds?.also {
            ads.showDialog(requireActivity(), it, this::closeAds)
        }
    }

    private fun emptyList() {
        adapter.clear()
        act?.run {
            showStaticToast(getString(R.string.list_empty))
            setAction(0)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView(container: View) {
        rvList = container.findViewById(R.id.rvList)
        rvList.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvList.adapter = adapter
        setListEvents(rvList)
    }

    private fun onItemClick(index: Int, item: BasicItem) {
        if (item.title.contains(getString(R.string.ad))) {
            itemAds = item
            ads.showDialog(requireActivity(), item, this::closeAds)
        } else if (item.link != "") {
            toiler.clearStates()
            openedReader = true
            openReader(item.link, null)
            act?.updateNew()
        }
    }

    override fun onAction(title: String) {
        toiler.clearList()
    }

    private fun closeAds() = itemAds?.let {
        toiler.markAsRead(it)
        itemAds = null
    }
}
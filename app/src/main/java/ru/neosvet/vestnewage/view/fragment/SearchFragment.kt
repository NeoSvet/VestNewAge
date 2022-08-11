package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import ru.neosvet.vestnewage.databinding.SearchFragmentBinding
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.SearchEngine
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.dialog.SearchDialog
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter
import ru.neosvet.vestnewage.view.list.paging.SearchFactory
import ru.neosvet.vestnewage.viewmodel.SearchToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class SearchFragment : NeoFragment(), SearchDialog.Parent {
    companion object {
        private const val SETTINGS = "s"
        private const val ADDITION = "a"
        private const val LAST_RESULTS = 0
        private const val CLEAR_RESULTS = 1

        @JvmStatic
        fun newInstance(s: String?, mode: Int): SearchFragment {
            val fragment = SearchFragment()
            fragment.arguments = Bundle().apply {
                putString(Const.STRING, s)
                putInt(Const.MODE, mode)
            }
            return fragment
        }
    }

    private val toiler: SearchToiler
        get() = neotoiler as SearchToiler
    private var binding: SearchFragmentBinding? = null
    private val adRequest: ArrayAdapter<String> by lazy {
        ArrayAdapter(requireContext(), R.layout.spinner_item, helper.getListRequests())
    }
    private val adDefault: RecyclerAdapter by lazy {
        RecyclerAdapter(this::defaultClick)
    }
    private val adResult: PagingAdapter by lazy {
        PagingAdapter(this::resultClick, this::resultLongClick, this::finishedList)
    }
    private val manager: GridLayoutManager by lazy {
        GridLayoutManager(requireContext(), 1)
    }
    private val listPosition: Int
        get() = manager.findFirstVisibleItemPosition() + SearchFactory.min
    override lateinit var modes: ArrayAdapter<String>
        private set

    private var settings: SearchDialog? = null
    private var jobResult: Job? = null
    private var isNotUser = false
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding!!.pSearch)
    }
    override val helper: SearchHelper
        get() = toiler.helper

    override val title: String
        get() = getString(R.string.search)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = SearchFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(SearchToiler::class.java).apply { init(requireContext()) }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        initSearchBox()
        setViews()
        initSearchList()
        restoreState(savedInstanceState)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSearchBox() = binding?.run {
        etSearch.threshold = 1
        etSearch.setAdapter(adRequest)
        etSearch.setOnKeyListener { _, keyCode: Int, keyEvent: KeyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == EditorInfo.IME_ACTION_SEARCH
            ) {
                enterSearch()
                return@setOnKeyListener true
            }
            false
        }
        bSearch.setOnClickListener { enterSearch() }
        etSearch.doAfterTextChanged {
            bClear.isVisible = it?.isNotEmpty() ?: false
        }
        bClear.setOnClickListener { etSearch.setText("") }
        bExpanded.setOnClickListener { etSearch.showDropDown() }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun setStatus(load: Boolean) {
        binding?.run {
            if (load) {
                act?.hideHead()
                act?.blocked()
                pStatus.isVisible = true
                etSearch.isEnabled = false
            } else {
                act?.unblocked()
                pStatus.isVisible = false
                etSearch.isEnabled = true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        settings?.let {
            outState.putBundle(SETTINGS, it.onSaveInstanceState())
        }
        binding?.run {
            outState.putBoolean(ADDITION, content.pAdditionSet.isVisible)
        }
        outState.putInt(Const.PAGE, listPosition)
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) = binding?.run {
        if (state == null) {
            arguments?.let { args ->
                helper.mode = args.getInt(Const.MODE)
                helper.request = args.getString(Const.STRING) ?: ""
            }
            if (helper.request.isNotEmpty()) {
                etSearch.setText(helper.request)
                etSearch.setSelection(helper.request.length)
                startSearch()
            }
            return@run
        }
        SearchFactory.reset(state.getInt(Const.PLACE))
        if (toiler.shownResult) {
            toiler.showLastResult()
            etSearch.setText(helper.request)
            etSearch.setSelection(helper.request.length)
            content.tvLabel.text = helper.label
            bPanelSwitch.isVisible = true
            if (state.getBoolean(ADDITION))
                showAdditionPanel()
        }
        state.getBundle(SETTINGS)?.let {
            openSettings()
            settings?.onRestoreInstanceState(it)
        }
        if (toiler.isRun)
            setStatus(true)
    }

    private fun initSearchList() = binding?.content?.run {
        rvSearch.layoutManager = manager
        if (toiler.shownResult) {
            rvSearch.adapter = adResult
        } else {
            if (toiler.existsResults()) {
                val list = listOf(
                    ListItem(getString(R.string.results_last_search), true),
                    ListItem(getString(R.string.clear_results_search), true)
                )
                adDefault.setItems(list)
            }
            rvSearch.adapter = adDefault
        }
        setListEvents(rvSearch)
        rvSearch.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(view, dx, dy)
                val p = listPosition
                if (p % Const.MAX_ON_PAGE == 0)
                    setResultScroll(p / Const.MAX_ON_PAGE)
            }
        })
        sbResults.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (isNotUser) return
                SearchFactory.reset(seekBar.progress * Const.MAX_ON_PAGE)
                startPaging()
            }
        })
    }

    private fun setResultScroll(value: Int) {
        isNotUser = true
        binding?.content?.run {
            sbResults.progress = value
        }
        isNotUser = false
    }

    private fun openSettings() {
        softKeyboard.hide()
        settings = SearchDialog(act!!, this).apply {
            setOnDismissListener { settings = null }
            show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        bStop.setOnClickListener { toiler.cancel() }
        modes = ArrayAdapter(
            requireContext(), R.layout.spinner_button,
            resources.getStringArray(R.array.search_mode)
        )
        modes.setDropDownViewResource(R.layout.spinner_item)
        bPanelSwitch.setOnClickListener {
            if (content.pAdditionSet.isVisible) {
                bPanelSwitch.setImageResource(R.drawable.ic_bottom)
                content.pAdditionSet.isVisible = false
            } else
                showAdditionPanel()
        }
    }

    private fun showAdditionPanel() {
        act?.hideHead()
        binding?.run {
            bPanelSwitch.setImageResource(R.drawable.ic_top)
            content.pAdditionSet.isVisible = true
        }
    }

    private fun enterSearch() = binding?.run {
        etSearch.dismissDropDown()
        if (etSearch.length() < 3)
            act?.showToast(getString(R.string.low_sym_for_search))
        else
            startSearch()
    }

    private fun startSearch() = binding?.run {
        act?.hideToast()
        softKeyboard.hide()
        setStatus(true)
        val mode = if (content.cbSearchInResults.isChecked) {
            tvStatus.text = getString(R.string.search)
            SearchEngine.MODE_RESULTS
        } else helper.mode
        val request = etSearch.text.toString()
        adResult.submitData(lifecycle, PagingData.empty())
        content.rvSearch.adapter = adResult
        if (!helper.isEnding)
            toiler.setEndings(requireContext())
        toiler.startSearch(request, mode)
        addRequest(request)
    }

    private fun addRequest(request: String) {
        for (i in 0 until adRequest.count) {
            if (adRequest.getItem(i) == request)
                return
        }
        adRequest.add(request)
        adRequest.notifyDataSetChanged()
        helper.saveRequest(request)
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is NeoState.Message ->
                binding?.tvStatus?.text = state.message
            is NeoState.ListValue -> { //finish load page
                setStatus(false)
                adResult.update(state.list[0])
            }
            NeoState.Success -> {
                if (toiler.isRun)
                    showResult()
                else
                    finishSearch()
            }
            NeoState.Ready ->
                act?.hideToast()
            is NeoState.LongValue -> { //SpecialEvent
                if (state.value == SearchEngine.EVENT_WORDS_NOT_FOUND)
                    act?.showToast(getString(R.string.words_not_found))
            }
            else -> {}
        }
    }

    private fun showResult() = binding?.run {
        bPanelSwitch.isVisible = true
        if (content.pAdditionSet.isVisible)
            bPanelSwitch.setImageResource(R.drawable.ic_top)
        else
            bPanelSwitch.setImageResource(R.drawable.ic_bottom)
        content.tvLabel.text = helper.label
        if (helper.countMaterials <= Const.MAX_ON_PAGE) {
            setResultScroll(1)
            content.sbResults.max = 1
            content.sbResults.isEnabled = false
        } else {
            setResultScroll(0)
            val count = helper.countMaterials / Const.MAX_ON_PAGE - 1
            content.sbResults.max = count
            content.sbResults.isEnabled = true
        }
        startPaging()
    }

    private fun startPaging() {
        jobResult?.cancel()
        jobResult = lifecycleScope.launch {
            toiler.paging().collect {
                adResult.submitData(lifecycle, it)
            }
        }
    }

    private fun finishSearch() {
        setStatus(false)
        if (helper.countMaterials == 0 && helper.isNeedLoad.not())
            noResults()
        else
            showResult()
    }

    private fun noResults() {
        adDefault.clear()
        binding?.run {
            bPanelSwitch.isVisible = false
            content.pAdditionSet.isVisible = false
            content.cbSearchInResults.isChecked = false
        }
        act?.showStaticToast(getString(R.string.search_no_results))
    }

    private fun defaultClick(index: Int, item: ListItem) {
        when (index) {
            LAST_RESULTS -> {
                helper.loadLastResult()
                toiler.showLastResult()
                binding?.run {
                    content.rvSearch.adapter = adResult
                    content.tvLabel.text = helper.label
                    bPanelSwitch.isVisible = true
                    etSearch.setText(helper.request)
                    etSearch.setSelection(helper.request.length)
                }
            }
            CLEAR_RESULTS -> {
                helper.deleteBase()
                adDefault.clear()
            }
        }
    }

    private fun resultClick(index: Int, item: ListItem) {
        when (helper.getType(item)) {
            SearchHelper.Type.NORMAL -> {
                val s = when {
                    helper.mode == SearchEngine.MODE_TITLES -> null
                    helper.mode == SearchEngine.MODE_LINKS -> null
                    helper.isByWords -> Lib.withOutTags(
                        item.des.substring(0, item.des.length - 4).replace("</p>", Const.NN)
                    )
                    else -> helper.request
                }
                BrowserActivity.openReader(item.link, s)
            }
            SearchHelper.Type.LOAD_MONTH ->
                toiler.loadMonth(item.link)
            SearchHelper.Type.LOAD_PAGE ->
                toiler.loadPage(item.link)
        }
    }

    private fun resultLongClick(index: Int, item: ListItem): Boolean {
        if (helper.getType(item) != SearchHelper.Type.NORMAL) return true
        var des = helper.label
        des = getString(R.string.search_for) +
                des.substring(des.indexOf("“") - 1, des.indexOf(Const.N) - 1)
        MarkerActivity.addByPar(
            requireContext(),
            item.link,
            item.des, des
        )
        return true
    }

    private fun finishedList() {
        if (toiler.isRun) return
        val msg = if (toiler.isLoading)
            getString(R.string.load)
        else getString(R.string.finish_list)
        act?.showToast(msg)
    }

    override fun onAction(title: String) {
        openSettings()
    }

    override fun clearHistory() {
        adRequest.clear()
        adRequest.notifyDataSetChanged()
        helper.clearRequests()
    }
}
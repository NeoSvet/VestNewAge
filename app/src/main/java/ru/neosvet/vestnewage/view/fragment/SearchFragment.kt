package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.SearchScreen
import ru.neosvet.vestnewage.databinding.SearchFragmentBinding
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.SearchEngine
import ru.neosvet.vestnewage.utils.TipUtils
import ru.neosvet.vestnewage.utils.fromHTML
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.NeoScrollBar
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.dialog.PromptDialog
import ru.neosvet.vestnewage.view.dialog.SearchDialog
import ru.neosvet.vestnewage.view.list.BasicAdapter
import ru.neosvet.vestnewage.view.list.RequestAdapter
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter
import ru.neosvet.vestnewage.viewmodel.SearchToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.NeoState
import ru.neosvet.vestnewage.viewmodel.state.SearchState

class SearchFragment : NeoFragment(), SearchDialog.Parent, PagingAdapter.Parent, NeoScrollBar.Host {
    companion object {
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
    private lateinit var helper: SearchHelper
    private lateinit var adRequest: RequestAdapter
    private val adDefault: BasicAdapter by lazy {
        BasicAdapter(this::defaultClick)
    }
    private val adPaging: PagingAdapter by lazy {
        PagingAdapter(this)
    }
    private lateinit var resultAdapter: ArrayAdapter<String>
    private var screen = SearchScreen.EMPTY

    private var settings: SearchDialog? = null
    private var jobList: Job? = null
    private var isUserScroll = true
    private var collectResult: Job? = null
    private var maxPages = 0
    private var firstPosition = 0
    private var isNeedPaging = true
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding!!.pSearch)
    }

    override val title: String
        get() = getString(R.string.search)

    private val exportResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.let {
            parseFileResult(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = SearchFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[SearchToiler::class.java]

    override fun onViewCreated(savedInstanceState: Bundle?) {
        initSearchBox()
        setViews()
        if (savedInstanceState == null) {
            arguments?.let { args ->
                val mode = args.getInt(Const.MODE, 0)
                val request = args.getString(Const.STRING) ?: ""
                toiler.setArguments(mode, request)
                binding?.run {
                    etSearch.setText(request)
                    etSearch.setSelection(request.length)
                }
            }
            TipUtils.showTipIfNeed(TipUtils.Type.SEARCH)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val i = if (screen == SearchScreen.RESULTS) {
            adPaging.firstPosition
        } else -1
        toiler.setStatus(
            SearchState.Status(
                screen = screen,
                settings = settings?.onSaveInstanceState(),
                shownAddition = binding?.content?.pAdditionSet?.isVisible == true,
                firstPosition = i
            )
        )
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSearchBox() = binding?.run {
        adRequest = RequestAdapter(
            requireContext(),
            this@SearchFragment::selectRequest
        )

        etSearch.threshold = 1
        etSearch.setAdapter(adRequest.adapter)
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
        etSearch.setOnTouchListener { _, motionEvent: MotionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN)
                hideRequests()
            false
        }
        etSearch.doAfterTextChanged {
            bClear.isVisible = it?.isNotEmpty() ?: false
        }
        rvRequests.layoutManager = GridLayoutManager(requireContext(), 1)
        rvRequests.adapter = adRequest

        bClear.setOnClickListener { etSearch.setText("") }
        bRequestsSwitcher.setOnClickListener {
            val v = rvRequests.isVisible
            rvRequests.isVisible = !v
            if (v)
                bRequestsSwitcher.setImageResource(R.drawable.ic_triangle_down)
            else
                bRequestsSwitcher.setImageResource(R.drawable.ic_triangle_up)
        }
    }

    private fun hideRequests() = binding?.run {
        rvRequests.isVisible = false
        bRequestsSwitcher.setImageResource(R.drawable.ic_triangle_down)
    }

    override fun onDestroyView() {
        act?.unlockHead()
        adRequest.save()
        binding = null
        super.onDestroyView()
    }

    override fun onBackPressed(): Boolean {
        binding?.run {
            if (rvRequests.isVisible) {
                hideRequests()
                return false
            }
        }
        return super.onBackPressed()
    }

    override fun setStatus(load: Boolean) {
        super.setStatus(false)
        binding?.run {
            if (load) {
                act?.let {
                    moveExportButton(true)
                    it.initScrollBar(0, null)
                    it.lockHead()
                    it.blocked()
                }
                pStatus.isVisible = true
                etSearch.isEnabled = false
            } else {
                act?.let {
                    it.unlockHead()
                    it.unblocked()
                }
                pStatus.isVisible = false
                etSearch.isEnabled = true
            }
        }
    }

    private fun moveExportButton(reset: Boolean) {
        binding?.content?.bExport?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            rightMargin = if (reset) resources.getDimension(R.dimen.def_indent).toInt()
            else resources.getDimension(R.dimen.double_indent).toInt()
        }
    }

    private fun initSearchList(screen: SearchScreen) = binding?.content?.run {
        rvSearch.layoutManager = GridLayoutManager(requireContext(), 1)
        this@SearchFragment.screen = screen
        when (screen) {
            SearchScreen.RESULTS ->
                rvSearch.adapter = adPaging

            SearchScreen.EMPTY ->
                rvSearch.adapter = adDefault

            SearchScreen.DEFAULT -> {
                val list = listOf(
                    BasicItem(getString(R.string.results_last_search), true),
                    BasicItem(getString(R.string.clear_results_search), true)
                )
                adDefault.setItems(list)
                rvSearch.adapter = adDefault
            }
        }
        setListEvents(rvSearch)
    }

    private fun openSettings() {
        softKeyboard.hide()
        settings = SearchDialog(
            act = requireActivity(),
            parent = this,
            mode = helper.mode,
            startInDays = helper.start.timeInDays,
            endInDays = helper.end.timeInDays,
            optionsIn = helper.options
        ).apply {
            setOnDismissListener { settings = null }
            show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        bStop.setOnClickListener { toiler.cancel() }
        resultAdapter = ArrayAdapter(
            requireContext(), R.layout.spinner_button,
            resources.getStringArray(R.array.search_mode_results)
        )
        resultAdapter.setDropDownViewResource(R.layout.spinner_item)
        content.sSearchInResults.adapter = resultAdapter
        bPanelSwitch.setOnClickListener {
            if (content.pAdditionSet.isVisible) {
                bPanelSwitch.setImageResource(R.drawable.ic_bottom)
                content.pAdditionSet.isVisible = false
            } else
                showAdditionPanel()
        }
        content.bExport.setOnClickListener {
            if (isBlocked) return@setOnClickListener
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.type = "text/html"
            val date = DateUnit.initToday()
            intent.putExtra(
                Intent.EXTRA_TITLE, getString(R.string.search_results) + " "
                        + date.toString().replace(".", "-") + ".html"
            )
            exportResult.launch(intent)
        }
        rvRequests.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = App.CONTENT_BOTTOM_INDENT
        }
    }

    private fun parseFileResult(data: Intent) {
        data.dataString?.let { file ->
            binding?.tvStatus?.text = getString(R.string.export)
            setStatus(true)
            toiler.startExport(file)
        }
    }

    private fun showAdditionPanel() {
        act?.lockHead()
        binding?.run {
            bPanelSwitch.setImageResource(R.drawable.ic_top)
            content.pAdditionSet.isVisible = true
        }
    }

    private fun enterSearch() = binding?.run {
        hideRequests()
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
        val i = content.sSearchInResults.selectedItemPosition
        val mode = if (i > 0) {
            tvStatus.text = getString(R.string.search)
            if (i == 1) SearchEngine.MODE_RESULT_TEXT
            else SearchEngine.MODE_RESULT_PAR
        } else helper.mode
        val request = etSearch.text.toString().trim()
        adPaging.submitData(lifecycle, PagingData.empty())
        screen = SearchScreen.RESULTS
        content.rvSearch.adapter = adPaging
        if (!helper.isEnding)
            toiler.setEndings(requireContext())
        firstPosition = 0
        toiler.startSearch(request, mode)
        adRequest.add(request)
    }

    private fun selectRequest(request: String) {
        binding?.etSearch?.let {
            it.setText(request)
            it.dismissDropDown()
        }
        hideRequests()
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is BasicState.Message -> binding?.run {
                if (pStatus.isVisible) tvStatus.text = state.message
                else act?.showScrollTip(state.message)
            }

            is ListState.Update<*> ->  //finish load page
                adPaging.update(state.item as BasicItem)

            is SearchState.Results -> {
                if (state.max == 0) noResults()
                else showResult(state.max)
                if (state.finish) setStatus(false)
            }

            is SearchState.Primary ->
                helper = state.helper

            BasicState.Success ->
                setStatus(false)

            is SearchState.Status ->
                restoreStatus(state)

            BasicState.Ready ->
                act?.hideToast()

            SearchState.Start ->
                startSearch()

            is SearchState.FinishExport ->
                doneExport(state.message)

            BasicState.Empty ->
                act?.showToast(getString(R.string.words_not_found))
        }
    }

    private fun restoreStatus(state: SearchState.Status) = binding?.run {
        initSearchList(state.screen)
        if (state.firstPosition > -1) {
            toiler.showLastResult()
            etSearch.setText(helper.request)
            etSearch.setSelection(etSearch.length())
            content.tvLabel.text = helper.label
            bPanelSwitch.isVisible = true
            if (state.shownAddition)
                showAdditionPanel()
            firstPosition = state.firstPosition //for paging
        }
        state.settings?.let {
            openSettings()
            settings?.onRestoreInstanceState(it)
        }
    }

    private fun showResult(max: Int) {
        binding?.run {
            if (toiler.isTelegram) {
                content.sSearchInResults.setSelection(0)
                content.sSearchInResults.isEnabled = false
            } else content.sSearchInResults.isEnabled = true
            bPanelSwitch.isVisible = true
            if (content.pAdditionSet.isVisible)
                bPanelSwitch.setImageResource(R.drawable.ic_top)
            else
                bPanelSwitch.setImageResource(R.drawable.ic_bottom)
            content.tvLabel.text = helper.label
        }
        if (max > NeoPaging.ON_PAGE)
            maxPages = max / NeoPaging.ON_PAGE + 1
        if (maxPages > 0) {
            moveExportButton(false)
            act?.initScrollBar(maxPages, this)
        }
        if (!isNeedPaging) return
        isNeedPaging = !toiler.isLoading
        if (firstPosition == 0) startPaging(0)
        else startPaging(firstPosition / NeoPaging.ON_PAGE)
    }

    override fun onScrolled(value: Int) {
        if (isUserScroll) {
            firstPosition = value * NeoPaging.ON_PAGE
            startPaging(value)
        }
    }

    override fun onPreviewScroll(value: Int) {
        if (isUserScroll)
            toiler.getTitleOn(value * NeoPaging.ON_PAGE)
    }

    private fun startPaging(page: Int) = binding?.content?.run {
        jobList?.cancel()
        rvSearch.adapter = null
        jobList = lifecycleScope.launch {
            toiler.paging(page, adPaging).collect {
                adPaging.submitData(lifecycle, it)
            }
        }
        rvSearch.adapter = adPaging
    }

    private fun noResults() {
        adDefault.clear()
        binding?.run {
            bPanelSwitch.isVisible = false
            content.pAdditionSet.isVisible = false
            content.sSearchInResults.setSelection(0)
        }
        act?.showStaticToast(getString(R.string.search_no_results))
    }

    private fun defaultClick(index: Int, item: BasicItem) {
        when (index) {
            LAST_RESULTS -> {
                toiler.showLastResult()
                binding?.run {
                    screen = SearchScreen.RESULTS
                    content.rvSearch.adapter = adPaging
                    content.tvLabel.text = helper.label
                    bPanelSwitch.isVisible = true
                    etSearch.setText(helper.request)
                    etSearch.setSelection(etSearch.length())
                }
            }

            CLEAR_RESULTS -> {
                screen = SearchScreen.EMPTY
                toiler.clearBase()
                adDefault.clear()
            }
        }
    }

    override fun onItemClick(index: Int, item: BasicItem) {
        when (RequestAdapter.getType(item)) {
            RequestAdapter.Type.NORMAL -> {
                if (item.link.isDigitsOnly()) {
                    Urls.openInApps(Urls.TelegramUrl + item.link)
                    return
                }
                val s = when {
                    helper.mode == SearchEngine.MODE_TITLES -> null
                    helper.mode == SearchEngine.MODE_LINKS -> null
                    helper.isByWords -> item.des.substring(0, item.des.length - 4)
                        .replace("</p>", Const.NN).fromHTML

                    else -> helper.request
                }
                BrowserActivity.openReader(item.link, s)
            }

            RequestAdapter.Type.LOAD_MONTH -> {
                isNeedPaging = true
                toiler.loadMonth(item.link)
            }

            RequestAdapter.Type.LOAD_PAGE ->
                toiler.loadPage(item.link)
        }
    }

    override fun onItemLongClick(index: Int, item: BasicItem): Boolean {
        if (RequestAdapter.getType(item) != RequestAdapter.Type.NORMAL) return true
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

    private fun doneExport(file: String) {
        setStatus(false)
        if (childFragmentManager.findFragmentByTag(Const.FILE) == null)
            PromptDialog.newInstance(getString(R.string.send_file))
                .show(childFragmentManager, Const.FILE)
        collectResult?.cancel()
        collectResult = lifecycleScope.launch {
            PromptDialog.result.collect {
                if (it == PromptDialog.Result.Yes) {
                    val sendIntent = Intent(Intent.ACTION_SEND)
                    sendIntent.type = "text/plain"
                    sendIntent.putExtra(Intent.EXTRA_STREAM, file.toUri())
                    startActivity(sendIntent)
                }
                toiler.clearStates()
            }
        }
    }

    override fun onAction(title: String) {
        openSettings()
    }

    override fun onChangePage(page: Int) {
        if (page > 0)
            act?.lockHead()
        isUserScroll = false
        act?.setScrollBar(page)
        isUserScroll = true
    }

    override fun onFinishList(endList: Boolean) {
        isNeedPaging = true
        if (toiler.isLoading)
            act?.showToast(getString(R.string.wait))
        else act?.let {
            it.showToast(getString(R.string.finish_list))
            if (endList) act?.setScrollBar(-1)
            else it.unlockHead()
        }
    }

    override fun putSearchDialogResult(
        mode: Int,
        start: DateUnit,
        end: DateUnit,
        list: List<Boolean>
    ) {
        helper.start = start
        helper.end = end
        helper.putOptions(list)
        helper.savePerformance(mode)
    }
}
package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.databinding.SearchFragmentBinding
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.SearchEngine
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.activity.TipActivity
import ru.neosvet.vestnewage.view.activity.TipName
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.dialog.PromptDialog
import ru.neosvet.vestnewage.view.dialog.PromptResult
import ru.neosvet.vestnewage.view.dialog.SearchDialog
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.view.list.RequestAdapter
import ru.neosvet.vestnewage.view.list.paging.NeoPaging
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter
import ru.neosvet.vestnewage.viewmodel.SearchToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

class SearchFragment : NeoFragment(), SearchDialog.Parent, PagingAdapter.Parent {
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
    private val adSearch: ArrayAdapter<String> by lazy {
        ArrayAdapter(requireContext(), R.layout.spinner_item, helper.getListRequests())
    }
    private lateinit var adRequest: RequestAdapter
    private val adDefault: RecyclerAdapter by lazy {
        RecyclerAdapter(this::defaultClick)
    }
    private val adResult: PagingAdapter by lazy {
        PagingAdapter(this)
    }
    override lateinit var modeAdapter: ArrayAdapter<String>
        private set
    private lateinit var resultAdapter: ArrayAdapter<String>

    private var settings: SearchDialog? = null
    private var jobList: Job? = null
    private var isUserScroll = true
    private var collectResult: Job? = null
    private var maxPages = 0
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding!!.pSearch)
    }
    override val helper: SearchHelper
        get() = toiler.helper

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
        etSearch.setAdapter(adSearch)
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

        adRequest = RequestAdapter(
            helper.requests,
            this@SearchFragment::selectRequest,
            this@SearchFragment::removeRequest,
            this@SearchFragment::clearRequests
        )
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
        helper.saveRequest()
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
        binding?.run {
            if (load) {
                act?.initScrollBar(0, null)
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
        if (toiler.shownResult) binding?.run {
            outState.putBoolean(ADDITION, content.pAdditionSet.isVisible)
        }
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) = binding?.run {
        if (state == null) {
            arguments?.let { args ->
                helper.mode = args.getInt(Const.MODE)
                helper.request = args.getString(Const.STRING) ?: ""
            }
            if (helper.request.isEmpty())
                TipActivity.showTipIfNeed(TipName.SEARCH)
            else {
                etSearch.setText(helper.request)
                etSearch.setSelection(helper.request.length)
                startSearch()
            }
            return@run
        }
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
        rvSearch.layoutManager = GridLayoutManager(requireContext(), 1)
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
        modeAdapter = ArrayAdapter(
            requireContext(), R.layout.spinner_button,
            resources.getStringArray(R.array.search_mode)
        )
        modeAdapter.setDropDownViewResource(R.layout.spinner_item)
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
            if (toiler.isRun) return@setOnClickListener
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.type = "text/html"
            val date = DateUnit.initToday()
            intent.putExtra(
                Intent.EXTRA_TITLE, getString(R.string.search_results) + " "
                        + date.toString().replace(".", "-") + ".html"
            )
            exportResult.launch(intent)
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
        act?.hideHead()
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
        adResult.submitData(lifecycle, PagingData.empty())
        content.rvSearch.adapter = adResult
        if (!helper.isEnding)
            toiler.setEndings(requireContext())
        toiler.startSearch(request, mode)
        addRequest(request)
    }

    private fun addRequest(r: String) = helper.run {
        val i = requests.indexOf(r)
        if (i == 0) return@run
        if (i > -1) {
            requests.removeAt(i)
            adRequest.notifyItemRemoved(i + 1)
        } else {
            adSearch.add(r)
            adSearch.notifyDataSetChanged()
        }
        requests.add(0, r)
        adRequest.notifyItemInserted(1)
        if (requests.size == SearchHelper.REQUESTS_LIMIT) {
            requests.removeAt(SearchHelper.REQUESTS_LIMIT - 1)
            adRequest.notifyItemRemoved(SearchHelper.REQUESTS_LIMIT)
        }
    }

    private fun removeRequest(index: Int) {
        adSearch.remove(helper.requests[index])
        helper.requests.removeAt(index)
        adRequest.notifyItemRemoved(index + 1)
    }

    private fun selectRequest(index: Int) {
        binding?.etSearch?.let {
            it.setText(helper.requests[index])
            it.dismissDropDown()
        }
        hideRequests()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearRequests() {
        adSearch.clear()
        adSearch.notifyDataSetChanged()
        helper.clearRequests()
        adRequest.notifyDataSetChanged()
    }

    override fun onChangedOtherState(state: NeoState) {
        when (state) {
            is NeoState.Message -> {
                if (toiler.isExport) //finish export
                    doneExport(state.message)
                else
                    binding?.tvStatus?.text = state.message
            }
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
        if (helper.countMaterials > NeoPaging.ON_PAGE) {
            maxPages = helper.countMaterials / NeoPaging.ON_PAGE - 1
            onChangePage(0)
        }
        startPaging(toiler.page)
    }

    private fun onScroll(value: Int) {
        if (isUserScroll)
            startPaging(value)
    }

    private fun startPaging(page: Int) {
        jobList?.cancel()
        jobList = lifecycleScope.launch {
            toiler.paging(page, adResult).collect {
                adResult.submitData(lifecycle, it)
            }
        }
    }

    private fun finishSearch() {
        setStatus(false)
        if (helper.countMaterials == 0 && helper.isNeedLoad.not())
            noResults()
        else {
            showResult()
            if (maxPages > 0)
                act?.initScrollBar(maxPages, this::onScroll)
        }
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
                toiler.clearBase()
                adDefault.clear()
            }
        }
    }

    override fun onItemClick(index: Int, item: ListItem) {
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

    override fun onItemLongClick(index: Int, item: ListItem): Boolean {
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

    private fun doneExport(file: String) {
        setStatus(false)
        if (childFragmentManager.findFragmentByTag(Const.FILE) == null)
            PromptDialog.newInstance(getString(R.string.send_file))
                .show(childFragmentManager, Const.FILE)
        collectResult?.cancel()
        collectResult = lifecycleScope.launch {
            PromptDialog.result.collect {
                if (it == PromptResult.Yes) {
                    val sendIntent = Intent(Intent.ACTION_SEND)
                    sendIntent.type = "text/plain"
                    sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(file))
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
        isUserScroll = false
        act?.setScrollBar(page)
        isUserScroll = true
    }

    override fun onFinishList() {
        act?.temporaryBlockHead()
        if (toiler.isLoading)
            act?.showToast(getString(R.string.search_continue))
        else
            act?.showToast(getString(R.string.finish_list))
    }
}
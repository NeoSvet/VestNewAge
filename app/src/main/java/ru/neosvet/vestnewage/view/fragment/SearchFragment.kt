package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.databinding.SearchFragmentBinding
import ru.neosvet.vestnewage.helper.SearchHelper
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.activity.MarkerActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.basic.Tip
import ru.neosvet.vestnewage.view.dialog.SearchDialog
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter
import ru.neosvet.vestnewage.view.list.paging.SearchFactory
import ru.neosvet.vestnewage.viewmodel.SearchToiler
import ru.neosvet.vestnewage.viewmodel.basic.*

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
    override lateinit var modes: ArrayAdapter<String>
        private set

    private lateinit var tip: Tip
    private var settings: SearchDialog? = null
    private var jobResult: Job? = null
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
        binding?.run {
            tip = Tip(act, tvFinish)
        }
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
        etSearch.setOnTouchListener { _, motionEvent: MotionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN)
                etSearch.showDropDown()
            false
        }
        bSearch.setOnClickListener { enterSearch() }
        etSearch.doAfterTextChanged {
            bClear.isVisible = it?.isNotEmpty() ?: false
        }
        bClear.setOnClickListener { etSearch.setText("") }
    }

    override fun onDestroyView() {
        helper.saveLastResult()
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
            outState.putBoolean(ADDITION, content.pAdditionSet.visibility == View.VISIBLE)
        }
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
        if (toiler.shownResult) {
            toiler.showLastResult()
            etSearch.setText(helper.request)
            etSearch.setSelection(helper.request.length)
            content.tvLabel.text = helper.label
            if (state.getBoolean(ADDITION))
                content.pAdditionSet.isVisible = true
            else
                bShow.isVisible = true
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
            if (SearchFactory.offset > 0)
                rvSearch.smoothScrollToPosition(SearchFactory.offset)
            return@run
        }
        if (helper.existsResults()) {
            val list = listOf(
                ListItem(getString(R.string.results_last_search), true),
                ListItem(getString(R.string.clear_results_search), true)
            )
            adDefault.setItems(list)
        }
        rvSearch.adapter = adDefault
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
        modes = ArrayAdapter(
            requireContext(), R.layout.spinner_button,
            resources.getStringArray(R.array.search_mode)
        )
        modes.setDropDownViewResource(R.layout.spinner_item)
        bShow.setOnClickListener {
            bShow.isVisible = false
            content.pAdditionSet.isVisible = true
        }
        content.bHide.setOnClickListener {
            bShow.isVisible = true
            content.pAdditionSet.isVisible = false
        }
    }

    private fun enterSearch() = binding?.run {
        etSearch.dismissDropDown()
        if (etSearch.length() < 3)
            Lib.showToast(getString(R.string.low_sym_for_search))
        else
            startSearch()
    }

    private fun startSearch() = binding?.run {
        softKeyboard.hide()
        setStatus(true)
        val mode = if (content.cbSearchInResults.isChecked) {
            tvStatus.text = getString(R.string.search)
            SearchToiler.MODE_RESULTS
        } else helper.mode
        val request = etSearch.text.toString()
        adResult.submitData(lifecycle, PagingData.empty())
        content.rvSearch.adapter = adResult
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

    override fun onChangedState(state: NeoState) {
        when (state) {
            is MessageState ->
                binding?.tvStatus?.text = state.message
            is SuccessList -> { //finish load page
                setStatus(false)
                adResult.update(state.list[0])
            }
            Success -> {
                if (toiler.isRun)
                    showResult()
                else
                    finishSearch()
            }
            Ready ->
                tip.hideAnimated()
            else -> {}
        }
    }

    private fun showResult() = binding?.run {
        bShow.isVisible = content.pAdditionSet.isVisible.not()
        content.tvLabel.text = helper.label

        jobResult?.cancel()
        jobResult = lifecycleScope.launch {
            toiler.paging().collect {
                adResult.submitData(lifecycle, it)
            }
        }
    }

    private fun finishSearch() {
        setStatus(false)
        if (helper.countMaterials == 0)
            noResults()
        else
            showResult()
    }

    private fun noResults() {
        adDefault.clear()
        binding?.run {
            bShow.isVisible = false
            content.pAdditionSet.isVisible = false
            content.cbSearchInResults.isChecked = false
        }
        AlertDialog.Builder(requireContext(), R.style.NeoDialog).apply {
            setMessage(getString(R.string.alert_search))
            setPositiveButton(getString(android.R.string.ok))
            { dialog: DialogInterface, _ -> dialog.dismiss() }
        }.create().show()
    }

    private fun defaultClick(index: Int, item: ListItem) {
        when (index) {
            LAST_RESULTS -> {
                helper.loadLastResult()
                toiler.showLastResult()
                binding?.run {
                    content.rvSearch.adapter = adResult
                    content.tvLabel.text = helper.label
                    bShow.isVisible = true
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
                Lib.showToast(getString(R.string.long_press_for_mark))
                BrowserActivity.openReader(
                    item.link,
                    helper.request
                )
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
        binding?.tvFinish?.text = if (toiler.isLoading)
            getString(R.string.load)
        else getString(R.string.finish_list)
        tip.show()
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
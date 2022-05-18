package ru.neosvet.vestnewage.fragment

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.ResizeAnim
import ru.neosvet.ui.SoftKeyboard
import ru.neosvet.ui.Tip
import ru.neosvet.ui.dialogs.DateDialog
import ru.neosvet.utils.Const
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity
import ru.neosvet.vestnewage.activity.MarkerActivity
import ru.neosvet.vestnewage.databinding.SearchFragmentBinding
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.SearchHelper
import ru.neosvet.vestnewage.list.RecyclerAdapter
import ru.neosvet.vestnewage.list.item.ListItem
import ru.neosvet.vestnewage.list.paging.PagingAdapter
import ru.neosvet.vestnewage.list.paging.SearchFactory
import ru.neosvet.vestnewage.model.SearchModel
import ru.neosvet.vestnewage.model.basic.*

class SearchFragment : NeoFragment(), DateDialog.Result {
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

    private val model: SearchModel
        get() = neomodel as SearchModel
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

    private lateinit var tip: Tip
    private var anim: ResizeAnim? = null
    private var dialog = -1
    private var dateDialog: DateDialog? = null
    private var jobResult: Job? = null
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding!!.etSearch)
    }
    private val helper: SearchHelper
        get() = model.helper
    override val title: String
        get() = getString(R.string.search)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = SearchFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun initViewModel(): NeoViewModel =
        ViewModelProvider(this).get(SearchModel::class.java).apply { init(requireContext()) }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        binding?.run {
            tip = Tip(act, tvFinish)
        }
        initSearchBox()
        initSettings()
        initSearchList()
        restoreState(savedInstanceState)
    }

    override fun onBackPressed(): Boolean {
        if (binding?.settings?.root?.isVisible == true) {
            closeSettings()
            return false
        }
        return super.onBackPressed()
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
    }

    override fun onDestroyView() {
        helper.saveLastResult()
        binding = null
        super.onDestroyView()
    }

    override fun setStatus(load: Boolean) {
        binding?.run {
            if (load) {
                pStatus.isVisible = true
                fabSettings.isVisible = false
                etSearch.isEnabled = false
            } else {
                pStatus.isVisible = false
                fabSettings.isVisible = true
                etSearch.isEnabled = true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(Const.DIALOG, dialog)
        dateDialog?.dismiss()
        binding?.run {
            outState.putBoolean(ADDITION, content.pAdditionSet.visibility == View.VISIBLE)
            outState.putBoolean(SETTINGS, settings.root.visibility == View.VISIBLE)
        }
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) = binding?.run {
        with(settings) {
            root.isVisible = false
            sMode.setSelection(helper.loadMode())
            bStartRange.text = formatDate(helper.start)
            bEndRange.text = formatDate(helper.end)
        }
        if (state == null) {
            arguments?.let { args ->
                settings.sMode.setSelection(args.getInt(Const.MODE))
                helper.request = args.getString(Const.STRING) ?: ""
            }
            if (helper.request.isNotEmpty()) {
                etSearch.setText(helper.request)
                softKeyboard.hide()
                etSearch.setSelection(helper.request.length)
                startSearch()
            }
        } else {
            if (model.shownResult) {
                fabSettings.isVisible = false
                model.showLastResult()
                etSearch.setText(helper.request)
                softKeyboard.hide()
                etSearch.setSelection(helper.request.length)
                content.tvLabel.text = helper.label
                if (state.getBoolean(ADDITION))
                    content.pAdditionSet.isVisible = true
                else
                    bShow.isVisible = true
            }
            if (state.getBoolean(SETTINGS)) {
                openSettings()
                dialog = state.getInt(Const.DIALOG)
                if (dialog > -1) showDatePicker(dialog)
            }
            if (model.isRun)
                setStatus(true)
        }
    }

    private fun initSearchList() = binding?.content?.run {
        rvSearch.layoutManager = GridLayoutManager(requireContext(), 1)
        if (model.shownResult) {
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
    }

    private fun openSettings() = binding?.run {
        pSearch.isVisible = false
        fabSettings.isVisible = false
        fabOk.isVisible = true
        content.root.isVisible = false
        settings.root.isVisible = true
        softKeyboard.hide()
        initResizeAnim()
    }

    private fun closeSettings() = binding?.run {
        pSearch.isVisible = true
        fabSettings.isVisible = true
        fabOk.isVisible = false
        content.root.isVisible = true
        settings.root.isVisible = false
    }

    private fun initResizeAnim() = binding?.settings?.run {
        if (anim == null) {
            anim = ResizeAnim(
                root,
                false,
                (270 * resources.displayMetrics.density).toInt()
            ).apply {
                setStart(10)
                duration = 800
            }
            anim?.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    root.requestLayout()
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
        }
        root.clearAnimation()
        root.startAnimation(anim)
    }

    private fun formatDate(d: DateHelper): String {
        return resources.getStringArray(R.array.months_short)[d.month - 1].toString() + " " + d.year
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSettings() = binding?.run {
        fabSettings.setOnClickListener { openSettings() }
        bStop.setOnClickListener { model.cancel() }
        fabOk.setOnClickListener {
            closeSettings()
            helper.savePerformance(settings.sMode.selectedItemPosition)
        }
        val adMode = ArrayAdapter(
            requireContext(), R.layout.spinner_button,
            resources.getStringArray(R.array.search_mode)
        )
        adMode.setDropDownViewResource(R.layout.spinner_item)
        with(settings) {
            sMode.adapter = adMode
            bClearSearch.setOnClickListener {
                adRequest.clear()
                adRequest.notifyDataSetChanged()
                helper.clearRequests()
            }
            bStartRange.setOnClickListener { showDatePicker(0) }
            bEndRange.setOnClickListener { showDatePicker(1) }
            bChangeRange.setOnClickListener {
                helper.changeDates()
                bStartRange.text = formatDate(helper.start)
                bEndRange.text = formatDate(helper.end)
            }
        }
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

    private fun showDatePicker(id: Int) {
        val d = if (id == 0) helper.start else helper.end
        dialog = id
        dateDialog = DateDialog(act, d).apply {
            setMinMonth(helper.minMonth)
            setMinYear(helper.minYear)
            setResult(this@SearchFragment)
        }
        dateDialog?.show()
    }

    override fun putDate(date: DateHelper?) {
        if (date == null) { // cancel
            dialog = -1
            return
        }
        if (dialog == 0) {
            helper.start = date
            binding?.settings?.bStartRange?.text = formatDate(helper.start)
        } else {
            helper.end = date
            binding?.settings?.bEndRange?.text = formatDate(helper.end)
        }
        dialog = -1
    }

    private fun startSearch() = binding?.run {
        setStatus(true)
        val mode = if (content.cbSearchInResults.isChecked) {
            tvStatus.text = getString(R.string.search)
            SearchModel.MODE_RESULTS
        } else
            settings.sMode.selectedItemPosition
        val request = etSearch.text.toString()
        adResult.submitData(lifecycle, PagingData.empty())
        content.rvSearch.adapter = adResult
        model.startSearch(request, mode)
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
            Success -> {
                if (model.isRun)
                    showResult()
                else
                    finishSearch()
            }
            Ready ->
                tip.hideAnimated()
        }
    }

    private fun showResult() = binding?.run {
        bShow.isVisible = content.pAdditionSet.isVisible.not()
        content.tvLabel.text = helper.label

        jobResult?.cancel()
        jobResult = lifecycleScope.launch {
            model.paging().collect {
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
                binding?.fabSettings?.isVisible = false
                helper.loadLastResult()
                model.showLastResult()
                binding?.run {
                    content.rvSearch.adapter = adResult
                    content.tvLabel.text = helper.label
                    bShow.isVisible = true
                    etSearch.setText(helper.request)
                }
            }
            CLEAR_RESULTS -> {
                helper.deleteBase()
                adDefault.clear()
            }
        }
    }

    private fun resultClick(index: Int, item: ListItem) {
        Lib.showToast(getString(R.string.long_press_for_mark))
        BrowserActivity.openReader(
            item.link,
            helper.request
        )
    }

    private fun resultLongClick(index: Int, item: ListItem): Boolean {
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
        if (model.isRun) return
        binding?.tvFinish?.text = if (model.loading)
            getString(R.string.load)
        else getString(R.string.finish_list)
        tip.show()
    }
}
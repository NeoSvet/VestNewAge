package ru.neosvet.vestnewage.fragment

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import android.view.View.OnTouchListener
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.ResizeAnim
import ru.neosvet.ui.SoftKeyboard
import ru.neosvet.ui.dialogs.DateDialog
import ru.neosvet.utils.Const
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity
import ru.neosvet.vestnewage.activity.MarkerActivity
import ru.neosvet.vestnewage.databinding.SearchFragmentBinding
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.SearchHelper
import ru.neosvet.vestnewage.list.ListAdapter
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.list.PageAdapter
import ru.neosvet.vestnewage.model.SearchModel
import ru.neosvet.vestnewage.model.basic.MessageState
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.SuccessList

class SearchFragment : NeoFragment(), DateDialog.Result, OnTouchListener {
    companion object {
        private const val SETTINGS = "s"
        private const val ADDITION = "a"
        private const val LAST_RESULTS = "r"
        private const val CLEAR_RESULTS = "c"

        @JvmStatic
        fun newInstance(s: String?, mode: Int, page: Int): SearchFragment {
            val search = SearchFragment()
            val args = Bundle()
            args.putString(Const.STRING, s)
            args.putInt(Const.MODE, mode)
            args.putInt(Const.PAGE, page - 1)
            search.arguments = args
            return search
        }
    }

    private val model: SearchModel
        get() = neomodel as SearchModel
    private var binding: SearchFragmentBinding? = null
    private var adPages: PageAdapter? = null
    private val adSearch: ArrayAdapter<String> by lazy {
        ArrayAdapter(requireContext(), R.layout.spinner_item, helper.getListRequests())
    }
    private val adResults: ListAdapter by lazy {
        ListAdapter(requireContext())
    }
    private var anim: ResizeAnim? = null
    private var dialog = -1
    private var dateDialog: DateDialog? = null
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding!!.content.etSearch)
    }
    private var scrollToFirst = false
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
        initViews()
        initSearchBox()
        initSearchList()
        initSettings()
        restoreState(savedInstanceState)
    }

    override fun onBackPressed(): Boolean {
        if (binding?.pSettings?.isVisible == true) {
            closeSettings()
            return false
        }
        return super.onBackPressed()
    }

    private fun initSearchList() = binding?.content?.run {
        lvResult.onItemClickListener = OnItemClickListener { _, _, pos: Int, _ ->
            if (model.isRun) return@OnItemClickListener
            when (adResults.getItem(pos).link) {
                LAST_RESULTS -> {
                    binding?.fabSettings?.isVisible = false
                    bShow.isVisible = true
                    model.showResult(0)
                    helper.loadLastResult()
                    tvLabel.text = helper.label
                    etSearch.setText(helper.request)
                }
                CLEAR_RESULTS -> {
                    helper.deleteBase()
                    adResults.clear()
                    adResults.notifyDataSetChanged()
                }
                else -> {
                    Lib.showToast(getString(R.string.long_press_for_mark))
                    BrowserActivity.openReader(
                        adResults.getItem(pos).link,
                        helper.request
                    )
                }
            }
        }
        lvResult.onItemLongClickListener = OnItemLongClickListener { _, _, pos: Int, _ ->
            var des = tvLabel.text.toString()
            des = getString(R.string.search_for) +
                    des.substring(des.indexOf("“") - 1, des.indexOf(Const.N) - 1)
            MarkerActivity.addMarker(
                requireContext(),
                adResults.getItem(pos).link,
                adResults.getItem(pos).des, des
            )
            true
        }
        lvResult.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(absListView: AbsListView, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE && scrollToFirst) {
                    if (lvResult.firstVisiblePosition > 0)
                        lvResult.smoothScrollToPosition(0)
                    else scrollToFirst = false
                }
            }

            override fun onScroll(
                absListView: AbsListView, firstVisibleItem: Int,
                visibleItemCount: Int, totalItemCount: Int
            ) {
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSearchBox() = binding?.content?.run {
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
                content.etSearch.isEnabled = false
            } else {
                pStatus.isVisible = false
                fabSettings.isVisible = pPages.isVisible.not()
                content.etSearch.isEnabled = true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(Const.DIALOG, dialog)
        dateDialog?.dismiss()
        binding?.run {
            outState.putBoolean(ADDITION, content.pAdditionSet.visibility == View.VISIBLE)
            outState.putBoolean(SETTINGS, pSettings.visibility == View.VISIBLE)
        }
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) = binding?.run {
        sMode.setSelection(helper.loadMode())
        bStartRange.text = formatDate(helper.start)
        bEndRange.text = formatDate(helper.end)
        if (state == null) {
            val args = arguments
            if (args != null) {
                sMode.setSelection(args.getInt(Const.MODE))
                helper.page = args.getInt(Const.PAGE)
                helper.request = args.getString(Const.STRING) ?: ""
            }
            if (helper.request.isNotEmpty()) {
                content.etSearch.setText(helper.request)
                startSearch()
            }
        } else {
            if (helper.page > -1) {
                fabSettings.isVisible = false
                model.showResult(helper.page)
                content.etSearch.setText(helper.request)
                content.tvLabel.text = helper.label
                if (state.getBoolean(ADDITION))
                    content.pAdditionSet.isVisible = true
                else
                    content.bShow.isVisible = true
            }
            if (state.getBoolean(SETTINGS)) {
                openSettings()
                dialog = state.getInt(Const.DIALOG)
                if (dialog > -1) showDatePicker(dialog)
            }
            if (model.isRun)
                setStatus(true)
        }
        if (adResults.count == 0 && !model.isRun && helper.existsResults()) {
            addActionsForLastResults()
        }
    }

    private fun addActionsForLastResults() {
        adResults.addItem(
            ListItem(getString(R.string.results_last_search), LAST_RESULTS)
        )
        adResults.addItem(
            ListItem(getString(R.string.clear_results_search), CLEAR_RESULTS)
        )
        adResults.notifyDataSetChanged()
    }

    private fun openSettings() = binding?.run {
        fabSettings.isVisible = false
        fabOk.isVisible = true
        content.root.isVisible = false
        pSettings.isVisible = true
        softKeyboard.hide()
        pPages.isVisible = false
        initResizeAnim()
    }

    private fun closeSettings() = binding?.run {
        if (helper.page == -1)
            fabSettings.isVisible = true else pPages.isVisible = true
        fabOk.isVisible = false
        content.root.isVisible = true
        pSettings.isVisible = false
    }

    private fun initResizeAnim() = binding?.run {
        if (anim == null) {
            anim = ResizeAnim(
                pSettings,
                false,
                (270 * resources.displayMetrics.density).toInt()
            ).apply {
                setStart(10)
                duration = 800
            }
            anim?.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    pSettings.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    pSettings.requestLayout()
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
        }
        pSettings.clearAnimation()
        pSettings.startAnimation(anim)
    }

    private fun formatDate(d: DateHelper): String {
        return resources.getStringArray(R.array.months_short)[d.month - 1].toString() + " " + d.year
    }

    private fun initViews() = binding?.run {
        content.lvResult.adapter = adResults
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSettings() = binding?.run {
        val adMode = ArrayAdapter(
            requireContext(), R.layout.spinner_button,
            resources.getStringArray(R.array.search_mode)
        )
        adMode.setDropDownViewResource(R.layout.spinner_item)
        sMode.adapter = adMode
        val click = View.OnClickListener {
            openSettings()
        }
        fabSettings.setOnClickListener(click)
        bSettings.setOnClickListener(click)
        bStop.setOnClickListener { model.cancel() }
        fabOk.setOnClickListener {
            closeSettings()
            helper.savePerformance(sMode.selectedItemPosition)
        }
        bClearSearch.setOnClickListener {
            adSearch.clear()
            adSearch.notifyDataSetChanged()
            helper.clearRequests()
        }
        bStartRange.setOnClickListener { showDatePicker(0) }
        bEndRange.setOnClickListener { showDatePicker(1) }
        bChangeRange.setOnClickListener {
            helper.changeDates()
            bStartRange.text = formatDate(helper.start)
            bEndRange.text = formatDate(helper.end)
        }
        with(content) {
            bShow.setOnClickListener {
                bShow.isVisible = false
                pAdditionSet.isVisible = true
            }
            bHide.setOnClickListener {
                bShow.isVisible = true
                pAdditionSet.isVisible = false
            }
        }
    }

    private fun enterSearch() = binding?.run {
        content.etSearch.dismissDropDown()
        if (content.etSearch.length() < 3)
            Lib.showToast(getString(R.string.low_sym_for_search))
        else {
            pPages.isVisible = false
            startSearch()
        }
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
            binding?.bStartRange?.text = formatDate(helper.start)
        } else {
            helper.end = date
            binding?.bEndRange?.text = formatDate(helper.end)
        }
        dialog = -1
    }

    private fun startSearch() = binding?.run {
        setStatus(true)
        val mode = if (content.cbSearchInResults.isChecked) {
            tvStatus.text = getString(R.string.search)
            SearchModel.MODE_RESULTS
        } else
            sMode.selectedItemPosition
        val request = content.etSearch.text.toString()
        model.startSearch(request, mode)
        addRequest(request)
    }

    private fun addRequest(request: String) {
        for (i in 0 until adSearch.count) {
            if (adSearch.getItem(i) == request)
                return
        }
        adSearch.add(request)
        adSearch.notifyDataSetChanged()
        helper.saveRequest(request)
    }

    private fun showResult(list: List<ListItem>) = binding?.content?.run {
        adResults.clear()
        if (list.isEmpty()) {
            bShow.isVisible = false
            pAdditionSet.isVisible = false
            cbSearchInResults.isChecked = false
            val builder = AlertDialog.Builder(requireContext(), R.style.NeoDialog)
            builder.setMessage(getString(R.string.alert_search))
            builder.setPositiveButton(getString(android.R.string.ok))
            { dialog: DialogInterface, _ -> dialog.dismiss() }
            builder.create().show()
        } else {
            if (bShow.isVisible.not())
                pAdditionSet.isVisible = true
            tvLabel.text = helper.label
            adResults.setItems(list)
            if (lvResult.firstVisiblePosition > 0) {
                scrollToFirst = true
                lvResult.smoothScrollToPosition(0)
            }
        }
    }

    private fun initPages(max: Int) = binding?.run {
        if (max == 0) {
            pPages.isVisible = false
            fabSettings.isVisible = true
            return@run
        }
        pPages.isVisible = pSettings.isVisible.not()
        fabSettings.isVisible = false
        if (adPages?.itemCount == max)
            adPages?.setSelect(helper.page)
        else {
            adPages = PageAdapter(max, helper.page, this@SearchFragment)
            val layoutManager = LinearLayoutManager(act, LinearLayoutManager.HORIZONTAL, false)
            rvPages.layoutManager = layoutManager
            rvPages.adapter = adPages
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean { //click page item
        if (event.action != MotionEvent.ACTION_UP) return false
        val pos = v.tag as Int
        if (helper.page != pos)
            model.showResult(pos)
        return false
    }

    override fun onChangedState(state: NeoState) {
        when (state) {
            is MessageState ->
                binding?.tvStatus?.text = state.message
            is SuccessList -> {
                initPages(helper.countPages)
                showResult(state.list)
                setStatus(false)
            }
        }
    }
}
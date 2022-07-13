package ru.neosvet.vestnewage.view.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.databinding.BrowserActivityBinding
import ru.neosvet.vestnewage.helper.BrowserHelper
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.network.ConnectObserver
import ru.neosvet.vestnewage.network.ConnectWatcher
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.view.basic.NeoToast
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.basic.StatusButton
import ru.neosvet.vestnewage.view.basic.Tip
import ru.neosvet.vestnewage.view.browser.HeadBar
import ru.neosvet.vestnewage.view.browser.NeoInterface
import ru.neosvet.vestnewage.view.browser.WebClient
import ru.neosvet.vestnewage.viewmodel.BrowserToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState

class BrowserActivity : AppCompatActivity(), ConnectObserver, StateUtils.Host {
    companion object {
        @JvmStatic
        fun openReader(link: String?, search: String?) {
            val intent = Intent(App.context, BrowserActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(Const.LINK, link)
            if (search != null) intent.putExtra(Const.SEARCH, search)
            App.context.startActivity(intent)
        }
    }

    data class NeoMenu(
        val theme: MenuItem,
        val buttons: MenuItem,
        val refresh: MenuItem,
        val share: MenuItem
    )

    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding.content.etSearch)
    }
    private val status = StatusButton()
    private lateinit var headBar: HeadBar
    private lateinit var prom: PromUtils
    private lateinit var menu: NeoMenu
    private var navIsTop = false
    private lateinit var tipFinish: Tip
    private var isTouch = true
    private var isScrollTop = false
    private var isSearch = false
    private var twoPointers = false
    private var scroll: Job? = null
    private val toiler: BrowserToiler by lazy {
        ViewModelProvider(this).get(BrowserToiler::class.java)
    }
    private val stateUtils: StateUtils by lazy {
        StateUtils(this, toiler)
    }
    private val toast: NeoToast by lazy {
        NeoToast(binding.tvToast, null)
    }
    private val wordsUtils: WordsUtils by lazy {
        WordsUtils()
    }
    override val scope: LifecycleCoroutineScope
        get() = lifecycleScope
    private val helper: BrowserHelper
        get() = toiler.helper!!
    private lateinit var binding: BrowserActivityBinding
    private val positionOnPage: Float
        get() = binding.content.wvBrowser.run {
            scrollY.toFloat() / scale / contentHeight.toFloat()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectWatcher.start(this)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        binding = BrowserActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (toiler.helper == null)
            toiler.init(this)
        initViews()
        if (savedInstanceState != null) // for logo in topBar
            ScreenUtils.init(this)
        setHeadBar()
        setBottomBar()
        setViews()
        setContent()
        initWords()
        initTheme()
        restoreState(savedInstanceState)
        stateUtils.runObserve()
    }

    override fun onPause() {
        super.onPause()
        prom.stop()
    }

    override fun onResume() {
        super.onResume()
        prom.resume()
    }

    override fun onDestroy() {
        scroll?.cancel()
        helper.position = positionOnPage
        ConnectWatcher.unSubscribe()
        helper.zoom = (binding.content.wvBrowser.scale * 100f).toInt()
        helper.save()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (isSearch)
            outState.putString(Const.SEARCH, binding.content.etSearch.text.toString())
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) {
        stateUtils.restore()
        if (state == null) {
            val link = intent.getStringExtra(Const.LINK) ?: return
            toiler.openLink(link, true)
            intent.getStringExtra(Const.SEARCH)?.let {
                helper.setSearchString(it)
                isSearch = true
            }
        } else {
            toiler.openLink(helper.link, true)
            state.getString(Const.SEARCH)?.let {
                isSearch = true
                binding.content.etSearch.setText(it)
            }
        }

        if (isSearch) {
            binding.content.pSearch.isVisible = true
            headBar.hide()
        }
        if (helper.isSearch) {
            if (helper.searchIndex > -1) {
                binding.content.etSearch.setText(helper.request)
                binding.content.etSearch.isEnabled = false
            }
        }
    }

    private fun restoreSearch() = helper.run {
        if (searchIndex < 0)
            return
        findRequest()
        var i = 0
        if (prog > 0) while (i < prog) {
            binding.content.wvBrowser.findNext(true)
            i++
        }
        else while (i > prog) {
            binding.content.wvBrowser.findNext(false)
            i--
        }
    }

    private fun restorePosition() {
        if (helper.position == 0f) return
        binding.content.wvBrowser.run {
            val pos = (helper.position * scale * contentHeight.toFloat()).toInt()
            scrollTo(0, pos)
            helper.position = 0f
        }
    }

    private fun closeSearch() {
        tipFinish.hide()
        with(binding.content) {
            if (helper.searchIndex > -1) {
                etSearch.setText("")
                etSearch.isEnabled = true
            }
            pSearch.isVisible = false
            wvBrowser.clearMatches()
        }
        isSearch = false
        softKeyboard.hide()
        helper.clearSearch()
        headBar.show()
    }

    private fun findRequest() {
        val s = helper.request
        if (s.contains(Const.N))
            binding.content.wvBrowser.findAllAsync(s.substring(0, s.indexOf(Const.N)))
        else
            binding.content.wvBrowser.findAllAsync(s)
    }

    private fun initViews() {
        tipFinish = Tip(this, binding.tvFinish)
        status.init(this, binding.pStatus)

        val pref = getSharedPreferences(MainHelper.TAG, MODE_PRIVATE)
        prom = if (pref.getBoolean(Const.PROM_FLOAT, false))
            PromUtils(binding.tvPromTimeFloat)
        else
            PromUtils(binding.tvPromTimeHead)
    }

    override fun onBackPressed() {
        when {
            status.isVisible -> {
                ErrorUtils.clear()
                status.setError(null)
                bottomUnblocked()
            }
            binding.bottomBar.isScrolledDown -> {
                if (isSearch.not())
                    headBar.show()
                binding.bottomBar.performShow()
            }
            isSearch ->
                closeSearch()
            toiler.onBackBrowser().not() ->
                super.onBackPressed()
        }
    }

    private fun setViews() = binding.run {
        bBack.setOnClickListener { finish() }
        fabNav.setOnClickListener {
            headBar.blocked()
            if (navIsTop) {
                content.wvBrowser.scrollTo(0, 0)
                if (isSearch.not()) {
                    headBar.unblocked()
                    headBar.expanded()
                }
                bottomBar.performShow()
            } else {
                isTouch = false
                with(content.wvBrowser) {
                    scrollTo(0, (contentHeight * scale).toInt())
                }
                isTouch = false
                if (isSearch.not()) {
                    headBar.hide()
                    headBar.unblocked()
                }
                bottomBar.performHide()
            }
        }
        if (helper.isNavButton)
            setCheckItem(menu.buttons, true)
        else
            fabNav.isVisible = false
        status.setClick {
            if (toiler.isRun)
                toiler.cancel()
            else {
                bottomUnblocked()
                status.onClick()
            }
        }
    }

    private fun initWords() {
        val funClick = { v: View ->
            wordsUtils.showAlert(this) {
                val main = Intent(this, MainActivity::class.java)
                main.putExtra(Const.START_SCEEN, false)
                main.putExtra(Const.SEARCH, it)
                startActivity(main)
            }
        }
        binding.run {
            btnGodWords.setOnClickListener(funClick)
            tvGodWords.setOnClickListener(funClick)
            if (ScreenUtils.isLand) {
                ivHead.setImageResource(R.drawable.headland)
                val p = tvGodWords.paddingBottom
                tvGodWords.setPadding(p, p, tvGodWords.paddingEnd, p)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setContent() = binding.content.run {
        etSearch.requestLayout()
        wvBrowser.settings.builtInZoomControls = true
        wvBrowser.settings.displayZoomControls = false
        wvBrowser.settings.javaScriptEnabled = true
        wvBrowser.settings.allowContentAccess = true
        wvBrowser.settings.allowFileAccess = true
        wvBrowser.addJavascriptInterface(NeoInterface(toiler), "NeoInterface")
        if (helper.zoom > 0)
            wvBrowser.setInitialScale(helper.zoom)
        wvBrowser.webViewClient = WebClient(this@BrowserActivity)
        wvBrowser.setOnTouchListener { _, event ->
            if (event.pointerCount == 2) {
                isTouch = false
                if (twoPointers)
                    wvBrowser.setInitialScale((wvBrowser.scale * 100.0).toInt())
                twoPointers = twoPointers.not()
                return@setOnTouchListener false
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                scroll?.cancel()
                if (isSearch.not() && wvBrowser.scrollY == 0) {
                    isTouch = false
                    if (headBar.isExpanded.not()) {
                        headBar.expanded()
                        scroll = lifecycleScope.launch {
                            delay(300)
                            wvBrowser.post { wvBrowser.scrollTo(0, 0) }
                        }
                    }
                } else isTouch = true
            }
            return@setOnTouchListener false
        }

        wvBrowser.setOnScrollChangeListener { _, _, scrollY: Int, _, oldScrollY: Int ->
            if (isTouch) {
                isScrollTop = scrollY >= oldScrollY
                if (isSearch || headBar.onScrollHost(scrollY, oldScrollY)) {
                    isTouch = false
                }
                if (isScrollTop)
                    binding.bottomBar.performHide()
                else
                    binding.bottomBar.performShow()
            } else if (isScrollTop != scrollY >= oldScrollY) {
                isTouch = true
            }

            if (!helper.isNavButton) return@setOnScrollChangeListener
            if (scrollY > 300) {
                binding.fabNav.setImageResource(R.drawable.ic_top)
                navIsTop = true
            } else {
                binding.fabNav.setImageResource(R.drawable.ic_bottom)
                navIsTop = false
            }
        }
        etSearch.setOnKeyListener { _, keyCode: Int, keyEvent: KeyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == EditorInfo.IME_ACTION_SEARCH
            ) {
                if (etSearch.length() > 0) {
                    softKeyboard.hide()
                    helper.setSearchString(etSearch.text.toString())
                    findRequest()
                }
                return@setOnKeyListener true
            }
            false
        }
        bPrev.setOnClickListener {
            if (helper.prevSearch()) {
                etSearch.setText(helper.request)
                findRequest()
                return@setOnClickListener
            }
            softKeyboard.hide()
            initSearch()
            helper.downProg()
            wvBrowser.findNext(false)
        }
        bNext.setOnClickListener {
            if (helper.nextSearch()) {
                etSearch.setText(helper.request)
                findRequest()
                return@setOnClickListener
            }
            softKeyboard.hide()
            initSearch()
            helper.upProg()
            wvBrowser.findNext(true)
        }
        bClose.setOnClickListener { closeSearch() }
        etSearch.doAfterTextChanged {
            bClear.isVisible = it?.isNotEmpty() ?: false
        }
        bClear.setOnClickListener { etSearch.setText("") }
    }

    private fun initTheme() = binding.content.run {
        toiler.lightTheme = helper.isLightTheme
        val context = this@BrowserActivity
        if (helper.isLightTheme) {
            etSearch.setTextColor(ContextCompat.getColor(context, android.R.color.black))
            root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
            menu.theme.title = getString(R.string.dark_theme)
        } else {
            etSearch.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            menu.theme.title = getString(R.string.light_theme)
        }
    }

    private fun setHeadBar() = binding.run {
        if (ScreenUtils.isTablet)
            ivHead.setImageResource(R.drawable.headtablet)
        else if (ScreenUtils.isLand)
            ivHead.setImageResource(R.drawable.headland)
        headBar = HeadBar(
            mainView = ivHead,
            distanceForHide = if (ScreenUtils.isLand) 50 else 100,
            additionViews = listOf(bBack, tvPromTimeHead)
        ) {
            if (menu.refresh.isVisible)
                Lib.openInApps(NeoClient.SITE + helper.link, null)
            else
                Lib.openInApps(NeoClient.SITE, null)
        }
    }

    private fun setBottomBar() = binding.run {
        bottomBar.menu.let {
            menu = NeoMenu(
                refresh = it.getItem(4),
                share = it.getItem(1),
                buttons = it.getItem(0).subMenu.getItem(0),
                theme = it.getItem(0).subMenu.getItem(1),
            )
        }
        bottomBar.setBackgroundResource(R.drawable.panel_bg)
        bottomBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.nav_refresh ->
                    toiler.refresh()
                R.id.nav_share ->
                    helper.sharePage(this@BrowserActivity, getPageTitle())
                R.id.nav_buttons -> {
                    helper.isNavButton = helper.isNavButton.not()
                    setCheckItem(it, helper.isNavButton)
                    fabNav.isVisible = helper.isNavButton
                }
                R.id.nav_search -> with(content) {
                    headBar.hide()
                    pSearch.isVisible = true
                    isSearch = true
                    etSearch.post { etSearch.requestFocus() }
                    softKeyboard.show()
                }
                R.id.nav_marker -> {
                    val des = if (helper.isSearch)
                        getString(R.string.search_for) + " “" + helper.request + "”"
                    else ""
                    MarkerActivity.addByPos(
                        this@BrowserActivity,
                        helper.link,
                        positionOnPage,
                        des
                    )
                }
                R.id.nav_opt_scale, R.id.nav_src_scale -> {
                    helper.zoom = if (it.itemId == R.id.nav_opt_scale) 0 else 100
                    helper.save()
                    openReader(helper.link, null)
                    finish()
                }
                R.id.nav_theme -> {
                    helper.isLightTheme = helper.isLightTheme.not()
                    initTheme()
                    content.wvBrowser.clearCache(true)
                    toiler.openPage(false)
                }
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun getPageTitle(): String {
        val t = binding.content.wvBrowser.title
        if (t.isNullOrEmpty())
            return getString(R.string.default_title)
        return t
    }

    private fun setCheckItem(item: MenuItem, check: Boolean) {
        item.setIcon(if (check) R.drawable.checkbox_simple else R.drawable.uncheckbox_simple)
    }

    fun onPageFinished(isLocal: Boolean) {
        if (isLocal) {
            val unread = UnreadUtils()
            unread.deleteLink(helper.link)
        }

        lifecycleScope.launch {
            delay(250)
            binding.content.wvBrowser.post {
                if (helper.isSearch)
                    restoreSearch()
                else
                    restorePosition()
            }
        }
    }

    private fun initSearch() {
        if (helper.isSearch)
            findRequest()
    }

    fun openLink(url: String) {
        helper.clearSearch()
        toiler.openLink(url, true)
    }

    fun onBack() {
        toiler.openPage(false)
    }

    override fun onChangedState(state: NeoState) {
        when (state) {
            NeoState.Loading -> {
                bottomBlocked()
                binding.content.wvBrowser.clearCache(true)
                status.setLoad(true)
                status.loadText()
            }
            NeoState.NoConnected -> {
                finishLoading()
                ConnectWatcher.subscribe(this)
                if (ConnectWatcher.needShowMessage())
                    toast.show(getString(R.string.no_connected))
            }
            is NeoState.Page -> {
                finishLoading()
                binding.content.wvBrowser.loadUrl(state.url)
                menu.refresh.isVisible = state.isOtkr.not()
                menu.share.isVisible = state.isOtkr.not()
            }
            NeoState.Ready ->
                binding.tvNotFound.isVisible = true
            NeoState.Success ->
                tipFinish.show()
            is NeoState.Error ->
                status.setError(state.throwable.localizedMessage)
            else -> {}
        }
    }

    private fun finishLoading() {
        binding.tvNotFound.isVisible = false
        if (status.isVisible)
            status.setLoad(false)
        bottomUnblocked()
    }

    override fun connectChanged(connected: Boolean) {
        if (connected) {
            this.runOnUiThread {
                status.setLoad(true)
                toiler.load()
            }
            ConnectWatcher.unSubscribe()
        }
    }

    private fun bottomBlocked() {
        binding.bottomBar.isVisible = false
        binding.fabNav.isVisible = false
    }

    private fun bottomUnblocked() {
        binding.bottomBar.isVisible = true
        binding.fabNav.isVisible = helper.isNavButton
    }
}
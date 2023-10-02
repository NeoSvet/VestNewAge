package ru.neosvet.vestnewage.view.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.Window
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
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
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.PromUtils
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.utils.UnreadUtils
import ru.neosvet.vestnewage.utils.WordsUtils
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.view.basic.BottomAnim
import ru.neosvet.vestnewage.view.basic.NeoSnackbar
import ru.neosvet.vestnewage.view.basic.NeoToast
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.basic.StatusButton
import ru.neosvet.vestnewage.view.basic.Tip
import ru.neosvet.vestnewage.view.basic.convertDpi
import ru.neosvet.vestnewage.view.basic.fromDpi
import ru.neosvet.vestnewage.view.browser.HeadBar
import ru.neosvet.vestnewage.view.browser.NeoInterface
import ru.neosvet.vestnewage.view.browser.WebClient
import ru.neosvet.vestnewage.view.dialog.ShareDialog
import ru.neosvet.vestnewage.viewmodel.BrowserToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.BrowserState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class BrowserActivity : AppCompatActivity() {
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
        val numpar: MenuItem,
        val buttons: MenuItem,
        val top: MenuItem,
        val autoreturn: MenuItem,
        val refresh: MenuItem
    )

    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding.content.etSearch)
    }
    private lateinit var status: StatusButton
    private lateinit var headBar: HeadBar
    private lateinit var prom: PromUtils
    private lateinit var menu: NeoMenu
    private var animButton: BottomAnim? = null
    private var navIsTop = false
    private lateinit var tipFinish: Tip
    private var isTouch = true
    private var isScrollTop = false
    private var isSearch = false
    private var twoPointers = false
    private var isBlocked = false
    private var jobScroll: Job? = null
    var currentScale = 1f
    private val toiler: BrowserToiler by lazy {
        ViewModelProvider(this)[BrowserToiler::class.java]
    }
    private val toast: NeoToast by lazy {
        NeoToast(binding.tvToast, null)
    }
    private val snackbar = NeoSnackbar()
    private val unread = UnreadUtils()
    private var connectWatcher: Job? = null
    private lateinit var binding: BrowserActivityBinding
    private lateinit var helper: BrowserHelper
    private var link = ""
    private var searchIndex = -1
    private val positionOnPage: Float
        get() = binding.content.wvBrowser.run {
            (scrollY.toFloat() / currentScale / contentHeight.toFloat()) * 100f
        }
    private val isBigHead: Boolean
        get() {
            val h = resources.getDimension(R.dimen.head_height) / resources.displayMetrics.density
            return h > 110
        }
    private val isEndScroll: Boolean
        get() = binding.content.wvBrowser.run {
            scrollY + measuredHeight >= (contentHeight * currentScale).toInt()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = BrowserActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ScreenUtils.init(this) // for logo in topBar
        initViews()
        initHeadBar()
        setBottomBar()
        setViews()
        setContent()
        initWords()
        if (savedInstanceState == null) initArguments()
        runObserve()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun initArguments() {
        val h = BrowserHelper(this)
        intent?.getStringExtra(Const.LINK)?.let { s ->
            link = s
            intent.getStringExtra(Const.SEARCH)?.let {
                isSearch = true
                h.setSearchString(it)
                binding.content.etSearch.setText(h.request)
            }
        }
        setHelper(h)
        changeArguments()
    }

    private fun setHelper(helper: BrowserHelper) {
        this.helper = helper
        currentScale = helper.zoom / 100f
        initOptions()
        if (helper.isFullScreen) binding.bottomBar.post {
            switchFullScreen(true)
        } else bottomUnblocked()
        with(binding.content) {
            if (isSearch) {
                pSearch.isVisible = true
                headBar.hide()
                bottomBlocked()
            }
            if (helper.isSearch && helper.place.size > 1) {
                etSearch.isEnabled = false
                etSearch.updatePadding(
                    right = fromDpi(R.dimen.def_indent)
                )
                bClear.isVisible = false
            }
        }
    }

    private fun changeArguments() {
        toiler.setArgument(
            link = link,
            isLightTheme = helper.isLightTheme,
            isNumPar = helper.isNumPar
        )
    }

    private fun runObserve() {
        lifecycleScope.launch {
            toiler.state.collect {
                onChangedState(it)
            }
        }
        toiler.start(this)
    }

    override fun onPause() {
        super.onPause()
        prom.stop()
    }

    override fun onResume() {
        super.onResume()
        prom.resume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        helper.position = positionOnPage
        helper.zoom = (currentScale * 100f).toInt()
        val s = if (isSearch)
            binding.content.etSearch.text.toString()
        else null
        toiler.setStatus(
            BrowserState.Status(
                helper = helper,
                search = s,
                index = searchIndex
            )
        )
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        helper.save()
        jobScroll?.cancel()
        super.onDestroy()
    }

    private fun restoreSearch() = helper.run {
        if (placeIndex < 0) return
        if (helper.isSearch) findRequest()
        var i = -1
        val final = searchIndex
        while (i < final) {
            binding.content.wvBrowser.findNext(true)
            i++
        }
    }

    private fun restorePosition() = binding.content.wvBrowser.run {
        if (helper.position == 0f) return
        val pos = (helper.position / 100f * currentScale * contentHeight.toFloat()).toInt()
        scrollTo(0, pos)
    }

    private fun closeSearch() {
        tipFinish.hide()
        with(binding.content) {
            if (helper.placeIndex > -1) {
                etSearch.setText("")
                etSearch.isEnabled = true
                etSearch.updatePadding(
                    right = resources.getDimension(R.dimen.padding_for_clear).toInt()
                )
            }
            pSearch.isVisible = false
            wvBrowser.clearMatches()
        }
        isSearch = false
        softKeyboard.hide()
        helper.clearSearch()
        headBar.show()
        bottomUnblocked()
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
        status = StatusButton(this, binding.pStatus)

        val pref = getSharedPreferences(MainHelper.TAG, MODE_PRIVATE)
        prom = if (pref.getBoolean(Const.PROM_FLOAT, false))
            PromUtils(binding.tvPromTimeFloat)
        else
            PromUtils(binding.tvPromTimeHead)
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                snackbar.isShown ->
                    snackbar.hide()

                status.isVisible -> {
                    toiler.cancel()
                    status.setError(null)
                    bottomUnblocked()
                }

                helper.isFullScreen ->
                    switchFullScreen(false)

                binding.bottomBar.isScrolledDown -> {
                    if (isSearch.not())
                        headBar.show()
                    bottomShow()
                }

                isSearch ->
                    closeSearch()

                toiler.onBackBrowser().not() ->
                    finish()
            }
        }
    }

    private fun setViews() = binding.run {
        bBack.setOnClickListener { finish() }
        fabNav.setOnClickListener {
            if (helper.isNavButton.not()) {
                switchFullScreen(true)
                return@setOnClickListener
            }
            headBar.blocked()
            if (navIsTop) {
                content.wvBrowser.scrollTo(0, 0)
                if (isSearch.not()) {
                    headBar.unblocked()
                    headBar.expanded()
                }
                bottomShow()
            } else {
                isTouch = false
                with(content.wvBrowser) {
                    scrollTo(0, (contentHeight * currentScale).toInt())
                }
                isTouch = false
                if (isSearch.not()) {
                    headBar.hide()
                    headBar.unblocked()
                }
                bottomHide()
            }
        }
        status.setClick {
            if (isBlocked)
                toiler.cancel()
            else
                status.onClick()
            finishLoading()
        }
    }

    private fun initOptions() {
        binding.content.wvBrowser.setInitialScale(helper.zoom)
        if (helper.isNavButton)
            setCheckItem(menu.buttons, true)
        switchNavButton()
        if (helper.isMiniTop)
            setCheckItem(menu.top, true)
        if (helper.isAutoReturn)
            setCheckItem(menu.autoreturn, true)
        if (helper.isNumPar)
            setCheckItem(menu.numpar, true)
        initTheme()
    }

    private fun initWords() {
        binding.run {
            tvGodWords.setOnClickListener {
                val context = this@BrowserActivity
                WordsUtils.showAlert(context) {
                    val main = Intent(context, MainActivity::class.java)
                    main.putExtra(Const.START_SCEEN, false)
                    main.putExtra(Const.SEARCH, it)
                    startActivity(main)
                }
            }
            if (ScreenUtils.isLand) {
                val p = tvGodWords.paddingBottom
                tvGodWords.setPadding(p, p, tvGodWords.paddingEnd, p)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun setContent() = with(binding.content) {
        etSearch.requestLayout()
        wvBrowser.settings.builtInZoomControls = true
        wvBrowser.settings.displayZoomControls = false
        wvBrowser.settings.javaScriptEnabled = true
        wvBrowser.settings.allowContentAccess = true
        wvBrowser.settings.allowFileAccess = true
        wvBrowser.addJavascriptInterface(NeoInterface(toiler), "NeoInterface")
        wvBrowser.webViewClient = WebClient(this@BrowserActivity)
        wvBrowser.setOnTouchListener { _, event ->
            if (event.pointerCount == 2) {
                isTouch = false
                if (twoPointers)
                    wvBrowser.setInitialScale((currentScale * 100f).toInt())
                twoPointers = twoPointers.not()
                return@setOnTouchListener false
            }
            if (helper.isFullScreen) return@setOnTouchListener false
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                jobScroll?.cancel()
                if (isSearch.not() && wvBrowser.scrollY == 0) {
                    isTouch = false
                    if (helper.isAutoReturn && headBar.isExpanded.not()) {
                        headBar.expanded()
                        jobScroll = lifecycleScope.launch {
                            delay(250)
                            wvBrowser.post { wvBrowser.scrollTo(0, 0) }
                        }
                    }
                } else {
                    isTouch = true
                    if (isEndScroll) bottomHide()
                }
            }
            return@setOnTouchListener false
        }

        wvBrowser.setOnScrollChangeListener { _, _, scrollY: Int, _, oldScrollY: Int ->
            if (helper.isNavButton)
                setNavButton(scrollY)
            if (helper.isFullScreen) return@setOnScrollChangeListener
            if (isTouch) {
                isScrollTop = scrollY >= oldScrollY
                if (isScrollTop.not()) if (helper.isAutoReturn.not())
                    return@setOnScrollChangeListener
                if (isSearch || headBar.onScrollHost(scrollY, oldScrollY)) {
                    isTouch = false
                }
                if (isScrollTop) {
                    if (headBar.isHided) bottomHide()
                } else bottomShow()
            } else if (isScrollTop != scrollY >= oldScrollY) {
                isTouch = true
            }
        }
        etSearch.setOnKeyListener { _, keyCode: Int, keyEvent: KeyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == EditorInfo.IME_ACTION_SEARCH
            ) {
                if (etSearch.length() > 0)
                    initSearch()
                return@setOnKeyListener true
            }
            false
        }
        bPrev.setOnClickListener {
            if (helper.prevSearch()) {
                etSearch.setText(helper.request)
                findRequest()
            } else if (etSearch.length() > 0) {
                initSearch()
                wvBrowser.findNext(false)
            }
        }
        bNext.setOnClickListener {
            if (helper.nextSearch()) {
                etSearch.setText(helper.request)
                findRequest()
            } else if (etSearch.length() > 0) {
                initSearch()
                wvBrowser.findNext(true)
            }
        }
        wvBrowser.setFindListener { activeMatchOrdinal, _, isDoneCounting -> //numberOfMatches
            if (isDoneCounting)
                searchIndex = activeMatchOrdinal
        }
        bClose.setOnClickListener { closeSearch() }
        etSearch.doAfterTextChanged {
            if (etSearch.isEnabled)
                bClear.isVisible = it?.isNotEmpty() ?: false
        }
        bClear.setOnClickListener { etSearch.setText("") }
    }

    private fun bottomHide() {
        animButton?.hide()
        binding.bottomBar.performHide()
    }

    private fun bottomShow() {
        if (isSearch) return
        animButton?.show()
        binding.bottomBar.performShow()
    }

    private fun setNavButton(scrollY: Int) {
        navIsTop = if (scrollY > 300) {
            binding.fabNav.setImageResource(R.drawable.ic_top)
            true
        } else {
            binding.fabNav.setImageResource(R.drawable.ic_bottom)
            false
        }
    }

    private fun initTheme() = with(binding.content) {
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

    private fun initHeadBar() = binding.run {
        headBar = HeadBar(
            mainView = ivHeadBack,
            distanceForHide = if (ScreenUtils.isLand) 50 else 100,
            additionViews = listOf(tvGodWords, btnFullScreen)
        ) {
            if (link.contains(Const.DOCTRINE))
                Urls.openInBrowser(Urls.DoctrineSite)
            else if (menu.refresh.isVisible)
                Urls.openInBrowser(Urls.Site + link)
            else
                Urls.openInBrowser(Urls.Site)
        }
        btnFullScreen.setOnClickListener {
            switchFullScreen(true)
        }
    }

    private fun switchFullScreen(value: Boolean) {
        helper.isFullScreen = value
        if (value) {
            headBar.hide()
            bottomBlocked()
        } else {
            headBar.show()
            bottomUnblocked()
        }
    }

    private fun setHeadBar(isDoctrine: Boolean) = binding.run {
        if (ivHeadBack.isVisible) return@run
        if (isDoctrine) {
            if (ScreenUtils.isTablet && isBigHead)
                ivHeadBack.setImageResource(R.drawable.head_back_tablet_d)
            else if (ScreenUtils.isLand)
                ivHeadBack.setImageResource(R.drawable.head_back_land_d)
            else
                ivHeadBack.setImageResource(R.drawable.head_back_d)
            ivHeadFront.setImageResource(R.drawable.head_front_d)
        } else if (ScreenUtils.isTablet && isBigHead)
            ivHeadBack.setImageResource(R.drawable.head_back_tablet)
        else if (ScreenUtils.isLand)
            ivHeadBack.setImageResource(R.drawable.head_back_land)
        if (headBar.isHided) return@run
        ivHeadBack.isVisible = true
        ivHeadFront.isVisible = true
        ivHeadBack.post {
            headBar.setExpandable(helper.isMiniTop)
        }
    }

    private fun setBottomBar() = binding.run {
        bottomBar.menu.let { main ->
            main.getItem(0).subMenu?.let {
                menu = NeoMenu(
                    refresh = main.getItem(4),
                    buttons = it.getItem(0),
                    top = it.getItem(1),
                    autoreturn = it.getItem(2),
                    numpar = it.getItem(3),
                    theme = it.getItem(4)
                )
            }
        }
        bottomBar.setBackgroundResource(R.drawable.bottombar_bg)
        bottomBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.nav_refresh ->
                    toiler.refresh()

                R.id.nav_share ->
                    ShareDialog.newInstance(link).show(supportFragmentManager, null)

                R.id.nav_buttons -> {
                    helper.isNavButton = helper.isNavButton.not()
                    setCheckItem(it, helper.isNavButton)
                    switchNavButton()
                }

                R.id.nav_minitop -> {
                    helper.isMiniTop = helper.isMiniTop.not()
                    setCheckItem(it, helper.isMiniTop)
                    headBar.setExpandable(helper.isMiniTop)
                }

                R.id.nav_autoreturn -> {
                    helper.isAutoReturn = helper.isAutoReturn.not()
                    setCheckItem(it, helper.isAutoReturn)
                }

                R.id.nav_numpar -> {
                    helper.isNumPar = helper.isNumPar.not()
                    setCheckItem(it, helper.isNumPar)
                    changeArguments()
                    toiler.openPage(true)
                }

                R.id.nav_search ->
                    goSearch(true)

                R.id.nav_marker -> {
                    val des = if (helper.isSearch)
                        getString(R.string.search_for) + " “" + helper.request + "”"
                    else ""
                    MarkerActivity.addByPos(
                        this@BrowserActivity, link, positionOnPage, des
                    )
                }

                R.id.nav_opt_scale, R.id.nav_src_scale -> {
                    helper.zoom = if (it.itemId == R.id.nav_opt_scale)
                        convertDpi(100) else 100
                    helper.save()
                    openReader(link, null)
                    finish()
                }

                R.id.nav_theme -> {
                    helper.isLightTheme = helper.isLightTheme.not()
                    initTheme()
                    content.wvBrowser.clearCache(true)
                    changeArguments()
                    toiler.openPage(false)
                }
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun switchNavButton() = binding.run {
        if (helper.isNavButton) {
            btnFullScreen.isVisible = tvGodWords.isVisible
            btnFullScreen.tag = null
            animButton = null
            setNavButton(content.wvBrowser.scrollY)
            fabNav.alpha = 0.5f
        } else {
            btnFullScreen.isVisible = false
            btnFullScreen.tag = "v"
            animButton = BottomAnim(binding.fabNav)
            fabNav.setImageResource(R.drawable.ic_fullscreen)
            fabNav.alpha = 1f
        }
    }

    private fun goSearch(withPreparing: Boolean) = with(binding.content) {
        headBar.hide()
        bottomBlocked()
        pSearch.isVisible = true
        isSearch = true
        if (withPreparing) {
            etSearch.post { etSearch.requestFocus() }
            softKeyboard.show()
        }
    }

    private fun setCheckItem(item: MenuItem, check: Boolean) {
        item.setIcon(if (check) R.drawable.checkbox_simple else R.drawable.uncheckbox_simple)
    }

    fun onPageFinished(isLocal: Boolean) {
        if (isLocal)
            unread.deleteLink(link)
        lifecycleScope.launch {
            delay(250)
            binding.content.wvBrowser.post {
                if (helper.isSearch) restoreSearch()
                else restorePosition()
            }
        }
    }

    private fun initSearch() {
        softKeyboard.hide()
        if (helper.isSearch) return
        helper.setSearchString(binding.content.etSearch.text.toString())
        findRequest()
    }

    fun openLink(url: String) {
        helper.clearSearch()
        toiler.openLink(url, true)
    }

    fun onBack() {
        toiler.openPage(false)
    }

    private fun onChangedState(state: NeoState) {
        when (state) {
            BasicState.Loading -> {
                isBlocked = true
                bottomBlocked()
                binding.content.wvBrowser.clearCache(true)
                status.setLoad(true)
                status.loadText()
            }

            BasicState.NoConnected ->
                noConnected()

            is BrowserState.Primary -> {
                finishLoading()
                setHeadBar(state.type == BrowserState.Type.DOCTRINE)
                link = state.link
                binding.content.wvBrowser.loadUrl(state.url)
                menu.numpar.isVisible = link.isPoem
                menu.refresh.isVisible = state.type != BrowserState.Type.OLD_BOOK
            }

            is BrowserState.Status ->
                restoreStatus(state)

            BasicState.Ready -> {
                finishLoading()
                binding.tvNotFound.isVisible = true
                menu.refresh.isVisible = false
            }

            BasicState.Success ->
                tipFinish.show()

            is BasicState.Error ->
                if (state.isNeedReport)
                    status.setError(state)
                else {
                    finishLoading()
                    snackbar.show(binding.fabNav, state.message)
                }
        }
    }

    private fun restoreStatus(state: BrowserState.Status) {
        state.search?.let {
            binding.content.etSearch.setText(it)
            goSearch(false)
        }
        searchIndex = state.index
        setHelper(state.helper)
    }

    private fun noConnected() {
        finishLoading()
        connectWatcher = lifecycleScope.launch {
            OnlineObserver.isOnline.collect {
                connectChanged(it)
            }
        }
        if (OnlineObserver.needShowMessage())
            toast.show(getString(R.string.no_connected))
    }

    private fun finishLoading() {
        isBlocked = false
        binding.tvNotFound.isVisible = false
        if (status.isVisible)
            status.setLoad(false)
    }

    private fun connectChanged(connected: Boolean) {
        if (connected) {
            this.runOnUiThread {
                status.setLoad(true)
                toiler.load()
            }
            connectWatcher?.cancel()
        }
    }

    private fun bottomBlocked() {
        binding.fabNav.isVisible = false
        binding.bottomBar.isVisible = false
    }

    private fun bottomUnblocked() {
        if (isSearch) return
        binding.fabNav.isVisible = true
        binding.bottomBar.isVisible = true
    }
}
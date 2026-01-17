package ru.neosvet.vestnewage.view.activity

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Insets
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.ActionMenuView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.MenuItem
import ru.neosvet.vestnewage.databinding.BrowserActivityBinding
import ru.neosvet.vestnewage.helper.BrowserHelper
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.storage.UnreadStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.InsetsUtils
import ru.neosvet.vestnewage.utils.PromUtils
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.utils.ScreenUtils.Type
import ru.neosvet.vestnewage.utils.TipUtils
import ru.neosvet.vestnewage.utils.WordsUtils
import ru.neosvet.vestnewage.utils.isDoctrineBook
import ru.neosvet.vestnewage.utils.isPoem
import ru.neosvet.vestnewage.view.basic.NeoSnackbar
import ru.neosvet.vestnewage.view.basic.NeoToast
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.basic.StatusButton
import ru.neosvet.vestnewage.view.basic.Y
import ru.neosvet.vestnewage.view.basic.convertToDpi
import ru.neosvet.vestnewage.view.basic.defIndent
import ru.neosvet.vestnewage.view.basic.fromDpi
import ru.neosvet.vestnewage.view.browser.HeadBar
import ru.neosvet.vestnewage.view.browser.NeoInterface
import ru.neosvet.vestnewage.view.browser.ReaderClient
import ru.neosvet.vestnewage.view.dialog.ShareDialog
import ru.neosvet.vestnewage.view.list.MenuAdapter
import ru.neosvet.vestnewage.viewmodel.BrowserToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.BrowserState
import ru.neosvet.vestnewage.viewmodel.state.NeoState
import kotlin.math.abs

class BrowserActivity : AppCompatActivity(), ReaderClient.Parent, NeoInterface.Parent {
    companion object {
        @JvmStatic
        fun openReader(link: String?, search: String?) {
            val intent = Intent(App.context, BrowserActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(Const.LINK, link)
            if (!search.isNullOrEmpty()) intent.putExtra(Const.SEARCH, search)
            App.context.startActivity(intent)
        }

        private const val MENU_NAVBUTTONS = 0
        private const val MENU_MINITOP = 1
        private const val MENU_AUTORETURN = 2
        private const val MENU_NUMPAR = 3
        private const val MENU_THEME = 4
    }

    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding.content.etSearch)
    }
    private lateinit var status: StatusButton
    private lateinit var headBar: HeadBar
    private lateinit var prom: PromUtils
    private lateinit var refreshItem: android.view.MenuItem
    private var insetsUtils: InsetsUtils? = null
    private val isNewAndroid: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    private var navIsTop = false
    private var isSearch = false
    private var twoPointers = false
    private var isBlocked = false
    private var isFullScreen = false
    private var currentScale = 1f
    private val toiler: BrowserToiler by lazy {
        ViewModelProvider(this)[BrowserToiler::class.java]
    }
    private val toast: NeoToast by lazy {
        NeoToast(binding.tvToast, null)
    }
    private val snackbar = NeoSnackbar()
    private val unread = UnreadStorage()
    private var connectWatcher: Job? = null
    private lateinit var binding: BrowserActivityBinding
    private lateinit var adMenu: MenuAdapter
    private var helper = BrowserHelper()
    private var link = ""
    private var searchIndex = -1
    private var lastScroll = 0
    private var positionForRestore = 0f
    private var positionFactor = 1f
    private var bottomX = 0
    private var position = 0f
    private var headResId = R.drawable.head_back

    private val positionOnPage: Float
        get() = binding.content.wvBrowser.run {
            (scrollY.toFloat() / currentScale / contentHeight.toFloat()) * 100f * positionFactor
        }
    private val isBigHead: Boolean
        get() {
            val h = resources.getDimension(R.dimen.head_height) / resources.displayMetrics.density
            return h > 110
        }
    private val isEndScroll: Boolean
        get() = binding.content.wvBrowser.run {
            scrollY + measuredHeight >= (contentHeight * currentScale).toInt() - 20
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BrowserActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ScreenUtils.init(this) // for logo in topBar
        initViews()
        initHeadBar()
        setBottomBar()
        setViews()
        setContent()
        initWords()
        if (isNewAndroid) initInsetsUtils()
        else binding.bottomBar.post {
            switchPromBottom()
            binding.rvMenu.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = binding.bottomBar.measuredHeight
            }
        }
        if (savedInstanceState == null) {
            initArguments()
            TipUtils.showTipIfNeed(TipUtils.Type.BROWSER)
        }
        runObserve()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun initArguments() {
        intent?.getStringExtra(Const.LINK)?.let { s ->
            link = s
            intent.getStringExtra(Const.SEARCH)?.let {
                isSearch = true
                helper.setSearchString(it)
                binding.content.etSearch.setText(helper.request)
            }
        }
        applyHelper()
        changeArguments()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initInsetsUtils() {
        insetsUtils = InsetsUtils(binding.ivHeadBack, this).apply {
            applyInsets = { insets ->
                if (binding.ivHeadBack.isVisible) {
                    setVerticalInsets(insets)
                    if (isSideNavBar)
                        setSideInsets(insets)
                    true
                } else false
            }
            init(window)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setSideInsets(insets: Insets) {
        binding.tvPromTimeFloat.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = insets.left + defIndent
        }
        binding.tvGodWords.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            rightMargin = insets.right
        }
        binding.btnBack.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = insets.left
        }
        binding.btnFullScreen.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = insets.left + defIndent
        }
        binding.rvMenu.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = insets.left
        }
        binding.bottomBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = insets.left
            rightMargin = insets.right
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setVerticalInsets(insets: Insets) {
        val vis = binding.bottomBar.isVisible
        if (!vis) binding.bottomBar.isVisible = true
        setSwitchHead(insets.top)
        binding.tvGodWords.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToTop = -1
            bottomToBottom = R.id.ivHeadBack
        }
        binding.btnBack.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = insets.top / 2
        }

        if (insets.bottom > 0) {
            binding.bottomBar.updatePadding(bottom = insets.bottom - defIndent)
            binding.bottomBar.children.first()
                .addOnLayoutChangeListener { v, _, top, _, _, _, _, _, _ ->
                    if (top > 0) v.top = 0
                }
            binding.bottomBar.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (v.minimumHeight < v.measuredHeight)
                    v.minimumHeight = v.measuredHeight
            }
        }
        binding.tvPromTimeFloat.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin += insets.bottom
        }
        binding.bottomBar.post {
            switchPromBottom()
            binding.rvMenu.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = binding.bottomBar.measuredHeight
            }
        }
        binding.content.wvBrowser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = insets.left
            rightMargin = insets.right
            bottomMargin = insets.bottom
        }
        if (!vis) binding.bottomBar.post { binding.bottomBar.isVisible = false }
    }

    private fun setSwitchHead(statusHeight: Int) {
        val h = statusHeight + statusHeight / 2
        if (h > headBar.collapsedH)
            headBar.collapsedH = h
        headBar.funcGone = {
            binding.ivHeadFront.isVisible = false
            binding.btnBack.isVisible = false
            binding.ivHeadBack.setImageResource(R.drawable.topbar_bg)
            binding.ivHeadBack.updateLayoutParams<ViewGroup.LayoutParams> {
                height = statusHeight
            }
        }
        headBar.funcShow = {
            binding.ivHeadFront.isVisible = true
            binding.btnBack.isVisible = true
            binding.ivHeadBack.setImageResource(headResId)
        }
        if (headBar.isBlocked) {
            headBar.hide()
            headBar.unblocked()
        }
    }

    private fun switchPromBottom() {
        if (!binding.tvPromTimeFloat.isVisible) return
        binding.bottomBar.post {
            val withBar = binding.bottomBar.isVisible
            binding.tvPromTimeFloat.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                var m = defIndent
                if (withBar) m += binding.bottomBar.measuredHeight
                if (isNewAndroid) insetsUtils?.let {
                    if (!it.isSideNavBar && it.navBar?.isVisible == true)
                        m += it.navBar?.measuredHeight ?: 0
                }
                if (bottomMargin != m) bottomMargin = m
            }
        }
    }

    private fun applyHelper() {
        helper.load(this)
        currentScale = helper.zoom / 100f
        initOptions()
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
                btnClear.isVisible = false
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
        toiler.savePosition(positionOnPage)
        prom.stop()
    }

    override fun onResume() {
        super.onResume()
        prom.resume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        helper.zoom = (currentScale * 100f).toInt()
        val s = if (isSearch)
            binding.content.etSearch.text.toString()
        else null
        toiler.setStatus(
            BrowserState.Status(
                helper = helper,
                fullscreen = isFullScreen,
                position = positionOnPage,
                search = s,
                index = searchIndex,
                head = getHeadStatus(),
                bottom = !binding.bottomBar.isScrolledDown
            )
        )
        super.onSaveInstanceState(outState)
    }

    private fun getHeadStatus(): Byte = when {
        headBar.isHided -> 0
        headBar.isExpanded -> 2
        else -> 1
    }

    override fun onDestroy() {
        if (currentScale > 0f)
            helper.zoom = (currentScale * 100f).toInt()
        helper.save(this)
        super.onDestroy()
    }

    private fun restoreSearch() {
        if (helper.placeIndex < 0) return
        if (helper.isSearch) findRequest()
        lifecycleScope.launch {
            val final = searchIndex
            searchIndex = 0
            while (searchIndex < final) {
                binding.content.wvBrowser.post {
                    binding.content.wvBrowser.findNext(true)
                }
                delay(50)
            }
        }
    }

    private fun restorePosition() = binding.content.wvBrowser.run {
        val pos = (position / 100f * currentScale * contentHeight.toFloat()).toInt()
        scrollTo(0, pos)
    }

    private fun closeSearch() {
        toast.hide()
        with(binding.content) {
            if (helper.placeIndex > -1) {
                etSearch.setText("")
                etSearch.isEnabled = true
                etSearch.updatePadding(
                    right = resources.getDimension(R.dimen.padding_for_clear).toInt()
                )
            }
            pSearch.isVisible = false
            if (isNewAndroid) {
                binding.root.post {
                    insetsUtils?.retryInsets()
                    if (ScreenUtils.type == Type.PHONE_PORT)
                        insetsUtils?.navBar?.isVisible = false
                }
            }
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
            binding.content.wvBrowser.findAllAsync(s.take(s.indexOf(Const.N)))
        else binding.content.wvBrowser.findAllAsync(s)
    }

    private fun initViews() {
        status = StatusButton(this, binding.pStatus)
        val pref = getSharedPreferences(MainHelper.TAG, MODE_PRIVATE)
        if (pref.getBoolean(Const.ALWAYS_DARK, false))
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        prom = if (pref.getBoolean(Const.PROM_FLOAT, false))
            PromUtils(binding.tvPromTimeFloat)
        else PromUtils(binding.tvPromTimeHead)

        adMenu = MenuAdapter(this::onOptionClick)
        adMenu.setItems(
            listOf(
                MenuItem(R.drawable.uncheckbox_simple, getString(R.string.nav_button)),
                MenuItem(R.drawable.uncheckbox_simple, getString(R.string.collapsing_top)),
                MenuItem(R.drawable.uncheckbox_simple, getString(R.string.auto_return_panels)),
                MenuItem(R.drawable.uncheckbox_simple, getString(R.string.num_par)),
                MenuItem(R.drawable.ic_theme, getString(R.string.dark_theme)),
                MenuItem(R.drawable.ic_opt_scale, getString(R.string.opt_scale)),
                MenuItem(R.drawable.ic_src_scale, getString(R.string.src_scale)),
            )
        )
        binding.rvMenu.let {
            it.layoutManager = GridLayoutManager(this, ScreenUtils.span)
            if (ScreenUtils.span > 1) {
                it.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    height = it.height / ScreenUtils.span
                }
            }
            it.adapter = adMenu
        }
    }

    private fun onOptionClick(index: Int, item: MenuItem) {
        when (index) {
            MENU_NAVBUTTONS -> {
                helper.isNavButton = helper.isNavButton.not()
                setCheckItem(index, helper.isNavButton)
                switchNavButton()
            }

            MENU_MINITOP -> {
                helper.isMiniTop = helper.isMiniTop.not()
                setCheckItem(index, helper.isMiniTop)
                headBar.setExpandable(helper.isMiniTop)
            }

            MENU_AUTORETURN -> {
                helper.isAutoReturn = helper.isAutoReturn.not()
                setCheckItem(index, helper.isAutoReturn)
            }

            MENU_NUMPAR -> {
                if (item.isSelect) return
                position = positionOnPage
                helper.isNumPar = helper.isNumPar.not()
                setCheckItem(index, helper.isNumPar)
                changeArguments()
                toiler.openPage(true)
            }

            MENU_THEME -> {
                position = positionOnPage
                helper.isLightTheme = helper.isLightTheme.not()
                initTheme()
                binding.content.wvBrowser.clearCache(true)
                changeArguments()
                toiler.openPage(false)
            }

            else -> { // R.drawable.ic_opt_scale, R.drawable.ic_src_scale
                helper.zoom = if (item.image == R.drawable.ic_opt_scale)
                    convertToDpi(100) else 100
                helper.save(this@BrowserActivity)
                helper.zoom = 0
                openReader(link, null)
                finish()
            }
        }
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                binding.rvMenu.isVisible -> binding.rvMenu.isVisible = false
                snackbar.isShown -> snackbar.hide()
                isSearch -> closeSearch()
                status.isVisible -> {
                    toiler.cancel()
                    status.setError(null)
                    bottomUnblocked()
                }

                isFullScreen -> switchFullScreen(false)
                binding.bottomBar.Y > 50 -> {
                    if (isSearch.not()) headBar.show()
                    bottomShow()
                }

                toiler.onBackBrowser().not() -> finish()
            }
        }
    }

    private fun setViews() = binding.run {
        btnBack.setOnClickListener {
            val m = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val act = m.appTasks[0].taskInfo.baseActivity
            if (act == null || act.shortClassName.contains(localClassName)) {
                val intent = Intent(this@BrowserActivity, MainActivity::class.java)
                startActivity(intent)
            }
            finish()
        }
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
                setNavButton(0)
            } else {
                with(content.wvBrowser) {
                    scrollTo(0, (contentHeight * currentScale).toInt())
                }
                if (isSearch.not()) {
                    headBar.hide()
                    headBar.unblocked()
                }
                bottomHide()
                setNavButton(500)
            }
        }
        status.setClick {
            if (isBlocked) toiler.cancel()
            else status.onClick()
            finishLoading()
        }
    }

    private fun initOptions() {
        binding.content.wvBrowser.setInitialScale(helper.zoom)
        if (helper.isNavButton)
            setCheckItem(MENU_NAVBUTTONS, true)
        switchNavButton()
        if (helper.isMiniTop)
            setCheckItem(MENU_MINITOP, true)
        if (helper.isAutoReturn)
            setCheckItem(MENU_AUTORETURN, true)
        if (helper.isNumPar)
            setCheckItem(MENU_NUMPAR, true)
        initTheme()
    }

    private fun initWords() {
        binding.run {
            tvGodWords.setOnClickListener {
                val context = this@BrowserActivity
                val words = WordsUtils()
                words.showAlert(context) {
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
        wvBrowser.settings.builtInZoomControls = true
        wvBrowser.settings.displayZoomControls = false
        wvBrowser.settings.javaScriptEnabled = true
        wvBrowser.settings.allowContentAccess = true
        wvBrowser.settings.allowFileAccess = true
        val act = this@BrowserActivity
        wvBrowser.addJavascriptInterface(NeoInterface(act), "NeoInterface")
        wvBrowser.webViewClient = ReaderClient(act, act.packageName)
        wvBrowser.setOnTouchListener { _, event ->
            if (snackbar.isShown) snackbar.hide()
            if (binding.rvMenu.isVisible) binding.rvMenu.isVisible = false
            if (event.pointerCount == 2) {
                if (twoPointers)
                    wvBrowser.setInitialScale((currentScale * 100f).toInt())
                twoPointers = twoPointers.not()
                return@setOnTouchListener false
            }
            if (isFullScreen || isSearch)
                return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val isScrollStart = lastScroll == 0 && wvBrowser.scrollY == 0
                    if (helper.isAutoReturn && !helper.isMiniTop && isScrollStart)
                        headBar.expanded()
                    lastScroll = wvBrowser.scrollY
                }

                MotionEvent.ACTION_MOVE -> {
                    scrollEvent()
                    lastScroll = wvBrowser.scrollY
                }
            }
            return@setOnTouchListener false
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
        btnPrev.setOnClickListener {
            if (helper.prevSearch()) {
                etSearch.setText(helper.request)
                findRequest()
            } else if (etSearch.length() > 0) {
                initSearch()
                wvBrowser.findNext(false)
            }
        }
        btnNext.setOnClickListener {
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
        btnClose.setOnClickListener { closeSearch() }
        etSearch.doAfterTextChanged {
            if (etSearch.isEnabled)
                btnClear.isVisible = it?.isNotEmpty() ?: false
        }
        btnClear.setOnClickListener { etSearch.setText("") }
    }

    private fun scrollEvent() {
        val scrollY = binding.content.wvBrowser.scrollY
        if (helper.isNavButton)
            setNavButton(scrollY)
        if (isEndScroll) bottomHide()
        else if (helper.isAutoReturn && scrollY < lastScroll) bottomShow()
        if (!headBar.isHided || helper.isAutoReturn)
            headBar.onScrollHost(scrollY, lastScroll)
    }

    private fun bottomHide() {
        binding.fabNav.hide()
        binding.bottomBar.performHide()
        binding.bottomBar.post {
            binding.bottomBar.isVisible = false
        }
        if (isNewAndroid) showNavBg()
        switchPromBottom()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showNavBg() {
        insetsUtils?.let {
            if (it.navBar == null)
                it.createBottomBar(binding.root)
            else it.navBar?.isVisible = true
        }
    }

    private fun bottomShow() {
        if (isSearch) return
        binding.fabNav.show()
        binding.fabNav.post {
            binding.fabNav.alpha = 0.5f
        }
        binding.bottomBar.isVisible = true
        binding.bottomBar.performShow()
        switchPromBottom()
        if (isNewAndroid)
            insetsUtils?.navBar?.isVisible = false
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
            adMenu.changeTitle(MENU_THEME, getString(R.string.dark_theme))
        } else {
            etSearch.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            adMenu.changeTitle(MENU_THEME, getString(R.string.light_theme))
        }
    }

    private fun initHeadBar() = binding.run {
        headBar = HeadBar(
            mainView = ivHeadBack,
            distanceForHide = if (ScreenUtils.isLand) 50 else 100,
            additionViews = listOf(tvGodWords, tvPromTimeHead, btnFullScreen)
        ) {
            when {
                link.contains(Const.DOCTRINE) -> Urls.openInBrowser(Urls.DoctrineSite)
                link.contains(Const.HOLY_RUS) -> Urls.openInBrowser(Urls.HolyRusSite)
                link.contains(Const.WORLD_AFTER_WAR) -> Urls.openInBrowser(Urls.WorldAfterWarSite)
                refreshItem.isVisible -> Urls.openInBrowser(Urls.Site + link)
                else -> Urls.openInBrowser(Urls.Site)
            }
        }
        btnFullScreen.setOnClickListener {
            switchFullScreen(true)
        }
    }

    private fun switchFullScreen(value: Boolean) {
        isFullScreen = value
        if (value) {
            headBar.hide()
            bottomBlocked()
        } else {
            headBar.show()
            bottomUnblocked()
        }
    }

    private fun setHeadBar(type: BrowserState.Type) = binding.run {
        if (ivHeadBack.isVisible) return@run
        when {
            type == BrowserState.Type.DOCTRINE -> {
                setDoctrineBack()
                if (link.isDoctrineBook)
                    ivHeadFront.setImageResource(R.drawable.head_front_db)
                else ivHeadFront.setImageResource(R.drawable.head_front_d)
            }

            type == BrowserState.Type.HOLY_RUS -> {
                setDoctrineBack()
                ivHeadFront.setImageResource(R.drawable.head_front_r)
            }

            type == BrowserState.Type.WORLD_AFTER_WAR -> {
                if (ScreenUtils.isWide && isBigHead)
                    setHeadResource(R.drawable.head_back_tablet_m)
                else if (ScreenUtils.isLand)
                    setHeadResource(R.drawable.head_back_land_m)
                else setHeadResource(R.drawable.head_back_m)
                ivHeadFront.setImageResource(R.drawable.head_front_m)
            }

            ScreenUtils.isWide && isBigHead -> setHeadResource(R.drawable.head_back_tablet)
            ScreenUtils.isLand -> setHeadResource(R.drawable.head_back_land)
        }
        if (headBar.isHided) return@run
        ivHeadBack.isVisible = true
        ivHeadFront.isVisible = true
        ivHeadBack.post {
            headBar.setExpandable(helper.isMiniTop)
        }
    }

    private fun setDoctrineBack() {
        if (ScreenUtils.isWide && isBigHead)
            setHeadResource(R.drawable.head_back_tablet_d)
        else if (ScreenUtils.isLand)
            setHeadResource(R.drawable.head_back_land_d)
        else setHeadResource(R.drawable.head_back_d)
    }

    private fun setHeadResource(resId: Int) {
        headResId = resId
        binding.ivHeadBack.setImageResource(resId)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setBottomBar() = binding.run {
        refreshItem = bottomBar.menu[4]
        bottomBar.setBackgroundResource(R.drawable.bottombar_bg)
        bottomBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.nav_refresh -> {
                    position = positionOnPage
                    toiler.refresh()
                }

                R.id.nav_share ->
                    ShareDialog.newInstance(link).show(supportFragmentManager, null)

                R.id.nav_menu ->
                    rvMenu.isVisible = !rvMenu.isVisible

                R.id.nav_search ->
                    goSearch(true)

                R.id.nav_marker -> {
                    MarkerActivity.addByPos(
                        context = this@BrowserActivity,
                        link = link,
                        pos = positionOnPage,
                        des = if (helper.isSearch)
                            getString(R.string.search_for) + " “" + helper.request + "”"
                        else ""
                    )
                }
            }
            return@setOnMenuItemClickListener true
        }
        val listener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    val x = event.getX(0).toInt()
                    if (abs(bottomX - x) > 40)
                        changePage(bottomX > x)
                }

                MotionEvent.ACTION_DOWN ->
                    bottomX = event.getX(0).toInt()
            }
            false
        }
        bottomBar.setOnTouchListener(listener)
        val m = bottomBar.getChildAt(0) as ActionMenuView
        m.children.forEach {
            it.setOnTouchListener(listener)
        }
    }

    private fun switchNavButton() = binding.run {
        fabNav.alpha = 0.5f
        if (helper.isNavButton) {
            btnFullScreen.isVisible = tvGodWords.isVisible
            btnFullScreen.tag = null
            setNavButton(content.wvBrowser.scrollY)
        } else {
            btnFullScreen.isVisible = false
            btnFullScreen.tag = "v"
            fabNav.setImageResource(R.drawable.ic_fullscreen)
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

    private fun setCheckItem(index: Int, check: Boolean) {
        val icon = if (check) R.drawable.checkbox_simple else R.drawable.uncheckbox_simple
        adMenu.changeIcon(index, icon)
    }

    private fun initSearch() {
        softKeyboard.hide()
        if (helper.isSearch) return
        helper.setSearchString(binding.content.etSearch.text.toString())
        findRequest()
    }

    override fun openLink(link: String) {
        positionForRestore = 0f
        helper.clearSearch()
        toiler.savePosition(positionOnPage)
        toiler.openLink(link, true)
    }

    override fun onBack() {
        positionForRestore = 0f
        toiler.openPage(false)
    }

    override fun onPageFinished(isLocal: Boolean) {
        if (isLocal)
            unread.deleteLink(link)
        lifecycleScope.launch {
            delay(250)
            if (positionForRestore < 0) {
                val height = binding.content.wvBrowser.contentHeight
                positionForRestore *= -1f
                positionFactor = height / positionForRestore
                position = (positionForRestore / height) * 100f
                positionForRestore = 0f
            }
            binding.content.wvBrowser.post {
                if (helper.isNavButton)
                    setNavButton(binding.content.wvBrowser.scrollY)
                if (helper.request.isNotEmpty()) restoreSearch()
                else if (position > 0f) restorePosition()

            }
        }
    }

    override fun setScale(scale: Float) {
        currentScale = scale
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

            is BrowserState.Primary ->
                setPrimary(state)

            is BrowserState.Status ->
                restoreStatus(state)

            BasicState.Ready -> {
                finishLoading()
                binding.tvNotFound.isVisible = true
                refreshItem.isVisible = false
                toiler.clearStates()
            }

            BasicState.Success -> {
                toast.show(getString(R.string.tip_end_list))
                toiler.clearStates()
            }

            BasicState.NotLoaded ->
                toast.show(getString(R.string.not_load_month))

            is BasicState.Error -> if (state.isNeedReport) {
                isBlocked = false
                status.setError(state)
            } else {
                finishLoading()
                snackbar.show(binding.fabNav, state.message)
            }
        }
    }

    private fun setPrimary(state: BrowserState.Primary) {
        toiler.clearStates()
        toast.hide()
        if (link.isNotEmpty()) {
            if (link != state.link) position = -1f
            else positionForRestore = state.position
        }
        link = state.link
        finishLoading()
        binding.content.wvBrowser.loadUrl(state.url)
        setHeadBar(state.type)
        when (state.type) {
            BrowserState.Type.DOCTRINE -> {
                adMenu.select(MENU_NUMPAR)
                refreshItem.isVisible = link.isDoctrineBook
            }

            BrowserState.Type.HOLY_RUS -> {
                adMenu.select(MENU_NUMPAR)
                refreshItem.isVisible = true
            }

            else -> {
                if (!link.isPoem) adMenu.select(MENU_NUMPAR)
                refreshItem.isVisible = state.type != BrowserState.Type.OLD_BOOK
            }
        }
        if (positionForRestore > 0f) snackbar.show(
            view = binding.fabNav,
            msg = getString(R.string.go_to_last_place),
            event = this::restoreLastPosition
        )
    }

    private fun restoreLastPosition() {
        position = positionForRestore
        if (position > 0f) restorePosition()
    }

    private fun restoreStatus(state: BrowserState.Status) {
        state.search?.let {
            binding.content.etSearch.setText(it)
            goSearch(false)
        }
        searchIndex = state.index
        isFullScreen = state.fullscreen
        if (position == 0f) position = state.position
        helper = state.helper
        applyHelper()
        restoreSearch()
        if (isFullScreen) binding.bottomBar.post {
            switchFullScreen(true)
        } else {
            bottomUnblocked()
            if (!state.bottom) bottomHide()
            when (state.head) {
                0.toByte() -> headBar.hide()
                1.toByte() -> binding.ivHeadBack.post {
                    headBar.setExpandable(true)
                    headBar.setExpandable(false)
                }
            }
        }
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
        if (!isFullScreen) bottomUnblocked()
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
        if (isNewAndroid) showNavBg()
        switchPromBottom()
    }

    private fun bottomUnblocked() {
        if (isSearch) return
        binding.fabNav.isVisible = true
        binding.bottomBar.isVisible = true
        switchPromBottom()
        if (isNewAndroid)
            insetsUtils?.navBar?.isVisible = false
    }

    override fun changePage(next: Boolean) {
        binding.content.wvBrowser.post {
            toiler.savePosition(positionOnPage)
        }
        if (next) toiler.nextPage()
        else toiler.prevPage()
    }

    override fun changeReaction(checked: Boolean) {
        binding.content.wvBrowser.let {
            it.post {
                toiler.switchReaction(if (checked) it.contentHeight else -1)
            }
        }

    }
}
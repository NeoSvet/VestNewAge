package ru.neosvet.vestnewage.view.activity

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ActionMenuView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.MenuItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.data.SiteTab
import ru.neosvet.vestnewage.helper.HomeHelper
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.service.LoaderWorker
import ru.neosvet.vestnewage.storage.DataBase
import ru.neosvet.vestnewage.storage.DevStorage
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.*
import ru.neosvet.vestnewage.view.dialog.SetNotifDialog
import ru.neosvet.vestnewage.view.fragment.*
import ru.neosvet.vestnewage.view.fragment.WelcomeFragment.ItemClicker
import ru.neosvet.vestnewage.viewmodel.MainToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.MainState
import ru.neosvet.vestnewage.viewmodel.state.NeoState
import kotlin.math.absoluteValue

class MainActivity : AppCompatActivity(), ItemClicker {
    companion object {
        private const val LIMIT_DIFF_SEC = 4
    }

    private enum class StatusBack {
        FIRST, PAGE, EXIT
    }

    private lateinit var helper: MainHelper
    private var firstSection = Section.SETTINGS
    private var curFragment: NeoFragment? = null
    private var frWelcome: WelcomeFragment? = null

    lateinit var status: StatusButton
        private set
    private var prom: PromUtils? = null
    private var firstTab = 0
    private var isBlocked = false
    private var isEditor = false
    private var statusBack = StatusBack.EXIT

    private lateinit var utils: LaunchUtils
    private var jobBottomArea: Job? = null
    private var jobFinishStar: Job? = null
    private var isShowBottomArea = true
    private var animTitle: BottomAnim? = null
    private var animButton: BottomAnim? = null
    private val snackbar = NeoSnackbar()
    private val toiler: MainToiler by lazy {
        ViewModelProvider(this)[MainToiler::class.java]
    }
    private val toast: NeoToast by lazy {
        NeoToast(helper.tvToast) {
            statusBack = StatusBack.PAGE
        }
    }
    private val toastScroll: NeoToast by lazy {
        NeoToast(helper.tvScroll, null).apply {
            timeHide = 1000L
        }
    }

    val newId: Int
        get() = helper.newId

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            doCheckPermission()
        val withSplash = intent.getBooleanExtra(Const.START_SCEEN, true)
        toiler.setArgument(withSplash)
        if (savedInstanceState == null && withSplash)
            launchSplashScreen()
        else setTheme(R.style.Theme_MainTheme)
        ScreenUtils.init(this)
        if (ScreenUtils.isTabletLand)
            setContentView(R.layout.main_activity_tablet)
        else
            setContentView(R.layout.main_activity)
        App.context = this
        helper = MainHelper(this)
        initLaunch()
        super.onCreate(savedInstanceState)
        setBottomPanel()
        initStatusButton()
        initAnim()
        initWords()
        setFloatProm(helper.isFloatPromTime)
        runObserve()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun doCheckPermission() {
        val n = POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, n) == PackageManager.PERMISSION_DENIED)
            ActivityCompat.requestPermissions(this, arrayOf(n), 1)
    }

    private fun runObserve() {
        lifecycleScope.launch {
            toiler.state.collect {
                onChangedState(it)
            }
        }
        toiler.start(this)
    }

    private fun initWords() {
        findViewById<View>(R.id.tvGodWords).setOnClickListener {
            WordsUtils.showAlert(this, this::openSearch)
        }
    }

    private fun openSearch(s: String) {
        helper.changeSection(Section.SEARCH, true)
        curFragment = SearchFragment.newInstance(s, 5)
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.my_fragment, curFragment!!)
        fragmentTransaction.commit()
    }

    private fun initStatusButton() {
        status = StatusButton(this, helper.pStatus)
        status.setClick {
            if (status.onClick()) {
                unblocked()
                curFragment?.resetError()
            } else curFragment?.onStatusClick()
        }
    }

    override fun setTitle(title: CharSequence?) {
        helper.tvTitle?.text = title
    }

    private fun setBottomPanel() = helper.bottomBar?.let { bar ->
        loadMenu()
        val menuView = bar.menu.getItem(0).actionView as ActionMenuView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            menuView.tooltipText = getString(R.string.menu)
        helper.tvNew = menuView.findViewById(R.id.tvNew)
        helper.checkNew()
        menuView.setOnClickListener {
            setSection(Section.MENU, false)
        }
        menuView.setOnLongClickListener {
            if (helper.countNew > 0) {
                setSection(Section.NEW, false)
                true
            } else false
        }
        bar.setOnMenuItemClickListener {
            if (isEditor) editMenu(it.itemId)
            else openMenu(it.itemId)
            return@setOnMenuItemClickListener true
        }
    }

    private fun editMenu(itemId: Int) = curFragment?.let {
        if (it !is HomeFragment) {
            openMenu(itemId)
            return@let
        }
        when (itemId) {
            R.id.bottom_item1 ->
                it.editMainMenu(0)

            R.id.bottom_item2 ->
                it.editMainMenu(1)

            R.id.bottom_item3 ->
                it.editMainMenu(2)

            R.id.bottom_item4 ->
                it.editMainMenu(3)
        }
    }

    private fun openMenu(itemId: Int) {
        when (itemId) {
            R.id.bottom_item1 ->
                setSection(helper.bottomMenu[0], false)

            R.id.bottom_item2 ->
                setSection(helper.bottomMenu[1], false)

            R.id.bottom_item3 ->
                setSection(helper.bottomMenu[2], false)

            R.id.bottom_item4 ->
                setSection(helper.bottomMenu[3], false)
        }
    }

    private fun launchSplashScreen() {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.transparent)
            )
            val anStar = AnimationUtils.loadAnimation(this, R.anim.flash)
            anStar.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    provider.remove()
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
            provider.view.startAnimation(anStar)
        }
        jobFinishStar = lifecycleScope.launch {
            delay(1600)
            helper.fabAction.post {
                finishFlashStar()
            }
        }
    }

    private fun initLaunch() {
        Urls.restore()
        utils = LaunchUtils(this)
        utils.checkAdapterNewVersion()
        utils.openLink(intent)?.let {
            if (it.tab == -1) exit()
            firstTab = it.tab
            firstSection = it.section
            return
        }
        firstSection = helper.getFirstSection()
        if (utils.isNeedLoad) {
            NeoClient.deleteTempFiles()
            toiler.load()
        }
    }

    fun setFragment(fragment: NeoFragment) {
        title = fragment.title
        curFragment = fragment
        helper.svMain?.post {
            fragment.updateRoot(helper.svMain!!.height)
        }
    }

    private fun finishFlashStar() {
        showHead()
        unblocked()
        helper.setNewValue()
        if (helper.isFirstRun) {
            setSection(Section.HELP, false)
            statusBack = StatusBack.FIRST
            return
        }
        if (helper.startWithNew()) {
            setSection(Section.NEW, false)
            helper.prevSection = firstSection
        } else
            setSection(firstSection, false)
        showWelcome()
    }

    private fun showHead() {
        if (helper.topBar == null) {
            findViewById<View>(R.id.ivHeadBack).isVisible = true
            findViewById<View>(R.id.ivHeadFront).isVisible = true
            findViewById<View>(R.id.tvGodWords).isVisible = true
            findViewById<View>(R.id.tvPromTimeHead).isVisible = true
        } else helper.topBar!!.isVisible = true
    }

    private fun initAnim() = helper.run {
        fabAction.post {
            tvTitle?.let { animTitle = BottomAnim(it) }
            animButton = BottomAnim(fabAction)
        }
    }

    override fun onDestroy() {
        toast.destroy()
        super.onDestroy()
    }

    override fun onPause() {
        prom?.stop()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (curFragment == null && jobFinishStar?.isCancelled == true)
            finishFlashStar()
        prom?.resume()
    }

    fun updateNew() {
        helper.updateNew()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(Const.LIST, helper.isSideMenu)
        outState.putString(Const.MODE, helper.curSection.toString())
        jobFinishStar?.cancel()
        toiler.setStatus(
            MainState.Status(
                curSection = helper.curSection.toString(),
                isBlocked = isBlocked,
                isEditor = isEditor,
                shownDwnDialog = helper.shownDwnDialog,
                actionIcon = helper.actionIcon,
            )
        )
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) { //for support DeX / resize window
        val isSizeMenu = savedInstanceState.getBoolean(Const.LIST)
        val sec = savedInstanceState.getString(Const.MODE)?.let {
            Section.valueOf(it)
        } ?: Section.HOME
        if (helper.isSideMenu && !isSizeMenu) {
            showHead()
            setSection(sec, false)
            updateNew()
        } else if (!ScreenUtils.isTablet && isSizeMenu) {
            showHead()
            setSection(sec, false)
            helper.bottomBar?.isVisible = true
        }
        if (sec == Section.MENU) title = getString(R.string.app_name)
        super.onRestoreInstanceState(savedInstanceState)
    }

    fun setFrMenu(frMenu: MenuFragment) {
        helper.frMenu = frMenu
        frMenu.setNew(newId)
    }

    @SuppressLint("NonConstantResourceId")
    fun setSection(section: Section, savePrev: Boolean, tab: Int = firstTab) {
        if (section == helper.curSection) return
        helper.vsbScrollBar.isVisible = false
        toast.hide()
        statusBack = StatusBack.PAGE
        setMenu(section, savePrev)
        status.setError(null)
        if (isBlocked) unblocked()
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        curFragment = null
        helper.checkNew()
        helper.fabAction.isVisible = true
        when (section) {
            Section.MENU -> {
                unblocked()
                helper.frMenu = MenuFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
                title = getString(R.string.app_name)
            }

            Section.HOME -> {
                unblocked()
                fragmentTransaction.replace(R.id.my_fragment, HomeFragment())
                helper.frMenu?.setSelect(Section.HOME)
            }

            Section.NEW -> {
                fragmentTransaction.replace(R.id.my_fragment, NewFragment())
                helper.frMenu?.setSelect(Section.NEW)
            }

            Section.SUMMARY -> {
                curFragment = SummaryFragment.newInstance(tab).also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
                val id = intent.getIntExtra(DataBase.ID, NotificationUtils.NOTIF_SUMMARY)
                intent.removeExtra(DataBase.ID)
                utils.clearSummaryNotif(id)
            }

            Section.SITE -> {
                curFragment = SiteFragment.newInstance(tab).also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }

            Section.CALENDAR -> {
                curFragment = CalendarFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }

            Section.BOOK -> {
                curFragment = (if (tab > 2000) BookFragment.newInstance(0, tab)
                else BookFragment.newInstance(tab, -1)).also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }

            Section.SEARCH -> {
                val search = when {
                    intent.hasExtra(Const.LINK) -> {
                        SearchFragment.newInstance(
                            intent.getStringExtra(Const.LINK), tab
                        )
                    }

                    else -> SearchFragment()
                }
                curFragment = search
                fragmentTransaction.replace(R.id.my_fragment, search)
            }

            Section.JOURNAL -> {
                fragmentTransaction.replace(R.id.my_fragment, JournalFragment())
            }

            Section.MARKERS -> {
                curFragment = MarkersFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }

            Section.CABINET -> {
                curFragment = CabinetFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }

            Section.SETTINGS -> {
                curFragment = SettingsFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }

            Section.HELP -> {
                if (helper.isSideMenu)
                    helper.fabAction.isVisible = false
                if (tab == -1) { //first isRun
                    val frHelp = HelpFragment.newInstance(0)
                    fragmentTransaction.replace(R.id.my_fragment, frHelp)
                } else
                    fragmentTransaction.replace(R.id.my_fragment, HelpFragment())
            }
        }
        firstTab = 0
        if (supportFragmentManager.isDestroyed.not())
            fragmentTransaction.commit()
    }

    private fun setMenu(section: Section, savePrev: Boolean) {
        helper.changeSection(section, savePrev)
        if (helper.isSideMenu) helper.setMenuFragment()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (curFragment == null || resultCode != RESULT_OK) return
        if (requestCode == SetNotifDialog.RINGTONE.toInt() && curFragment is SettingsFragment) {
            val fr = curFragment as SettingsFragment
            fr.putRingtone(data)
        }
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                snackbar.isShown ->
                    snackbar.hide()

                status.isCrash -> {
                    status.setError(null)
                    unblocked()
                    curFragment?.resetError()
                }

                helper.shownActionMenu ->
                    helper.hideActionMenu()

                curFragment?.onBackPressed() == false ->
                    return

                helper.bottomAreaIsHide ->
                    showBottomArea()

                firstSection == Section.NEW -> {
                    firstSection = helper.getFirstSection()
                    setSection(firstSection, false)
                }

                else -> exit()
            }
        }
    }

    private fun exit() {
        when {
            statusBack == StatusBack.EXIT ->
                finish()

            helper.prevSection != null -> helper.prevSection?.let {
                if (it == Section.SITE)
                    setSection(it, false, SiteTab.SITE.value)
                else setSection(it, false)
                helper.prevSection = null
            }

            statusBack == StatusBack.FIRST ->
                setSection(firstSection, false)

            else -> {
                showToast(getString(R.string.click_for_exit))
                statusBack = StatusBack.EXIT
                lifecycleScope.launch {
                    delay(3000)
                    if (statusBack == StatusBack.EXIT)
                        statusBack = StatusBack.PAGE
                }
            }
        }
    }

    fun openBook(link: String, isPoems: Boolean) {
        firstTab = if (isPoems) 0 else 1
        val year = helper.getYear(link)
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        curFragment = BookFragment.newInstance(firstTab, year).also {
            fragmentTransaction.replace(R.id.my_fragment, it)
            fragmentTransaction.commit()
        }
        setMenu(Section.BOOK, true)
    }

    private fun showWelcome() {
        frWelcome?.show(supportFragmentManager, null)
    }

    private fun showWarnAds(warn: Int) {
        val storage = DevStorage()
        val ads = AdsUtils(storage, this)
        ads.getItem(warn)?.let { item ->
            val builder = AlertDialog.Builder(this, R.style.NeoDialog)
                .setTitle(getString(R.string.warning) + " " + item.title)
                .setMessage(item.des)
                .setNegativeButton(getString(android.R.string.ok)) { dialog: DialogInterface, _ ->
                    dialog.dismiss()
                }
                .setOnDismissListener { showWelcome() }
            if (item.link.isNotEmpty()) {
                builder.setPositiveButton(getString(R.string.open_link)) { _, _ ->
                    Urls.openInApps(item.link)
                }
            }
            builder.create().show()
        }
        storage.close()
    }

    override fun onItemClick(link: String) {
        if (link.isEmpty()) return
        if (link == Const.ADS) {
            firstTab = SiteTab.DEV.value
            setSection(Section.SITE, true)
        } else openReader(link, null)
    }

    private fun onChangedState(state: NeoState) {
        when (state) {
            is MainState.Ads -> {
                utils.updateTime()
                if (state.timediff.absoluteValue > LIMIT_DIFF_SEC || state.hasNew)
                    frWelcome = WelcomeFragment.newInstance(state.hasNew, state.timediff)
                if (state.warnIndex > -1)
                    showWarnAds(state.warnIndex)
            }

            is MainState.Status ->
                restoreStatus(state)

            is MainState.FirstRun ->
                firstRun(state)

            is ListState.Primary -> {
                if (frWelcome == null)
                    frWelcome = WelcomeFragment.newInstance(false, 0)
                frWelcome?.list?.addAll(state.list)
            }
        }
    }

    private fun firstRun(state: MainState.FirstRun) {
        val intent = intent
        if (firstTab == 0)
            firstTab = intent.getIntExtra(Const.TAB, 0)
        if (helper.isFirstRun)
            firstTab = -1
        else {
            intent.getStringExtra(Const.CUR_ID)
            if (firstSection == Section.MENU)
                updateNew()
        }
        if (intent.getBooleanExtra(Const.DIALOG, false))
            helper.showDownloadDialog()
        if (state.withSplash.not()) {
            finishFlashStar()
            intent.getStringExtra(Const.SEARCH)?.let {
                openSearch(it)
                statusBack = StatusBack.EXIT
            }
        }
    }

    private fun restoreStatus(state: MainState.Status) = helper.run {
        showHead()
        setActionIcon(state.actionIcon)
        if (state.isBlocked) blocked()
        else unblocked()
        curSection = Section.valueOf(state.curSection)

        if (supportFragmentManager.fragments.isEmpty() ||
            (isSideMenu && curSection == Section.MENU)
        ) setSection(firstSection, false)
        if (isSideMenu) setMenuFragment()
        else if (curSection != Section.MENU)
            statusBack = StatusBack.PAGE
        updateNew()
        if (curSection == Section.HELP && isSideMenu)
            fabAction.isVisible = false
        if (state.shownDwnDialog)
            showDownloadDialog()
        if (state.isEditor)
            startEditMenu()
    }

    fun onAction(title: String) {
        curFragment?.onAction(title)
    }

    fun hideBottomArea() {
        if (isBlocked || isShowBottomArea.not()) return
        jobBottomArea?.cancel()
        helper.run {
            bottomBar?.performHide()
            isShowBottomArea = false
            animTitle?.hide()
            animButton?.hide()
        }
        jobBottomArea = lifecycleScope.launch {
            delay(1500)
            showBottomArea()
        }
    }

    fun showBottomArea() {
        if (isBlocked) return
        jobBottomArea?.cancel()
        if (status.isVisible || isShowBottomArea) return
        isShowBottomArea = true
        helper.run {
            bottomBar?.performShow()
            animTitle?.show()
            animButton?.show()
        }
    }

    fun blocked() = helper.run {
        isBlocked = true
        tipAction.hide()
        tvTitle?.isVisible = false
        fabAction.isVisible = false
        bottomBar?.isVisible = false
    }

    fun unblocked() = helper.run {
        if (status.isVisible) return@run
        isBlocked = false
        tvTitle?.isVisible = true
        fabAction.isVisible = true
        bottomBar?.run {
            isVisible = true
            performShow()
        }
    }

    fun setAction(icon: Int) {
        helper.setActionIcon(icon)
        if (isBlocked)
            helper.fabAction.isVisible = false
    }

    fun setFloatProm(isFloat: Boolean) {
        prom?.hide()
        prom = if (isFloat)
            PromUtils(helper.tvPromTimeFloat)
        else
            PromUtils(findViewById(R.id.tvPromTimeHead))
        prom?.show()
    }

    fun lockHead() {
        helper.topBar?.let {
            it.setExpanded(false)
            it.isVisible = false
        }
        helper.svMain?.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = null
        }
    }

    fun unlockHead() {
        helper.svMain?.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }
        helper.topBar?.isVisible = true
    }

    fun setError(error: BasicState.Error) {
        if (error.isNeedReport) {
            blocked()
            status.setError(error)
        } else
            snackbar.show(helper.fabAction, error.message)
    }

    fun showStaticToast(msg: String) {
        if (snackbar.isShown) return
        toast.autoHide = false
        toast.show(msg)
    }

    fun showToast(msg: String) {
        if (snackbar.isShown) return
        toast.autoHide = true
        toast.show(msg)
    }

    fun hideToast() {
        toast.hideAnimated()
    }

    fun download(list: List<Int>) {
        toast.autoHide = true
        if (LoaderWorker.isRun) {
            toast.show(App.context.getString(R.string.load_already_run))
            return
        }
        toast.show(App.context.getString(R.string.load_background))
        LoaderWorker.load(list)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun initScrollBar(max: Int, host: NeoScrollBar.Host?) {
        helper.vsbScrollBar.isVisible = host?.let { event ->
            helper.vsbScrollBar.init(max, lifecycleScope, event)
            true
        } ?: false
    }

    fun showScrollTip(msg: String) {
        if (msg.isEmpty()) toastScroll.hide()
        else toastScroll.show(msg)
    }

    fun setScrollBar(value: Int) {
        if (value == -1)
            helper.vsbScrollBar.value = helper.vsbScrollBar.maxValue
        else helper.vsbScrollBar.value = value
    }

    fun startEditMenu() {
        isEditor = true
    }

    fun finishEditMenu() {
        isEditor = false
        loadMenu()
    }

    fun changeMenu(index: Int, item: MenuItem) = helper.bottomBar?.let { bar ->
        val bottomItem = bar.menu.getItem(index + 1)
        bottomItem.title = item.title
        bottomItem.icon = ContextCompat.getDrawable(this, item.image)
    }

    private fun loadMenu() = helper.bottomBar?.let { bar ->
        val home = HomeHelper(this).apply { isMain = true }
        var i = 1
        helper.setBottomMenu(home.loadMenu(true))
        helper.bottomMenu.forEach {
            val item = bar.menu.getItem(i)
            val m = if (it == Section.HOME)
                MenuItem(R.drawable.ic_home, getString(R.string.home_screen))
            else home.getItem(it)
            item.title = m.title
            item.icon = ContextCompat.getDrawable(this, m.image)
            i++
        }
    }
}
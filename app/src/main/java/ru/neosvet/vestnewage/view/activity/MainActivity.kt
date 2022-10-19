package ru.neosvet.vestnewage.view.activity

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ActionMenuView
import androidx.coordinatorlayout.widget.CoordinatorLayout
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
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.storage.AdsStorage
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.*
import ru.neosvet.vestnewage.view.dialog.SetNotifDialog
import ru.neosvet.vestnewage.view.fragment.*
import ru.neosvet.vestnewage.view.fragment.WelcomeFragment.ItemClicker
import ru.neosvet.vestnewage.viewmodel.MainToiler
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import kotlin.math.absoluteValue


class MainActivity : AppCompatActivity(), ItemClicker {
    companion object {
        private const val LIMIT_DIFF_SEC = 4
    }

    private enum class StatusBack {
        FIRST, PAGE, EXIT
    }

    private lateinit var helper: MainHelper
    private var firstSection = Section.CALENDAR
    private var curFragment: NeoFragment? = null
    private var frWelcome: WelcomeFragment? = null

    lateinit var status: StatusButton
        private set
    private var prom: PromUtils? = null
    private var tab = 0
    private var isBlocked = false
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

    val newId: Int
        get() = helper.newId

    override fun onCreate(savedInstanceState: Bundle?) {
        val withSplash = intent.getBooleanExtra(Const.START_SCEEN, true)
        if (savedInstanceState == null && withSplash)
            launchSplashScreen()
        else
            setTheme(R.style.Theme_MainTheme)
        ScreenUtils.init(this)
        if (ScreenUtils.isTabletLand)
            setContentView(R.layout.main_activity_tablet)
        else
            setContentView(R.layout.main_activity)
        App.context = this
        initLaunch()
        helper = MainHelper(this)
        helper.initViews()
        super.onCreate(savedInstanceState)
        setBottomPanel()
        initStatusButton()
        initAnim()
        initWords()
        setFloatProm(helper.isFloatPromTime)

        restoreState(savedInstanceState)
        if (savedInstanceState == null && withSplash.not()) {
            finishFlashStar()
            intent.getStringExtra(Const.SEARCH)?.let {
                openSearch(it)
                statusBack = StatusBack.EXIT
            }
        }
        if (toiler.isRun) runObservation()
    }

    private fun runObservation() {
        lifecycleScope.launch {
            toiler.state.collect {
                onChangedState(it)
            }
        }
    }

    private fun initWords() {
        val funClick = { v: View ->
            WordsUtils.showAlert(this, this::openSearch)
        }
        findViewById<View>(R.id.btnGodWords).setOnClickListener(funClick)
        findViewById<View>(R.id.tvGodWords).setOnClickListener(funClick)
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
        val isSummary = helper.getFirstSection() == Section.SUMMARY
        if (isSummary) {
            val item = bar.menu.getItem(1)
            item.title = getString(R.string.summary)
            item.icon = ContextCompat.getDrawable(this, R.drawable.ic_summary)
        }
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
            when (it.itemId) {
//                R.id.app_bar_menu ->
//                    setSection(Section.MENU, false)
                R.id.app_bar_start -> if (isSummary)
                    setSection(Section.SUMMARY, false)
                else
                    setSection(Section.CALENDAR, false)
                R.id.app_bar_book ->
                    setSection(Section.BOOK, false)
                R.id.app_bar_marker ->
                    setSection(Section.MARKERS, false)
                R.id.app_bar_journal ->
                    setSection(Section.JOURNAL, false)
                R.id.app_bar_search ->
                    setSection(Section.SEARCH, false)
                R.id.app_bar_cabinet ->
                    setSection(Section.CABINET, false)
                R.id.app_bar_prom ->
                    openReader(Const.PROM_LINK, null)
            }
            return@setOnMenuItemClickListener true
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
        utils = LaunchUtils()
        utils.checkAdapterNewVersion()
        utils.openLink(intent)?.let {
            if (it.tab == -1)
                exit()
            tab = it.tab
            firstSection = it.section
            return
        }
        if (utils.isNeedLoad) {
            NeoClient.deleteTempFiles()
            runObservation()
            toiler.load()
        }
    }

    fun setFragment(fragment: NeoFragment) {
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
        if (firstSection == Section.CALENDAR)
            firstSection = helper.getFirstSection()
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
            findViewById<View>(R.id.btnGodWords).isVisible = true
            findViewById<View>(R.id.tvPromTimeHead).isVisible = true
        } else helper.topBar!!.isVisible = true
    }

    private fun initAnim() = helper.run {
        fabAction.post {
            tvTitle?.let { animTitle = BottomAnim(it) }
            animButton = BottomAnim(fabAction)
        }
    }

    private fun restoreState(state: Bundle?) {
        if (state == null) {
            val intent = intent
            tab = intent.getIntExtra(Const.TAB, tab)
            if (helper.isFirstRun)
                tab = -1
            else {
                intent.getStringExtra(Const.CUR_ID)
                if (firstSection == Section.MENU)
                    updateNew()
            }
            if (intent.getBooleanExtra(Const.DIALOG, false))
                helper.showDownloadDialog()
        } else helper.run {
            showHead()
            setActionIcon(state.getInt(Const.SELECT))
            if (state.getBoolean(Const.PANEL))
                blocked()
            else
                unblocked()
            state.getString(Const.CUR_ID)?.let {
                curSection = Section.valueOf(it)
            }
            if (supportFragmentManager.fragments.isEmpty() ||
                (helper.isSideMenu && curSection == Section.MENU)
            ) setSection(firstSection, false)
            if (isSideMenu) setMenuFragment()
            else if (curSection != Section.MENU)
                statusBack = StatusBack.PAGE
            updateNew()
            if (curSection == Section.HELP && helper.isSideMenu)
                helper.fabAction.isVisible = false
            if (state.getBoolean(Const.DIALOG))
                helper.showDownloadDialog()
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
        prom?.resume()
    }

    fun updateNew() {
        helper.updateNew()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        jobFinishStar?.cancel()
        outState.putString(Const.CUR_ID, helper.curSection.toString())
        outState.putBoolean(Const.PANEL, isBlocked)
        outState.putBoolean(Const.DIALOG, helper.shownDwnDialog)
        outState.putInt(Const.SELECT, helper.actionIcon)
        super.onSaveInstanceState(outState)
    }

    fun setFrMenu(frMenu: MenuFragment) {
        helper.frMenu = frMenu
        frMenu.setNew(newId)
    }

    @SuppressLint("NonConstantResourceId")
    fun setSection(section: Section, savePrev: Boolean) {
        if (section == helper.curSection) return
        helper.vsbScrollBar.isVisible = false
        toast.hide()
        statusBack = StatusBack.PAGE
        setMenu(section, savePrev)
        status.setError(null)
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
                curFragment = BookFragment.newInstance(tab, -1).also {
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
        tab = 0
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

    override fun onBackPressed() {
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

    private fun exit() {
        when {
            statusBack == StatusBack.EXIT ->
                super.onBackPressed()
            helper.prevSection != null -> {
                if (helper.prevSection == Section.SITE)
                    tab = SiteToiler.TAB_SITE
                setSection(helper.prevSection!!, false)
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
        tab = if (isPoems) 0 else 1
        val year = helper.getYear(link)
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        curFragment = BookFragment.newInstance(tab, year).also {
            fragmentTransaction.replace(R.id.my_fragment, it)
            fragmentTransaction.commit()
        }
        setMenu(Section.BOOK, true)
    }

    private fun showWelcome() {
        frWelcome?.show(supportFragmentManager, null)
    }

    private fun showWarnAds(warn: Int) {
        val storage = AdsStorage()
        val ads = AdsUtils(storage)
        val item = ads.getItem(warn)
        storage.close()
        if (item[AdsUtils.TITLE].isEmpty()) return
        val builder = AlertDialog.Builder(this, R.style.NeoDialog)
            .setTitle(getString(R.string.warning) + " " + item[AdsUtils.TITLE])
            .setMessage(item[AdsUtils.DES])
            .setNegativeButton(getString(android.R.string.ok)) { dialog: DialogInterface, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener { showWelcome() }
        if (item[AdsUtils.LINK].isNotEmpty()) {
            builder.setPositiveButton(getString(R.string.open_link)) { _, _ ->
                Lib.openInApps(item[AdsUtils.LINK], null)
            }
        }
        builder.create().show()
    }

    override fun onItemClick(link: String) {
        if (link.isEmpty()) return
        if (link == Const.ADS) {
            tab = SiteToiler.TAB_DEV
            setSection(Section.SITE, true)
        } else openReader(link, null)
    }

    private fun onChangedState(state: NeoState) {
        when (state) {
            is NeoState.Ads -> {
                utils.updateTime()
                utils.reInitProm(state.timediff)
                if (state.timediff.absoluteValue > LIMIT_DIFF_SEC || state.hasNew)
                    frWelcome = WelcomeFragment.newInstance(state.hasNew, state.timediff)
                if (state.warnIndex > -1)
                    showWarnAds(state.warnIndex)
            }
            is NeoState.ListValue -> {
                if (frWelcome == null)
                    frWelcome = WelcomeFragment.newInstance(false, 0)
                frWelcome?.list?.addAll(state.list)
            }
            else -> {}
        }
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
        helper.topBar?.let {
            it.setExpanded(true)
            it.isVisible = true
        }
    }

    fun setError(error: NeoState.Error) {
        if (error.isNeedReport) {
            blocked()
            status.setError(error)
        } else
            snackbar.show(helper.fabAction, error.message)
    }

    fun showStaticToast(msg: String) {
        toast.autoHide = false
        toast.show(msg)
    }

    fun showToast(msg: String) {
        toast.autoHide = true
        toast.show(msg)
    }

    fun hideToast() {
        toast.hideAnimated()
    }

    fun download(list: List<Int>) {
        LoaderService.loadList(list, toast)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun initScrollBar(max: Int, onChange: ((Int) -> Unit)?) {
        helper.vsbScrollBar.isVisible = onChange?.let { event ->
            helper.vsbScrollBar.init(max, lifecycleScope, event)
            true
        } ?: false
    }

    fun setScrollBar(value: Int) {
        helper.vsbScrollBar.value = value
    }
}
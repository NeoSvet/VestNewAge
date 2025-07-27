package ru.neosvet.vestnewage.view.activity

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Insets
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.ActionMenuView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomappbar.BottomAppBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BookTab
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
import ru.neosvet.vestnewage.utils.AdsUtils
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.ErrorUtils
import ru.neosvet.vestnewage.utils.InsetsUtils
import ru.neosvet.vestnewage.utils.LaunchUtils
import ru.neosvet.vestnewage.utils.NotificationUtils
import ru.neosvet.vestnewage.utils.PromUtils
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.utils.WordsUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.NeoScrollBar
import ru.neosvet.vestnewage.view.basic.NeoSnackbar
import ru.neosvet.vestnewage.view.basic.NeoToast
import ru.neosvet.vestnewage.view.basic.StatusButton
import ru.neosvet.vestnewage.view.basic.defIndent
import ru.neosvet.vestnewage.view.dialog.SetNotifDialog
import ru.neosvet.vestnewage.view.fragment.BookFragment
import ru.neosvet.vestnewage.view.fragment.CalendarFragment
import ru.neosvet.vestnewage.view.fragment.HelpFragment
import ru.neosvet.vestnewage.view.fragment.HomeFragment
import ru.neosvet.vestnewage.view.fragment.JournalFragment
import ru.neosvet.vestnewage.view.fragment.MarkersFragment
import ru.neosvet.vestnewage.view.fragment.MenuFragment
import ru.neosvet.vestnewage.view.fragment.NewFragment
import ru.neosvet.vestnewage.view.fragment.SearchFragment
import ru.neosvet.vestnewage.view.fragment.SettingsFragment
import ru.neosvet.vestnewage.view.fragment.SiteFragment
import ru.neosvet.vestnewage.view.fragment.SummaryFragment
import ru.neosvet.vestnewage.view.fragment.WelcomeFragment
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
    private var firstSection = Section.EPISTLES
    private var curFragment: NeoFragment? = null
    private var frWelcome: WelcomeFragment? = null

    lateinit var status: StatusButton
        private set
    private var prom: PromUtils? = null
    private var firstTab = 0
    private var isBlocked = false
    private var isEditor = false
    private var statusBack = StatusBack.EXIT

    private lateinit var launchUtils: LaunchUtils
    private var star: SplashScreenViewProvider? = null
    private lateinit var tvGodWords: TextView
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
    private var insetsUtils: InsetsUtils? = null
    private val isNewAndroid: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val newId: Int
        get() = helper.newId

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) doCheckPermission()
        val withSplash = intent.getBooleanExtra(Const.START_SCEEN, true)
        toiler.setArgument(withSplash)
        if (savedInstanceState == null && withSplash) launchSplashScreen()
        else setTheme(R.style.Theme_MainTheme)
        ScreenUtils.init(this)
        if (ScreenUtils.isTabletLand) setContentView(R.layout.main_activity_tablet)
        else setContentView(R.layout.main_activity)
        App.context = this
        helper = MainHelper(this)
        initLaunch()
        super.onCreate(savedInstanceState)
        setBottomPanel()
        initStatusButton()
        initWords()
        setFloatProm(helper.isFloatPromTime)
        if (isNewAndroid) initInsetsUtils()
        else setIndent()
        if (helper.isAlwaysDark) setDarkTheme(true)
        runObserve()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun setIndent() = helper.bottomBar?.let { bar ->
        lifecycleScope.launch {
            while (bar.measuredHeight == 0) delay(50)
            bar.post {
                App.CONTENT_BOTTOM_INDENT = bar.measuredHeight
                helper.tvTitle?.let {
                    it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = App.CONTENT_BOTTOM_INDENT
                    }
                    App.CONTENT_BOTTOM_INDENT += it.measuredHeight
                }
                helper.tvPromTimeFloat.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = App.CONTENT_BOTTOM_INDENT
                }
                helper.tvToast.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = App.CONTENT_BOTTOM_INDENT
                }
            }
        }
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
        tvGodWords = findViewById(R.id.tvGodWords)
        tvGodWords.setOnClickListener {
            val words = WordsUtils()
            words.showAlert(this, this::openSearch)
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
        loadMenu(bar)
        val menuView = bar.menu[0].actionView as ActionMenuView
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
            R.id.bottom_item1 -> it.editMainMenu(0)
            R.id.bottom_item2 -> it.editMainMenu(1)
            R.id.bottom_item3 -> it.editMainMenu(2)
            R.id.bottom_item4 -> it.editMainMenu(3)
        }
    }

    private fun openMenu(itemId: Int) {
        when (itemId) {
            R.id.bottom_item1 -> setSection(helper.bottomMenu[0], false)
            R.id.bottom_item2 -> setSection(helper.bottomMenu[1], false)
            R.id.bottom_item3 -> setSection(helper.bottomMenu[2], false)
            R.id.bottom_item4 -> setSection(helper.bottomMenu[3], false)
        }
    }

    private fun launchSplashScreen() {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { provider ->
            star = provider
            provider.view.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.transparent)
            )
            val anStar = AnimationUtils.loadAnimation(this, R.anim.flash)
            anStar.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    finishFlashStar()
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
            provider.view.startAnimation(anStar)
        }
    }

    private fun initLaunch() {
        Urls.restore()
        launchUtils = LaunchUtils(this)
        launchUtils.checkAdapterNewVersion()
        try {
            launchUtils.openLink(intent)?.let {
                if (it.tab == -1) exit()
                firstTab = it.tab
                firstSection = it.section
                return
            }
        } catch (e: Exception) {
            val utils = ErrorUtils(e)
            val d = Data.Builder().putString(Const.TASK, "LaunchUtils")
                .putString("Data", intent.data.toString())
                .putString("Path", intent.data?.path.toString())
            toiler.setPublicState(utils.getErrorState(d.build()))
        }
        firstSection = helper.getFirstSection()
        if (launchUtils.isNeedLoad) {
            NeoClient.deleteTempFiles()
            toiler.load()
        }
    }

    fun setFragment(fragment: NeoFragment) {
        title = fragment.title
        curFragment = fragment
        helper.svMain?.let {
            it.post { fragment.updateRoot(it.height) }
        }
    }

    private fun finishFlashStar() {
        star?.remove()
        star = null
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
        } else setSection(firstSection, false)
        showWelcome()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initInsetsUtils() {
        insetsUtils = InsetsUtils(helper.ivHeadBack, this).apply {
            applyInsets = { insets ->
                if (helper.topBar?.isVisible ?: helper.ivHeadBack.isVisible) {
                    setVerticalInsets(insets)
                    if (isSideNavBar)
                        setSideInsets(insets)
                    if (ScreenUtils.isTabletLand) {
                        val root = findViewById<ViewGroup>(R.id.main)
                        createBottomBar(root)
                    }
                    true
                } else false
            }
            init(window)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setVerticalInsets(insets: Insets) {
        if (helper.topBar != null) {
            helper.tvPromTimeHead.updateLayoutParams<CollapsingToolbarLayout.LayoutParams> {
                gravity = Gravity.BOTTOM
            }
            tvGodWords.updateLayoutParams<CollapsingToolbarLayout.LayoutParams> {
                gravity = Gravity.BOTTOM + Gravity.END
            }
            val collapsingBar: CollapsingToolbarLayout = findViewById(R.id.collapsingBar)
            collapsingBar.scrimVisibleHeightTrigger = insets.top + 10
            collapsingBar.minimumHeight = insets.top
        } else {
            val top = findViewById<ImageView>(R.id.ivTop)
            top.isVisible = true
            top.maxHeight = insets.top
            top.minimumHeight = insets.top
            helper.vsbScrollBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin += insets.top
                bottomMargin += insets.bottom
            }
            helper.tvPromTimeFloat.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin += insets.bottom
            }
        }
        helper.svMain?.let {
            it.updatePadding(left = insets.left, right = insets.right)
            it.post { curFragment?.updateRoot(it.height) }
        }

        App.CONTENT_BOTTOM_INDENT = insets.bottom
        helper.bottomBar?.let { bar ->
            App.CONTENT_BOTTOM_INDENT += bar.measuredHeight
            if (insets.bottom > 0) {
                App.CONTENT_BOTTOM_INDENT -= defIndent
                bar.updatePadding(bottom = insets.bottom - defIndent)
                helper.rvAction.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin += insets.bottom
                }
                bar.children.first()
                    .addOnLayoutChangeListener { v, _, top, _, _, _, _, _, _ ->
                        if (top > 0) v.top = 0
                    }
                bar.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    if (v.minimumHeight < v.measuredHeight)
                        v.minimumHeight = v.measuredHeight
                }
            }
            helper.tvTitle?.let {
                it.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin += insets.left
                    bottomMargin = App.CONTENT_BOTTOM_INDENT
                }
                App.CONTENT_BOTTOM_INDENT += it.measuredHeight
            }
            curFragment?.onChangedInsets(insets)
            bar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
        }
        if (helper.bottomBar == null) {
            helper.fabAction.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = App.CONTENT_BOTTOM_INDENT + defIndent
            }
            helper.rvAction.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin += insets.bottom
            }
        } else {
            helper.vsbScrollBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = App.CONTENT_BOTTOM_INDENT + defIndent
            }
            helper.tvPromTimeFloat.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = App.CONTENT_BOTTOM_INDENT
            }
        }
        helper.tvToast.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = App.CONTENT_BOTTOM_INDENT + defIndent
            leftMargin = insets.left
            rightMargin = insets.right
        }
        helper.pStatus.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin += insets.bottom
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setSideInsets(insets: Insets) {
        helper.tvToast.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = insets.left
            rightMargin = insets.right
        }
        if (insets.right > 0) {
            helper.vsbScrollBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin = insets.right + defIndent
            }
            tvGodWords.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin = insets.right
            }
            helper.pStatus.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin = insets.right + defIndent
            }
            helper.rvAction.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin += insets.right
            }
        }
        if (insets.left > 0) {
            helper.tvPromTimeHead.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
            }
            helper.tvPromTimeFloat.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin += insets.left
            }
            val ivHeadFront: ImageView = findViewById(R.id.ivHeadFront)
            ivHeadFront.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
            }
        }
        helper.ivHeadBack.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = insets.left
            rightMargin = insets.right
        }
    }

    private fun showHead() {
        if (helper.topBar == null) {
            helper.ivHeadBack.isVisible = true
            findViewById<View>(R.id.ivHeadFront).isVisible = true
            findViewById<View>(R.id.tvGodWords).isVisible = true
            helper.tvPromTimeHead.isVisible = true
        } else helper.topBar!!.isVisible = true
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
        outState.putString(Const.MODE, helper.curSection.toString())
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

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        var sec = savedInstanceState.getString(Const.MODE)?.let {
            Section.valueOf(it)
        } ?: Section.HOME
        showHead()
        if (sec == Section.MENU) {
            if (ScreenUtils.isTabletLand) sec = Section.HOME
            else title = getString(R.string.app_name)
        }
        setMenu(sec, false)
        helper.bottomBar?.isVisible = true
        helper.fabAction.isVisible = true
        updateNew()
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
                helper.frMenu = MenuFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
                title = getString(R.string.app_name)
            }

            Section.HOME -> {
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
                launchUtils.clearSummaryNotif(id)
            }

            Section.SITE -> curFragment = SiteFragment.newInstance(tab).also {
                fragmentTransaction.replace(R.id.my_fragment, it)
            }

            Section.CALENDAR -> curFragment = CalendarFragment().also {
                fragmentTransaction.replace(R.id.my_fragment, it)
            }

            Section.BOOK -> {
                curFragment = (if (tab > 2000) BookFragment.newInstance(0, tab)
                else BookFragment.newInstance(tab, -1)).also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }

            Section.SEARCH -> {
                val search = if (intent.hasExtra(Const.LINK)) {
                    SearchFragment.newInstance(
                        intent.getStringExtra(Const.LINK), tab
                    )
                } else SearchFragment()
                curFragment = search
                fragmentTransaction.replace(R.id.my_fragment, search)
            }

            Section.JOURNAL -> fragmentTransaction.replace(R.id.my_fragment, JournalFragment())

            Section.MARKERS -> curFragment = MarkersFragment().also {
                fragmentTransaction.replace(R.id.my_fragment, it)
            }

            Section.SETTINGS -> curFragment = SettingsFragment().also {
                fragmentTransaction.replace(R.id.my_fragment, it)
            }

            Section.HELP -> {
                if (tab == -1) { //first run
                    val frHelp = HelpFragment.newInstance(0)
                    fragmentTransaction.replace(R.id.my_fragment, frHelp)
                } else fragmentTransaction.replace(R.id.my_fragment, HelpFragment())
            }

            Section.EPISTLES -> curFragment =
                BookFragment.newInstance(BookTab.EPISTLES.value, -1).also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }

            Section.DOCTRINE -> curFragment =
                BookFragment.newInstance(BookTab.DOCTRINE.value, -1).also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }

            Section.HOLY_RUS -> curFragment =
                BookFragment.newInstance(BookTab.HOLY_RUS.value, -1).also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }

            Section.WORLD_AFTER_WAR -> curFragment =
                BookFragment.newInstance(BookTab.WORLD_AFTER_WAR.value, -1).also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
        }
        firstTab = 0
        if (supportFragmentManager.isDestroyed.not()) fragmentTransaction.commit()
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
                snackbar.isShown -> snackbar.hide()

                status.isCrash -> {
                    status.setError(null)
                    unblocked()
                    curFragment?.resetError()
                }

                helper.shownActionMenu -> helper.hideActionMenu()
                curFragment?.onBackPressed() == false -> return

                firstSection == Section.NEW -> {
                    firstSection = helper.getFirstSection()
                    setSection(firstSection, false)
                }

                curFragment == null -> finishFlashStar()

                else -> exit()
            }
        }
    }

    private fun exit() {
        when {
            statusBack == StatusBack.EXIT -> finish()

            helper.prevSection != null -> helper.prevSection?.let {
                if (it == Section.SITE) setSection(it, false, SiteTab.SITE.value)
                else setSection(it, false)
                helper.prevSection = null
            }

            statusBack == StatusBack.FIRST -> setSection(firstSection, false)

            else -> {
                showToast(getString(R.string.click_for_exit))
                statusBack = StatusBack.EXIT
                lifecycleScope.launch {
                    delay(3000)
                    if (statusBack == StatusBack.EXIT) statusBack = StatusBack.PAGE
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
                .setTitle(getString(R.string.warning) + " " + item.title).setMessage(item.des)
                .setNegativeButton(getString(android.R.string.ok)) { dialog: DialogInterface, _ ->
                    dialog.dismiss()
                }.setOnDismissListener { showWelcome() }
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
                launchUtils.updateTime()
                if (state.timediff.absoluteValue > LIMIT_DIFF_SEC || state.hasNew) frWelcome =
                    WelcomeFragment.newInstance(state.hasNew, state.timediff)
                if (state.warnIndex > -1) showWarnAds(state.warnIndex)
            }

            is MainState.Status -> restoreStatus(state)
            is MainState.FirstRun -> firstRun(state)

            is ListState.Primary -> {
                if (frWelcome == null) frWelcome = WelcomeFragment.newInstance(false, 0)
                frWelcome?.list?.addAll(state.list)
            }

            is BasicState.Loading -> {
                blocked()
                status.loadText()
                status.setLoad(true)
            }

            is BasicState.Success -> {
                status.setLoad(false)
                unblocked()
            }

            is BasicState.Error -> setError(state)
        }
    }

    private fun firstRun(state: MainState.FirstRun) {
        val intent = intent
        if (firstTab == 0) firstTab = intent.getIntExtra(Const.TAB, 0)
        if (helper.isFirstRun) firstTab = -1
        else {
            intent.getStringExtra(Const.CUR_ID)
            if (firstSection == Section.MENU) updateNew()
        }
        if (intent.getBooleanExtra(Const.DIALOG, false)) helper.showDownloadDialog()
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

        if (supportFragmentManager.fragments.isEmpty() || (isSideMenu && curSection == Section.MENU)) setSection(
            firstSection,
            false
        )
        if (isSideMenu) setMenuFragment()
        else if (curSection != Section.MENU) statusBack = StatusBack.PAGE
        updateNew()
        if (state.shownDwnDialog) showDownloadDialog()
        if (state.isEditor) startEditMenu()
    }

    fun onAction(title: String) {
        curFragment?.onAction(title)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showNavBg() {
        val root = findViewById<ViewGroup>(R.id.root) ?: return
        insetsUtils?.let {
            if (it.navBar == null)
                it.createBottomBar(root)
            else it.navBar?.isVisible = true
        }
    }

    fun blocked() = helper.run {
        isBlocked = true
        tipAction.hide()
        tvTitle?.isVisible = false
        fabAction.isVisible = false
        bottomBar?.isVisible = false
        if (isNewAndroid) showNavBg()
    }

    fun unblocked() = helper.run {
        isBlocked = false
        tvTitle?.isVisible = true
        fabAction.isVisible = true
        bottomBar?.run {
            isVisible = true
            performShow()
        }
        if (isNewAndroid && !ScreenUtils.isTabletLand)
            insetsUtils?.navBar?.isVisible = false
    }

    fun setAction(icon: Int) {
        helper.setActionIcon(icon)
        if (isBlocked) helper.fabAction.isVisible = false
    }

    fun setFloatProm(isFloat: Boolean) {
        prom?.hide()
        prom = if (isFloat) PromUtils(helper.tvPromTimeFloat)
        else PromUtils(helper.tvPromTimeHead)
        prom?.show()
    }

    fun setError(error: BasicState.Error) {
        if (error.isNeedReport) {
            blocked()
            status.setError(error)
        } else snackbar.show(helper.fabAction, error.message)
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
        if (value == -1) helper.vsbScrollBar.value = helper.vsbScrollBar.maxValue
        else helper.vsbScrollBar.value = value
    }

    fun startEditMenu() {
        isEditor = true
    }

    fun finishEditMenu() {
        isEditor = false
        helper.bottomBar?.let { loadMenu(it) }
    }

    fun changeMenu(index: Int, item: MenuItem) = helper.bottomBar?.let { bar ->
        val bottomItem = bar.menu[index + 1]
        bottomItem.title = item.title
        bottomItem.icon = ContextCompat.getDrawable(this, item.image)
    }

    private fun loadMenu(bar: BottomAppBar) {
        val home = HomeHelper(this).apply { isMain = true }
        var i = 1
        helper.setBottomMenu(home.loadMenu(true))
        helper.bottomMenu.forEach {
            val item = bar.menu[i]
            val m = if (it == Section.HOME) MenuItem(
                R.drawable.ic_home,
                getString(R.string.home_screen)
            )
            else home.getItem(it)
            item.title = m.title
            item.icon = ContextCompat.getDrawable(this, m.image)
            i++
        }
    }

    fun setDarkTheme(dark: Boolean) {
        if (dark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            //saveThemePreference(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
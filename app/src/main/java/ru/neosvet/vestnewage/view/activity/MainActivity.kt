package ru.neosvet.vestnewage.view.activity

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ActionMenuView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.network.ConnectWatcher
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.BottomAnim
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.StatusButton
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import ru.neosvet.vestnewage.view.dialog.SetNotifDialog
import ru.neosvet.vestnewage.view.fragment.*
import ru.neosvet.vestnewage.view.fragment.WelcomeFragment.ItemClicker
import ru.neosvet.vestnewage.viewmodel.MainToiler
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import ru.neosvet.vestnewage.viewmodel.basic.AdsState
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.SuccessList
import kotlin.math.absoluteValue

class MainActivity : AppCompatActivity(), Observer<NeoState>, ItemClicker {
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

    @JvmField
    val status = StatusButton()
    private var prom: PromUtils? = null
    private var tab = 0
    private var isBlocked = false
    private var statusBack = StatusBack.EXIT

    private lateinit var utils: LaunchUtils
    private var jobBottomArea: Job? = null
    private var isShowBottomArea = false
    private var animTitle: BottomAnim? = null
    private var animButton: BottomAnim? = null

    val newId: Int
        get() = helper.newId

    override fun onCreate(savedInstanceState: Bundle?) {
        val withSplash = intent.getBooleanExtra(Const.START_SCEEN, true)
        if (savedInstanceState == null && withSplash)
            launchSplashScreen()
        else
            setTheme(R.style.Theme_MainTheme)
        setContentView(R.layout.main_activity)
        ConnectWatcher.start(this)
        ScreenUtils.init(this)
        App.context = this
        initLaunch()
        helper = MainHelper(this)
        helper.initViews()
        super.onCreate(savedInstanceState)
        setBottomPanel()
        status.init(this, helper.pStatus)
        initAnim()
        setFloatProm(helper.isFloatPromTime)

        restoreState(savedInstanceState)
        if (withSplash.not())
            finishFlashStar()
    }

    fun showGodWords() {
        val msg = helper.getGodWords()
        val dialog = CustomDialog(this)
        dialog.setTitle(getString(R.string.god_words))
        dialog.setRightButton(getString(R.string.close)) { dialog.dismiss() }
        if (msg.isEmpty()) {
            dialog.setMessage(getString(R.string.yet_load))
        } else {
            dialog.setMessage(msg)
            dialog.setLeftButton(getString(R.string.find)) {
                helper.changeSection(Section.SEARCH, true)
                curFragment = if (msg.indexOf("...") == 0)
                    SearchFragment.newInstance(msg.substring(3), 5)
                else
                    SearchFragment.newInstance(msg, 5)
                val fragmentTransaction = supportFragmentManager.beginTransaction()
                fragmentTransaction.replace(R.id.my_fragment, curFragment!!)
                fragmentTransaction.commit()
                dialog.dismiss()
            }
        }
        dialog.show(null)
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
                    openReader("Posyl-na-Edinenie.html", null)
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
        lifecycleScope.launch {
            delay(1600)
            helper.fabAction.post {
                finishFlashStar()
            }
        }
    }

    private fun initLaunch() {
        utils = LaunchUtils()
        utils.checkAdapterNewVersion()
        if (utils.openLink(intent)) {
            tab = utils.intent.getIntExtra(Const.TAB, tab)
            utils.intent.getStringExtra(Const.CUR_ID)?.let {
                firstSection = Section.valueOf(it)
            }
        } else if (utils.isNeedLoad) {
            val toiler = ViewModelProvider(this).get(MainToiler::class.java)
            toiler.init(this)
            toiler.state.observe(this, this)
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
        helper.topBar?.isVisible = true
        helper.setNewValue()
        if (helper.isFirstRun) {
            setSection(Section.HELP, false)
            statusBack = StatusBack.FIRST
            return
        }
        unblocked()
        firstSection = helper.getFirstSection()
        if (helper.startWithNew()) {
            setSection(Section.NEW, false)
            helper.prevSection = firstSection
        } else
            setSection(firstSection, false)
        showWelcome()
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
        } else helper.run {
            helper.topBar?.isVisible = true
            if (state.getBoolean(Const.PANEL))
                blocked()
            else
                unblocked()
            helper.setActionIcon(state.getInt(Const.SELECT))
            state.getString(Const.CUR_ID)?.let {
                curSection = Section.valueOf(it)
            }
            if (isSideMenu) setMenuFragment()
            else if (curSection != Section.MENU)
                statusBack = StatusBack.PAGE
            updateNew()
        }
    }

    override fun onDestroy() {
        ConnectWatcher.stop(this)
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
        outState.putString(Const.CUR_ID, helper.curSection.toString())
        outState.putBoolean(Const.PANEL, isBlocked)
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
        statusBack = StatusBack.PAGE
        setMenu(section, savePrev)
        status.setError(null)
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        curFragment = null
        helper.checkNew()
        prom?.show()
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
                curFragment = SummaryFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
                clearSummaryNotif()
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
                    utils.intent.hasExtra(Const.LINK) -> {
                        SearchFragment.newInstance(
                            utils.intent.getStringExtra(Const.LINK), tab
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
            Section.HELP -> if (tab == -1) { //first isRun
                val frHelp = HelpFragment.newInstance(0)
                fragmentTransaction.replace(R.id.my_fragment, frHelp)
            } else {
                fragmentTransaction.replace(R.id.my_fragment, HelpFragment())
            }
        }
        tab = 0
        fragmentTransaction.commit()
    }

    private fun setMenu(section: Section, savePrev: Boolean) {
        helper.changeSection(section, savePrev)
        if (helper.isSideMenu) helper.setMenuFragment()
    }

    private fun clearSummaryNotif() {
        var id = 0
        if (intent.hasExtra(DataBase.ID)) {
            id = intent.getIntExtra(DataBase.ID, NotificationUtils.NOTIF_SUMMARY)
            intent.removeExtra(DataBase.ID)
        } else if (utils.intent.hasExtra(DataBase.ID)) {
            id = utils.intent.getIntExtra(DataBase.ID, NotificationUtils.NOTIF_SUMMARY)
            utils.intent.removeExtra(DataBase.ID)
        }
        if (id != 0) {
            val helper = NotificationUtils()
            var i = NotificationUtils.NOTIF_SUMMARY
            while (i <= id) {
                helper.cancel(i)
                i++
            }
        }
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
        if (curFragment?.onBackPressed() == false)
            return
        when {
            helper.shownActionMenu ->
                helper.hideActionMenu()
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
                statusBack = StatusBack.EXIT
                Lib.showToast(getString(R.string.click_for_exit))
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
        val ads = AdsUtils(this)
        val item = ads.getItem(warn)
        ads.close()
        if (item[AdsUtils.TITLE].isEmpty()) return
        val builder = AlertDialog.Builder(this, R.style.NeoDialog)
            .setTitle(getString(R.string.warning) + " " + item[AdsUtils.TITLE])
            .setMessage(item[AdsUtils.DES])
            .setNegativeButton(getString(android.R.string.ok)) { dialog: DialogInterface, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener { showWelcome() }
        if (item[AdsUtils.LINK] != null) {
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

    override fun onChanged(state: NeoState) {
        when (state) {
            is AdsState -> {
                utils.reInitProm(state.timediff)
                if (state.timediff.absoluteValue > LIMIT_DIFF_SEC || state.hasNew)
                    frWelcome = WelcomeFragment.newInstance(state.hasNew, state.timediff)
                if (state.warnIndex > -1)
                    showWarnAds(state.warnIndex)
            }
            is SuccessList -> {
                if (frWelcome == null)
                    frWelcome = WelcomeFragment.newInstance(false, 0)
                frWelcome?.list?.addAll(state.list)
            }
            is NeoState.Error ->
                status.setError(state.throwable.localizedMessage)
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
            if (type != MainHelper.ActionType.INVISIBLE)
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
            if (type != MainHelper.ActionType.INVISIBLE)
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
        if (type != MainHelper.ActionType.INVISIBLE)
            fabAction.isVisible = true
        bottomBar?.run {
            isVisible = true
            performShow()
        }
    }

    fun setAction(icon: Int) {
        helper.setActionIcon(icon)
    }

    fun setFloatProm(isFloat: Boolean) {
        prom?.hide()
        prom = if (isFloat)
            PromUtils(helper.tvPromTimeFloat)
        else
            PromUtils(findViewById(R.id.tvPromTimeHead))
        prom?.show()
    }

    fun hideHead() {
        helper.topBar?.setExpanded(false)
    }
}
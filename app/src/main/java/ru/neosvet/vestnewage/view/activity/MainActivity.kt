package ru.neosvet.vestnewage.view.activity

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.network.ConnectWatcher
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.service.LoaderService
import ru.neosvet.vestnewage.utils.*
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.MultiWindowSupport
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.StatusButton
import ru.neosvet.vestnewage.view.dialog.SetNotifDialog
import ru.neosvet.vestnewage.view.fragment.*
import ru.neosvet.vestnewage.view.fragment.WelcomeFragment.ItemClicker
import ru.neosvet.vestnewage.viewmodel.MainToiler
import ru.neosvet.vestnewage.viewmodel.SiteToiler
import ru.neosvet.vestnewage.viewmodel.basic.AdsState
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.SuccessList
import java.util.*
import kotlin.math.absoluteValue

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener,
    Observer<NeoState>, ItemClicker {
    companion object {
        @JvmField
        var isCountInMenu = false
        private const val LIMIT_DIFF_SEC = 4
    }

    private enum class StatusBack {
        FIRST, PAGE, MENU, EXIT
    }

    private lateinit var helper: MainHelper
    private var firstFragment = Const.SCREEN_CALENDAR
    private var curFragment: NeoFragment? = null
    private var frWelcome: WelcomeFragment? = null

    @JvmField
    val status = StatusButton()
    private var prom: PromUtils? = null
    private var tab = 0
    private var statusBack = StatusBack.MENU

    @JvmField
    var fab: View? = null
    private lateinit var utils: LaunchUtils
    private lateinit var anMin: Animation
    private lateinit var anMax: Animation

    val newId: Int
        get() = helper.newId

    val tabLayout: TabLayout
        get() = helper.tabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ConnectWatcher.start(this)
        ScreenUtils.init(this)
        App.context = this
        if (savedInstanceState == null)
            launchSplashScreen()
        else
            setTheme(R.style.Theme_MainTheme)
        initLaunch()
        helper = MainHelper(this)
        firstFragment = helper.getFirstFragment()
        if (helper.isFullMenu)
            setContentView(R.layout.main_content)
        else
            setContentView(R.layout.main_activity)
        helper.initViews()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null)
            helper.toolbar?.isVisible = false

        status.init(this, helper.pStatus)
        initInterface()
        initAnim()
        isCountInMenu = helper.isCountInMenu
        if (isCountInMenu.not() || helper.isFullMenu) {
            prom = PromUtils(helper.tvPromTime)
        } else //it is not land
            helper.navView?.let {
                prom = PromUtils(
                    it.getHeaderView(0).findViewById(R.id.tvPromTimeInMenu)
                )
            }

        restoreState(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode) {
            MultiWindowSupport.resizeFloatTextView(helper.tvNew, true)
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
                    finishFlashStar()
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
            provider.view.startAnimation(anStar)
        }
    }

    private fun initLaunch() {
        utils = LaunchUtils()
        utils.checkAdapterNewVersion()
        if (utils.openLink(intent)) {
            tab = utils.intent.getIntExtra(Const.TAB, tab)
            firstFragment = utils.intent.getIntExtra(Const.CUR_ID, firstFragment)
        } else if (utils.isNeedLoad) {
            val toiler = ViewModelProvider(this).get(MainToiler::class.java)
            toiler.init(this)
            toiler.state.observe(this, this)
            toiler.load()
        }
    }

    fun setFragment(fragment: NeoFragment) {
        curFragment = fragment
        if (tabLayout.isVisible)
            tabLayout.removeAllTabs()
        tabLayout.isVisible = fragment is BookFragment || fragment is SiteFragment
    }

    private fun finishFlashStar() {
        helper.setNewValue()
        helper.toolbar?.isVisible = true
        if (helper.isFirstRun) {
            setFragment(R.id.nav_help, false)
            statusBack = StatusBack.FIRST
            return
        }
        if (firstFragment != 0)
            setFragment(firstFragment, false)
        showWelcome()
    }

    private fun initAnim() {
        anMin = AnimationUtils.loadAnimation(this, R.anim.minimize)
        anMin.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                helper.tvNew.isVisible = false
                fab?.isVisible = false
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        anMax = AnimationUtils.loadAnimation(this, R.anim.maximize)
    }

    private fun restoreState(state: Bundle?) {
        if (state == null) {
            val intent = intent
            tab = intent.getIntExtra(Const.TAB, tab)
            if (helper.isFirstRun)
                tab = -1
            else {
                val id = intent.getIntExtra(Const.CUR_ID, 0)
                if (id != 0)
                    firstFragment = id
                if (firstFragment == R.id.menu_fragment)
                    updateNew()
            }
        } else helper.run {
            curId = state.getInt(Const.CUR_ID)
            when {
                isFloatMenu -> navView?.setCheckedItem(curId)
                isSideMenu -> setMenuFragment()
                isFullMenu -> if (curId != R.id.menu_fragment)
                    statusBack = StatusBack.PAGE
            }
            updateNew()
            tvNew.clearAnimation()
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
        if (!helper.isFullMenu || !isCountInMenu || helper.curId == R.id.menu_fragment)
            prom?.resume()
        else
            prom?.hide()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration?) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            MultiWindowSupport.resizeFloatTextView(
                helper.tvNew,
                isInMultiWindowMode
            )
    }

    fun setProm(textView: View) {
        prom = PromUtils(textView)
    }

    fun updateNew() {
        helper.updateNew()
    }

    private fun initInterface() = helper.run {
        bDownloadIt.setOnClickListener {
            menuDownload.hide()
            if (curId == R.id.nav_calendar) {
                val year = (curFragment as CalendarFragment?)!!.currentYear
                LoaderService.postCommand(
                    LoaderService.DOWNLOAD_YEAR, year.toString()
                )
            } else {
                LoaderService.postCommand(
                    LoaderService.DOWNLOAD_ID, curId.toString()
                )
            }
        }
        tvNew.setOnClickListener {
            setFragment(R.id.nav_new, true)
        }
        toolbar?.let {
            setSupportActionBar(it)
            if (helper.isFloatMenu)
                initFloatMenu()
        }
    }

    private fun initFloatMenu() = helper.run {
        drawer?.let { drawer ->
            val toggle = ActionBarDrawerToggle(
                this@MainActivity,
                drawer,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
            )
            drawer.addDrawerListener(toggle)
            toggle.syncState()
        }

        navView?.run {
            setNavigationItemSelectedListener(this@MainActivity)
            getHeaderView(0).setOnClickListener {
                val url = NeoClient.SITE.substring(0, NeoClient.SITE.length - 1)
                Lib.openInApps(url, null)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(Const.CUR_ID, helper.curId)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val miDownloadAll = menu.add(getString(R.string.download_title))
        miDownloadAll.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        miDownloadAll.setIcon(R.drawable.download_button)
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        helper.drawer?.closeDrawer(GravityCompat.START)
        if (!item.isChecked) setFragment(item.itemId, false)
        return true
    }

    fun setFrMenu(frMenu: MenuFragment) {
        helper.frMenu = frMenu
        frMenu.setNew(newId)
    }

    @SuppressLint("NonConstantResourceId")
    fun setFragment(id: Int, savePrev: Boolean) {
        statusBack = StatusBack.PAGE
        setMenu(id, savePrev)
        status.setError(null)
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        curFragment = null
        helper.checkNew()
        when (id) {
            R.id.menu_fragment -> {
                hideTabs()
                statusBack = StatusBack.MENU
                helper.frMenu = MenuFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
                if (isCountInMenu) prom?.show()
            }
            R.id.nav_new -> {
                fragmentTransaction.replace(R.id.my_fragment, NewFragment())
                helper.frMenu?.setSelect(R.id.nav_new)
            }
            R.id.nav_rss -> {
                curFragment = SummaryFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
                clearSummaryNotif()
            }
            R.id.nav_site -> {
                curFragment = SiteFragment.newInstance(tab).also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }
            R.id.nav_calendar -> {
                curFragment = CalendarFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }
            R.id.nav_book -> {
                curFragment = BookFragment.newInstance(tab, -1).also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }
            R.id.nav_search -> {
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
            R.id.nav_journal -> {
                hideTabs()
                fragmentTransaction.replace(R.id.my_fragment, JournalFragment())
            }
            R.id.nav_marker -> {
                curFragment = MarkersFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }
            R.id.nav_cabinet -> {
                curFragment = CabinetFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }
            R.id.nav_settings -> {
                curFragment = SettingsFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }
            R.id.nav_help -> if (tab == -1) { //first isRun
                val frHelp = HelpFragment.newInstance(0)
                fragmentTransaction.replace(R.id.my_fragment, frHelp)
            } else {
                hideTabs()
                fragmentTransaction.replace(R.id.my_fragment, HelpFragment())
            }
        }
        tab = 0
        fragmentTransaction.commit()
    }

    private fun hideTabs() {
        if (tabLayout.isVisible) {
            tabLayout.removeAllTabs()
            tabLayout.isVisible = false
        }
    }

    private fun setMenu(id: Int, savePrev: Boolean) {
        helper.changeId(id, savePrev)
        when {
            helper.isFloatMenu -> helper.navView?.setCheckedItem(id)
            helper.isSideMenu -> helper.setMenuFragment()
            helper.isFullMenu -> if (isCountInMenu && id != R.id.menu_fragment)
                prom?.hide()
        }
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
        if (helper.drawer?.isDrawerOpen(GravityCompat.START) == true) {
            helper.drawer!!.closeDrawer(GravityCompat.START)
        } else if (helper.hasPrevId) {
            if (canBack.not()) return
            if (helper.prevId == R.id.nav_site)
                tab = SiteToiler.TAB_SITE
            setFragment(helper.prevId, false)
            helper.prevId = 0
        } else if (firstFragment == R.id.nav_new) {
            firstFragment = helper.getFirstFragment()
            setFragment(firstFragment, false)
        } else if (canBack)
            exit()
    }

    private val canBack: Boolean
        get() = curFragment?.onBackPressed() ?: true

    private fun exit() {
        if (statusBack == StatusBack.EXIT) {
            super.onBackPressed()
        } else if (statusBack == StatusBack.PAGE && helper.isFullMenu) {
            setFragment(R.id.menu_fragment, false)
        } else if (statusBack == StatusBack.FIRST) {
            setFragment(firstFragment, false)
        } else {
            statusBack = StatusBack.EXIT
            Lib.showToast(getString(R.string.click_for_exit))
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    if (statusBack == StatusBack.EXIT)
                        statusBack = StatusBack.MENU
                }
            }, (3 * DateUnit.SEC_IN_MILLS).toLong())
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        showDownloadMenu()
        return super.onOptionsItemSelected(item)
    }

    fun showDownloadMenu() {
        helper.showDownloadMenu()
    }

    fun openBook(link: String, isPoems: Boolean) {
        tab = if (isPoems) 0 else 1
        val year = helper.getYear(link)
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        curFragment = BookFragment.newInstance(tab, year).also {
            fragmentTransaction.replace(R.id.my_fragment, it)
            fragmentTransaction.commit()
        }
        setMenu(R.id.nav_book, true)
    }

    fun startAnimMin() {
        if (helper.countNew > 0 && helper.tvNew.isVisible)
            helper.tvNew.startAnimation(anMin)
        if (fab?.isVisible == true)
            fab?.startAnimation(anMin)
    }

    fun startAnimMax() {
        if (helper.checkNew() && helper.tvNew.isVisible.not()) {
            helper.tvNew.isVisible = true
            helper.tvNew.startAnimation(anMax)
        }
        if (fab?.isVisible == false) {
            fab?.isVisible = true
            fab?.startAnimation(anMax)
        }
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
            setFragment(R.id.nav_site, true)
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
        }
    }
}
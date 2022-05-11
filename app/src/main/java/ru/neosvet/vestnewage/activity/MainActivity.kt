package ru.neosvet.vestnewage.activity

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
import ru.neosvet.ui.MultiWindowSupport
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.StatusButton
import ru.neosvet.ui.dialogs.SetNotifDialog
import ru.neosvet.utils.*
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.fragment.*
import ru.neosvet.vestnewage.fragment.WelcomeFragment.ItemClicker
import ru.neosvet.vestnewage.helpers.*
import ru.neosvet.vestnewage.model.MainModel
import ru.neosvet.vestnewage.model.SiteModel
import ru.neosvet.vestnewage.model.basic.AdsState
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.SuccessList
import ru.neosvet.vestnewage.service.LoaderService
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
        MENU, PAGE, EXIT
    }

    private lateinit var helper: MainHelper
    val isMenuMode: Boolean
        get() = helper.menuType == MainHelper.MenuType.FULL
    private var firstFragment = Const.SCREEN_CALENDAR
    private var curFragment: NeoFragment? = null
    private var frWelcome: WelcomeFragment? = null

    @JvmField
    val status = StatusButton()
    private var prom: PromHelper? = null
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
        ScreenUtils.init(this)
        App.context = this
        if (savedInstanceState == null)
            launchSplashScreen()
        else
            setTheme(R.style.Theme_MainTheme)
        initLaunch()
        helper = MainHelper(this)
        firstFragment = helper.getFirstFragment()
        if (isMenuMode)
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
        if (isCountInMenu.not() || isMenuMode) {
            prom = PromHelper(helper.tvPromTime)
        } else //it is not land
            helper.navView?.let {
                prom = PromHelper(
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
            val model = ViewModelProvider(this).get(MainModel::class.java)
            model.init(this)
            model.state.observe(this, this)
            model.load()
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
                firstFragment = if (id == 0)
                    helper.getFirstFragment()
                else id
            }
        } else helper.run {
            curId = state.getInt(Const.CUR_ID)
            navView?.setCheckedItem(curId)
            if (isSideMenu)
                setMenuFragment()
            updateNew()
            tvNew.clearAnimation()
        }
    }

    override fun onPause() {
        prom?.stop()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        prom?.resume()
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
        prom = PromHelper(textView)
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
            if (isMenuMode.not())
                initDrawerMenu()
        }
    }

    private fun initDrawerMenu() = helper.run {
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
                            intent.getStringExtra(Const.LINK),
                            tab, intent.getIntExtra(Const.SEARCH, 1)
                        )
                    }
                    utils.intent.hasExtra(Const.LINK) -> {
                        SearchFragment.newInstance(
                            utils.intent.getStringExtra(Const.LINK),
                            tab, utils.intent.getIntExtra(Const.SEARCH, 1)
                        )
                    }
                    else -> SearchFragment()
                }
                curFragment = search
                fragmentTransaction.replace(R.id.my_fragment, search)
            }
            R.id.nav_journal -> {
                if (tabLayout.isVisible) {
                    tabLayout.removeAllTabs()
                    tabLayout.isVisible = false
                }
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
                if (tabLayout.isVisible) {
                    tabLayout.removeAllTabs()
                    tabLayout.isVisible = false
                }
                fragmentTransaction.replace(R.id.my_fragment, HelpFragment())
            }
        }
        tab = 0
        fragmentTransaction.commit()
    }

    private fun setMenu(id: Int, savePrev: Boolean) {
        helper.changeId(id, savePrev)
        when (helper.menuType) {
            MainHelper.MenuType.FLOAT -> helper.navView?.setCheckedItem(id)
            MainHelper.MenuType.SIDE -> helper.setMenuFragment()
            MainHelper.MenuType.FULL -> if (isCountInMenu && id != R.id.menu_fragment) prom?.hide()
        }
    }

    private fun clearSummaryNotif() {
        var id = 0
        if (intent.hasExtra(DataBase.ID)) {
            id = intent.getIntExtra(DataBase.ID, NotificationHelper.NOTIF_SUMMARY)
            intent.removeExtra(DataBase.ID)
        } else if (utils.intent.hasExtra(DataBase.ID)) {
            id = utils.intent.getIntExtra(DataBase.ID, NotificationHelper.NOTIF_SUMMARY)
            utils.intent.removeExtra(DataBase.ID)
        }
        if (id != 0) {
            val helper = NotificationHelper()
            var i = NotificationHelper.NOTIF_SUMMARY
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
        } else if (helper.isFirstRun) {
            setFragment(firstFragment, false)
        } else if (helper.hasPrevId) {
            if (canBack.not()) return
            if (helper.prevId == R.id.nav_site)
                tab = SiteModel.TAB_SITE
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
        } else if (statusBack == StatusBack.PAGE && isMenuMode) {
            statusBack = StatusBack.MENU
            setFragment(R.id.menu_fragment, false)
        } else {
            statusBack = StatusBack.EXIT
            Lib.showToast(getString(R.string.click_for_exit))
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    if (statusBack == StatusBack.EXIT)
                        statusBack = StatusBack.MENU
                }
            }, (3 * DateHelper.SEC_IN_MILLS).toLong())
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
        if (fab?.isVisible == false) return
        if (helper.countNew > 0) helper.tvNew.startAnimation(anMin)
        fab?.startAnimation(anMin)
    }

    fun startAnimMax() {
        if (helper.checkNew()) helper.tvNew.startAnimation(anMax)
        fab?.isVisible = true
        fab?.startAnimation(anMax)
    }

    private fun showWelcome() {
        frWelcome?.show(supportFragmentManager, null)
    }

    private fun showWarnAds(warn: Int) {
        val ads = DevadsHelper(this)
        val item = ads.getItem(warn)
        ads.close()
        if (item[DevadsHelper.TITLE].isEmpty()) return
        val builder = AlertDialog.Builder(this, R.style.NeoDialog)
            .setTitle(getString(R.string.warning) + " " + item[DevadsHelper.TITLE])
            .setMessage(item[DevadsHelper.DES])
            .setNegativeButton(getString(android.R.string.ok)) { dialog: DialogInterface, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener { showWelcome() }
        if (item[DevadsHelper.LINK] != null) {
            builder.setPositiveButton(getString(R.string.open_link)) { _, _ ->
                Lib.openInApps(item[DevadsHelper.LINK], null)
            }
        }
        builder.create().show()
    }

    override fun onItemClick(link: String) {
        if (link.isEmpty()) return
        if (link == Const.ADS) {
            tab = SiteModel.TAB_DEV
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
                frWelcome?.pagesList = state.list
            }
            is NeoState.Error ->
                status.setError(state.throwable.localizedMessage)
        }
    }
}
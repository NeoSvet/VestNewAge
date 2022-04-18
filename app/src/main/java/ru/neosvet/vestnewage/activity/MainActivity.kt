package ru.neosvet.vestnewage.activity

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.work.Data
import com.google.android.material.navigation.NavigationView
import ru.neosvet.ui.MultiWindowSupport
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.StatusButton
import ru.neosvet.ui.Tip
import ru.neosvet.ui.dialogs.SetNotifDialog
import ru.neosvet.utils.*
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.databinding.MainActivityBinding
import ru.neosvet.vestnewage.databinding.MainContentBinding
import ru.neosvet.vestnewage.fragment.*
import ru.neosvet.vestnewage.fragment.WelcomeFragment.ItemClicker
import ru.neosvet.vestnewage.helpers.*
import ru.neosvet.vestnewage.model.SiteModel
import ru.neosvet.vestnewage.model.SlashModel
import java.util.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener,
    Observer<Bundle>, ItemClicker {
    companion object {
        var isFirst = false

        @JvmField
        var isCountInMenu = false
        private const val LIMIT_DIFF_SEC = 4
    }

    private enum class StatusBack {
        MENU, PAGE, EXIT
    }

    enum class MenuType {
        FLOAT, SIDE, FULL
    }

    private var binding: MainActivityBinding? = null
    private lateinit var content: MainContentBinding
    var menuType = MenuType.FLOAT
    val isMenuMode: Boolean
        get() = menuType == MenuType.FULL
    var isBlinked = false
    private var firstFragment = Const.SCREEN_CALENDAR
    private var frMenu: MenuFragment? = null
    private var curFragment: NeoFragment? = null
    private var frWelcome: WelcomeFragment? = null
    private lateinit var menuDownload: Tip

    @JvmField
    val status = StatusButton()
    private var prom: PromHelper? = null
    private val pref: SharedPreferences by lazy {
        getSharedPreferences(MainActivity::class.java.simpleName, MODE_PRIVATE)
    }
    private lateinit var unread: UnreadHelper
    private var curId = 0
    private var prevId = 0
    private var tab = 0
    private var statusBack = StatusBack.MENU
    private var countNew = 0
    private val drawer: DrawerLayout?
        get() = binding?.drawerLayout as DrawerLayout?

    @JvmField
    var fab: View? = null
    private lateinit var slash: SlashUtils
    private lateinit var anMin: Animation
    private lateinit var anMax: Animation

    val newId: Int
        get() = unread.getNewId(countNew)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initFirstFragment()
        initContentView()
        if (savedInstanceState == null)
            initStar()
        else
            content.ivStar.isVisible = false
        initSlash()

        status.init(this, content.pStatus)
        menuDownload = Tip(this, content.pDownload)
        unread = UnreadHelper()
        initInterface()
        initAnim()
        initProgress()
        isCountInMenu = pref.getBoolean(Const.COUNT_IN_MENU, true)
        if (isCountInMenu.not() || isMenuMode) {
            prom = PromHelper(content.tvPromTime)
        } else //it is not tablet and land
            binding?.navView?.let {
                prom = PromHelper(
                    it.getHeaderView(0).findViewById(R.id.tvPromTimeInMenu)
                )
            }

        restoreState(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode) {
            MultiWindowSupport.resizeFloatTextView(content.tvNew, true)
        }
    }

    private fun initSlash() {
        val model = ViewModelProvider(this).get(SlashModel::class.java)
        slash = SlashUtils()
        if (slash.openLink(intent)) {
            tab = slash.intent.getIntExtra(Const.TAB, tab)
            firstFragment = slash.intent.getIntExtra(Const.CUR_ID, firstFragment)
        } else if (slash.isNeedLoad) {
            slash.checkAdapterNewVersion()
            SlashModel.addObserver(this, this)
            model.startLoad()
        }
    }

    private fun initContentView() {
        if (isMenuMode) {
            content = MainContentBinding.inflate(layoutInflater)
            setContentView(content.root)
        } else {
            binding = MainActivityBinding.inflate(layoutInflater).also {
                content = MainContentBinding.bind(it.root.findViewById(R.id.content_main))
                setContentView(it.root)
            }
        }
    }

    private fun initFirstFragment() {
        val startScreen = pref.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR)
        val mode = resources.getInteger(R.integer.screen_mode)
        val tablet = resources.getInteger(R.integer.screen_tablet_port)
        firstFragment = when {
            startScreen == Const.SCREEN_MENU && mode < tablet -> {
                menuType = MenuType.FULL
                R.id.menu_fragment
            }
            startScreen == Const.SCREEN_SUMMARY ->
                R.id.nav_rss
            else -> //startScreen == Const.SCREEN_CALENDAR || !isMenuMode ->
                R.id.nav_calendar
        }
    }

    fun setFragment(fragment: NeoFragment) {
        curFragment = fragment
    }

    private fun initProgress() {
        ProgressHelper.addObserver(this) { data: Data? ->
            if (curFragment == null || data == null || !ProgressHelper.isBusy())
                return@addObserver
            curFragment!!.onChanged(data)
        }
        if (ProgressHelper.isBusy()) status.setLoad(true)
    }

    private fun initStar() {
        val anStar = AnimationUtils.loadAnimation(this, R.anim.flash)
        anStar.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                content.ivStar.isVisible = false
                if (isFirst) {
                    setFragment(R.id.nav_help, false)
                    isFirst = true
                    return
                }
                if (firstFragment != 0) setFragment(firstFragment, false)
                if (frWelcome != null && !frWelcome!!.isAdded && isBlinked) showWelcome()
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        content.ivStar.startAnimation(anStar)
    }

    private fun initAnim() {
        anMin = AnimationUtils.loadAnimation(this, R.anim.minimize)
        anMin.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                content.tvNew.isVisible = false
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
            if (pref.getBoolean(Const.FIRST, true)) {
                val editor = pref.edit()
                editor.putBoolean(Const.FIRST, false)
                editor.apply()
                tab = -1
                isFirst = true
            } else {
                val startNew = pref.getBoolean(Const.START_NEW, false)
                firstFragment = if (startNew && countNew > 0)
                    R.id.nav_new
                else
                    intent.getIntExtra(Const.CUR_ID, firstFragment)
            }
        } else {
            curId = state.getInt(Const.CUR_ID)
            if (menuType == MenuType.SIDE)
                setMenuFragment()
        }
        updateNew()
        content.tvNew.clearAnimation()
    }

    override fun onPause() {
        super.onPause()
        prom?.stop()
    }

    override fun onResume() {
        super.onResume()
        prom?.resume()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration?) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            MultiWindowSupport.resizeFloatTextView(
                content.tvNew,
                isInMultiWindowMode
            )
    }

    fun setProm(textView: View) {
        prom = PromHelper(textView)
    }

    fun updateNew() {
        countNew = unread.count
        binding?.navView?.menu?.getItem(0)?.setIcon(newId) ?: frMenu?.setNew(newId)
        content.tvNew.text = countNew.toString()
        if (setNew())
            content.tvNew.startAnimation(AnimationUtils.loadAnimation(this, R.anim.blink))
    }

    private fun initInterface() = content.run {
        bDownloadAll.setOnClickListener {
            menuDownload.hide()
            LoaderHelper.postCommand(LoaderHelper.DOWNLOAD_ALL, "")
        }
        bDownloadIt.setOnClickListener {
            menuDownload.hide()
            if (curId == R.id.nav_calendar) {
                LoaderHelper.postCommand(
                    LoaderHelper.DOWNLOAD_YEAR,
                    (curFragment as CalendarFragment?)!!.currentYear.toString()
                )
            } else {
                LoaderHelper.postCommand(LoaderHelper.DOWNLOAD_ID, curId.toString())
            }
        }
        tvNew.setOnClickListener {
            if (!ProgressHelper.isBusy())
                setFragment(R.id.nav_new, true)
        }
        if (resources.getInteger(R.integer.screen_mode) !=
            resources.getInteger(R.integer.screen_tablet_land)
        ) {
            setSupportActionBar(toolbar)
            if (isMenuMode.not())
                initDrawerMenu()
        }
    }

    private fun initDrawerMenu() = binding?.run {
        val toggle = ActionBarDrawerToggle(
            this@MainActivity,
            drawer,
            content.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer?.addDrawerListener(toggle)
        toggle.syncState()

        navView?.run {
            setNavigationItemSelectedListener(this@MainActivity)
            setCheckedItem(curId)
            getHeaderView(0).setOnClickListener {
                val url = NeoClient.SITE.substring(0, NeoClient.SITE.length - 1)
                Lib.openInApps(url, null)
            }
        }
    }

    private fun setMenuFragment() {
        if (frMenu == null) {
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            frMenu = MenuFragment().also {
                it.setSelect(curId)
                fragmentTransaction.replace(R.id.menu_fragment, it).commit()
            }
        } else frMenu?.setSelect(curId)
        frMenu?.setNew(newId)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(Const.CUR_ID, curId)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        //getMenuInflater().inflate(R.menu.menu_table, menu);
        val miDownloadAll = menu.add(getString(R.string.download_title))
        miDownloadAll.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        miDownloadAll.setIcon(R.drawable.download_button)
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawer?.closeDrawer(GravityCompat.START)
        if (checkBusy()) return false
        if (!item.isChecked) setFragment(item.itemId, false)
        return true
    }

    fun setFrMenu(frMenu: MenuFragment) {
        this.frMenu = frMenu
        frMenu.setNew(newId)
    }

    @SuppressLint("NonConstantResourceId")
    fun setFragment(id: Int, savePrev: Boolean) {
        statusBack = StatusBack.PAGE
        isFirst = false
        menuDownload.hide()
        prevId = if (savePrev) curId else 0
        curId = id
        when (menuType) {
            MenuType.FLOAT -> binding?.navView?.setCheckedItem(id)
            MenuType.SIDE -> setMenuFragment()
            MenuType.FULL -> if (isCountInMenu && id != R.id.menu_fragment) prom?.hide()
        }
        status.setError(null)
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        curFragment = null
        setNew()
        when (id) {
            R.id.menu_fragment -> {
                statusBack = StatusBack.MENU
                frMenu = MenuFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
                if (isCountInMenu) prom?.show()
            }
            R.id.nav_new -> {
                fragmentTransaction.replace(R.id.my_fragment, NewFragment())
                frMenu?.setSelect(R.id.nav_new)
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
                    slash.intent.hasExtra(Const.LINK) -> {
                        SearchFragment.newInstance(
                            slash.intent.getStringExtra(Const.LINK),
                            tab, slash.intent.getIntExtra(Const.SEARCH, 1)
                        )
                    }
                    else -> SearchFragment()
                }
                curFragment = search
                fragmentTransaction.replace(R.id.my_fragment, search)
            }
            R.id.nav_journal ->
                fragmentTransaction.replace(R.id.my_fragment, JournalFragment())
            R.id.nav_marker -> {
                curFragment = CollectionsFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }
            R.id.nav_cabinet -> {
                curFragment = CabmainFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }
            R.id.nav_settings -> {
                curFragment = SettingsFragment().also {
                    fragmentTransaction.replace(R.id.my_fragment, it)
                }
            }
            R.id.nav_help -> if (tab == -1) { //first start
                val frHelp = HelpFragment.newInstance(0)
                fragmentTransaction.replace(R.id.my_fragment, frHelp)
            } else
                fragmentTransaction.replace(R.id.my_fragment, HelpFragment())
        }
        tab = 0
        fragmentTransaction.commit()
    }

    private fun clearSummaryNotif() {
        var id = 0
        if (intent.hasExtra(DataBase.ID)) {
            id = intent.getIntExtra(DataBase.ID, NotificationHelper.NOTIF_SUMMARY)
            intent.removeExtra(DataBase.ID)
        } else if (slash.intent.hasExtra(DataBase.ID)) {
            id = slash.intent.getIntExtra(DataBase.ID, NotificationHelper.NOTIF_SUMMARY)
            slash.intent.removeExtra(DataBase.ID)
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

    private fun setNew(): Boolean {
        if (countNew > 0 && (curId == R.id.nav_new || curId == R.id.nav_rss ||
                    curId == R.id.nav_site || curId == R.id.nav_calendar)) {
            content.tvNew.isVisible = true
            return true
        }
        content.tvNew.isVisible = false
        return false
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
        if (drawer != null && drawer!!.isDrawerOpen(GravityCompat.START)) {
            drawer!!.closeDrawer(GravityCompat.START)
        } else if (isFirst) {
            setFragment(firstFragment, false)
        } else if (prevId != 0) {
            if (curFragment != null && !curFragment!!.onBackPressed()) return
            if (prevId == R.id.nav_site) tab = SiteModel.TAB_SITE
            setFragment(prevId, false)
        } else if (firstFragment == R.id.nav_new) {
            val startScreen = pref.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR)
            firstFragment = when (startScreen) {
                Const.SCREEN_MENU -> R.id.menu_fragment
                Const.SCREEN_SUMMARY -> R.id.nav_rss
                else -> R.id.nav_calendar
            }
            setFragment(firstFragment, false)
        } else if (curFragment != null) {
            if (curFragment!!.onBackPressed()) exit()
        } else exit()
    }

    private fun exit() {
        if (statusBack == StatusBack.EXIT) {
            super.onBackPressed()
        } else if (statusBack == StatusBack.PAGE && isMenuMode) {
            statusBack = StatusBack.MENU
            setFragment(R.id.menu_fragment, false)
        } else { //statusBack == STATUS_MENU;
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

    @SuppressLint("NonConstantResourceId")
    fun showDownloadMenu() {
        if (menuDownload.isShow)
            menuDownload.hide()
        else {
            if (ProgressHelper.isBusy()) return
            with(content.bDownloadIt) {
                when (curId) {
                    R.id.nav_site -> {
                        isVisible = true
                        text = getString(R.string.download_it_main)
                    }
                    R.id.nav_calendar -> {
                        isVisible = true
                        text = getString(R.string.download_it_calendar)
                    }
                    R.id.nav_book -> {
                        isVisible = true
                        text = getString(R.string.download_it_book)
                    }
                    else -> isVisible = false
                }
            }
            menuDownload.show()
        }
    }

    fun openBook(link: String, katren: Boolean) {
        tab = if (katren) 0 else 1
        //setFragment(R.id.nav_book, true)
        var year = 2016
        try {
            var s = link
            s = s.substring(s.lastIndexOf("/") + 1, s.lastIndexOf("."))
            year = s.toInt()
        } catch (ignored: Exception) {
        }
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        curFragment = BookFragment.newInstance(tab, year).also {
            fragmentTransaction.replace(R.id.my_fragment, it)
        }
    }

    fun startAnimMin() {
        if (fab?.isVisible == false) return
        if (countNew > 0) content.tvNew.startAnimation(anMin)
        fab?.startAnimation(anMin)
    }

    fun startAnimMax() {
        if (setNew()) content.tvNew.startAnimation(anMax)
        fab?.isVisible = true
        fab?.startAnimation(anMax)
    }

    fun checkBusy(): Boolean {
        if (ProgressHelper.isBusy()) {
            Lib.showToast(getString(R.string.app_is_busy))
            return true
        }
        return false
    }

    override fun onChanged(data: Bundle) {
        SlashModel.inProgress = false
        var timediff = 0
        if (data.getBoolean(Const.TIME, false)) {
            timediff = data.getInt(Const.TIMEDIFF, 0)
            slash.reInitProm(timediff)
            if (timediff < 0) timediff *= -1
        }
        if (data.getBoolean(Const.ADS, false) || timediff > LIMIT_DIFF_SEC
            || data.getBoolean( Const.PAGE,false)
        ) {
            frWelcome = WelcomeFragment().apply {
                arguments = data
            }
            val warn = data.getInt(Const.WARN, -1)
            if (warn > -1) showWarnAds(warn) else isBlinked = true
        }
    }

    private fun showWelcome() {
        frWelcome?.show(supportFragmentManager, null)
    }

    private fun showWarnAds(warn: Int) {
        val ads = DevadsHelper(this)
        val item = ads.getItem(warn)
        ads.close()
        val builder = AlertDialog.Builder(this, R.style.NeoDialog)
            .setTitle(getString(R.string.warning) + " " + item[0])
            .setMessage(item[1])
            .setNegativeButton(getString(android.R.string.ok)) { dialog: DialogInterface, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener { showWelcome() }
        if (item[2] != null) {
            builder.setPositiveButton(getString(R.string.open_link)) { _, _ ->
                Lib.openInApps(item[2], null)
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
}
package ru.neosvet.vestnewage.view.activity

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.navigation.NavigationView
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.databinding.BrowserActivityBinding
import ru.neosvet.vestnewage.helper.BrowserHelper
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.network.ConnectObserver
import ru.neosvet.vestnewage.network.ConnectWatcher
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.PromUtils
import ru.neosvet.vestnewage.utils.UnreadUtils
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.basic.StatusButton
import ru.neosvet.vestnewage.view.basic.Tip
import ru.neosvet.vestnewage.view.browser.NeoInterface
import ru.neosvet.vestnewage.view.browser.WebClient
import ru.neosvet.vestnewage.viewmodel.BrowserToiler
import ru.neosvet.vestnewage.viewmodel.basic.MessageState
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.SuccessPage
import java.util.*

class BrowserActivity : AppCompatActivity(), Observer<NeoState>,
    NavigationView.OnNavigationItemSelectedListener, ConnectObserver {
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
        val themeLight: MenuItem,
        val themeDark: MenuItem,
        val nomenu: MenuItem,
        val buttons: MenuItem,
        val refresh: MenuItem,
        val share: MenuItem
    )

    private var twoPointers = false
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding.content.etSearch)
    }
    private val status = StatusButton()
    private lateinit var prom: PromUtils
    private lateinit var anMin: Animation
    private lateinit var anMax: Animation
    private lateinit var menu: NeoMenu
    private var tvPromTime: View? = null
    private lateinit var tip: Tip
    private val toiler: BrowserToiler by lazy {
        ViewModelProvider(this).get(BrowserToiler::class.java)
    }
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
        setViews()
        restoreState(savedInstanceState)
        toiler.state.observe(this, this)
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
        ConnectWatcher.unSubscribe()
        helper.zoom = (binding.content.wvBrowser.scale * 100.0).toInt()
        helper.save()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putFloat(DataBase.PARAGRAPH, positionOnPage)
        super.onSaveInstanceState(outState)
    }

    private fun restoreState(state: Bundle?) {
        var pos = 0f
        if (state == null) {
            val link = intent.getStringExtra(Const.LINK) ?: return
            toiler.openLink(link, true)
            intent.getStringExtra(Const.SEARCH)?.let {
                helper.setSearchString(it)
            }
        } else {
            toiler.openLink(helper.link, true)
            pos = state.getFloat(DataBase.PARAGRAPH, pos)
        }

        if (helper.isSearch) {
            with(binding.content) {
                etSearch.setText(helper.request)
                if (helper.searchIndex > -1)
                    etSearch.isEnabled = false
                pSearch.isVisible = true
            }
            binding.fabMenu.isVisible = false
        } else if (pos > 0f)
            restorePosition(pos)
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

    private fun restorePosition(pos: Float) {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                with(binding.content.wvBrowser) {
                    post {
                        scrollTo(0, (pos * scale * contentHeight.toFloat()).toInt())
                    }
                }
            }
        }, 500)
    }

    private fun closeSearch() {
        tip.hide()
        with(binding.content) {
            softKeyboard.hide()
            if (helper.searchIndex > -1) {
                etSearch.setText("")
                etSearch.isEnabled = true
            }
            helper.clearSearch()
            pSearch.isVisible = false
            if (helper.isNoMenu.not()) {
                binding.fabMenu.isVisible = true
                binding.fabMenu.startAnimation(anMax)
            }
            wvBrowser.clearMatches()
        }
    }

    private fun findRequest() {
        val s = helper.request
        if (s.contains(Const.N))
            binding.content.wvBrowser.findAllAsync(s.substring(0, s.indexOf(Const.N)))
        else
            binding.content.wvBrowser.findAllAsync(s)
    }

    private fun initViews() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.isVisible = false
        with(binding.navView) {
            setNavigationItemSelectedListener(this@BrowserActivity)
            this@BrowserActivity.menu = NeoMenu(
                refresh = menu.getItem(0),
                share = menu.getItem(1),
                nomenu = menu.getItem(6),
                buttons = menu.getItem(7),
                themeLight = menu.getItem(8),
                themeDark = menu.getItem(9)
            )
        }

        tip = Tip(this, binding.tvFinish)
        status.init(this, binding.pStatus)

        val pref = getSharedPreferences(MainHelper.TAG, MODE_PRIVATE)
        if (pref.getBoolean(Const.COUNT_IN_MENU, true)) {
            val tv = binding.navView.getHeaderView(0).findViewById(R.id.tvPromTimeInMenu) as View
            prom = PromUtils(tv)
        } else {
            tvPromTime = binding.tvPromTime
            prom = PromUtils(tvPromTime)
        }

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        with(binding.content) {
            initTheme()
            etSearch.requestLayout()
            root.requestLayout()
            wvBrowser.settings.builtInZoomControls = true
            wvBrowser.settings.displayZoomControls = false
            wvBrowser.settings.javaScriptEnabled = true
            wvBrowser.settings.allowContentAccess = true
            wvBrowser.settings.allowFileAccess = true
            wvBrowser.addJavascriptInterface(NeoInterface(toiler), "NeoInterface")
            if (helper.zoom > 0)
                wvBrowser.setInitialScale(helper.zoom)
        }

        anMin = AnimationUtils.loadAnimation(this, R.anim.minimize)
        anMin.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                if (!helper.isNoMenu)
                    binding.fabMenu.isVisible = false
                if (prom.isProm)
                    tvPromTime?.isVisible = false
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        anMax = AnimationUtils.loadAnimation(this, R.anim.maximize)
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else if (binding.content.pSearch.isVisible) {
            closeSearch()
        } else if (!toiler.onBackBrowser()) {
            super.onBackPressed()
        }
    }

    private fun setViews() = binding.content.run {
        wvBrowser.webViewClient = WebClient(this@BrowserActivity)
        wvBrowser.setOnScrollChangeListener { _, _, scrollY: Int, _, _ ->
            if (!helper.isNavButtons) return@setOnScrollChangeListener
            with(binding) {
                if (scrollY > 300) {
                    fabTop.isVisible = true
                    fabBottom.isVisible = false
                } else {
                    fabTop.isVisible = false
                    fabBottom.isVisible = true
                }
            }
        }
        wvBrowser.setOnTouchListener { view: View?, event: MotionEvent ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                if (!helper.isNoMenu && pSearch.isVisible.not())
                    binding.fabMenu.startAnimation(anMin)
                if (prom.isProm)
                    tvPromTime?.startAnimation(anMin)
            } else if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                if (!helper.isNoMenu && pSearch.isVisible.not()) {
                    binding.fabMenu.isVisible = true
                    binding.fabMenu.startAnimation(anMax)
                }
                if (prom.isProm) {
                    tvPromTime?.isVisible = true
                    tvPromTime?.startAnimation(anMax)
                }
            }
            if (event.pointerCount == 2) {
                twoPointers = true
            } else if (twoPointers) {
                twoPointers = false
                wvBrowser.setInitialScale((wvBrowser.scale * 100.0).toInt())
            }
            false
        }
        with(binding) {
            fabMenu.setOnClickListener { drawerLayout.openDrawer(Gravity.LEFT) }
            fabTop.setOnClickListener { wvBrowser.scrollTo(0, 0) }
            fabBottom.setOnClickListener {
                wvBrowser.scrollTo(0, (wvBrowser.contentHeight * wvBrowser.scale).toInt())
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
        bNext.setOnClickListener { view: View? ->
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
        val bBack = binding.navView.getHeaderView(0).findViewById(R.id.bBack) as View
        bBack.setOnClickListener { finish() }
        initTheme()
        if (helper.isNoMenu) {
            setCheckItem(menu.nomenu, true)
            binding.fabMenu.isVisible = false
        }
        if (helper.isNavButtons)
            setCheckItem(menu.buttons, true)
        else
            binding.fabBottom.isVisible = false
        status.setClick {
            if (status.isTime)
                toiler.load()
            else status.onClick()
        }
    }

    private fun initTheme() = binding.content.run {
        toiler.lightTheme = helper.isLightTheme
        val context = this@BrowserActivity
        if (helper.isLightTheme) {
            etSearch.setTextColor(ContextCompat.getColor(context, android.R.color.black))
            etSearch.setHintTextColor(ContextCompat.getColor(context, R.color.dark_gray))
            root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
            setCheckItem(menu.themeLight, true)
        } else {
            etSearch.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            etSearch.setHintTextColor(ContextCompat.getColor(context, R.color.light_gray))
            root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            setCheckItem(menu.themeDark, true)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_refresh ->
                toiler.load()
            R.id.nav_share ->
                helper.sharePage(this, getPageTitle())
            R.id.nav_nomenu -> {
                binding.fabMenu.isVisible = helper.isNoMenu
                helper.isNoMenu = helper.isNoMenu.not()
                setCheckItem(item, helper.isNoMenu)
            }
            R.id.nav_buttons -> {
                if (helper.isNavButtons)
                    binding.fabBottom.isVisible = false
                helper.isNavButtons = helper.isNavButtons.not()
                setCheckItem(item, helper.isNavButtons)
                binding.fabTop.isVisible = helper.isNavButtons
            }
            R.id.nav_search -> with(binding.content) {
                if (pSearch.isVisible) closeSearch()
                binding.fabMenu.isVisible = false
                pSearch.isVisible = true
                etSearch.post { etSearch.requestFocus() }
                softKeyboard.show()
            }
            R.id.nav_marker -> {
                val des = if (helper.isSearch)
                    getString(R.string.search_for) + " “" + helper.request + "”"
                else ""
                MarkerActivity.addByPos(this, helper.link, positionOnPage * 100f, des)
            }
            R.id.nav_opt_scale, R.id.nav_src_scale -> {
                helper.zoom = if (item.itemId == R.id.nav_opt_scale) 0 else 100
                helper.save()
                openReader(helper.link, null)
                finish()
                return true
            }
            else -> {
                val id = item.itemId
                if (id == R.id.nav_light && helper.isLightTheme)
                    return true
                if (id == R.id.nav_dark && helper.isLightTheme.not())
                    return true
                helper.isLightTheme = helper.isLightTheme.not()
                setCheckItem(menu.themeLight, helper.isLightTheme)
                setCheckItem(menu.themeDark, helper.isLightTheme.not())
                initTheme()
                binding.content.wvBrowser.clearCache(true)
                toiler.openPage(false)
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun getPageTitle(): String {
        val t = binding.content.wvBrowser.title
        if (t.isNullOrEmpty())
            return getString(R.string.default_title)
        return t
    }

    private fun setCheckItem(item: MenuItem, check: Boolean) {
        item.setIcon(if (check) R.drawable.check_transparent else R.drawable.none)
    }

    fun onPageFinished() {
        val unread = UnreadUtils()
        unread.deleteLink(helper.link)
        toiler.addJournal()
    }

    fun initSearch() {
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

    override fun onChanged(state: NeoState) {
        when (state) {
            NeoState.Loading -> {
                binding.content.wvBrowser.clearCache(true)
                status.setLoad(true)
                status.loadText()
            }
            NeoState.NoConnected -> {
                finishLoading()
                ConnectWatcher.subscribe(this)
                Lib.showToast(getString(R.string.no_connected))
            }
            is SuccessPage -> {
                finishLoading()
                if (state.timeInSeconds > 0)
                    status.checkTime(state.timeInSeconds)
                binding.content.wvBrowser.loadUrl(state.url)
                menu.refresh.isVisible = state.isOtkr.not()
                menu.share.isVisible = state.isOtkr.not()
                restoreSearch()
            }
            is MessageState ->
                Lib.showToast(state.message)
            is NeoState.Error -> {
                finishLoading()
                status.setError(state.throwable.localizedMessage)
            }
        }
    }

    private fun finishLoading() {
        if (status.isVisible)
            status.setLoad(false)
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
}
package ru.neosvet.vestnewage.activity

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.navigation.NavigationView
import ru.neosvet.ui.SoftKeyboard
import ru.neosvet.ui.StatusButton
import ru.neosvet.ui.Tip
import ru.neosvet.utils.Const
import ru.neosvet.utils.DataBase
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.browser.NeoInterface
import ru.neosvet.vestnewage.activity.browser.WebClient
import ru.neosvet.vestnewage.databinding.BrowserActivityBinding
import ru.neosvet.vestnewage.databinding.BrowserContentBinding
import ru.neosvet.vestnewage.helpers.BrowserHelper
import ru.neosvet.vestnewage.helpers.PromHelper
import ru.neosvet.vestnewage.helpers.UnreadHelper
import ru.neosvet.vestnewage.model.BrowserModel
import ru.neosvet.vestnewage.model.basic.MessageState
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.SuccessPage
import java.util.*

class BrowserActivity : AppCompatActivity(), Observer<NeoState>,
    NavigationView.OnNavigationItemSelectedListener {
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
    private lateinit var softKeyboard: SoftKeyboard
    private val status = StatusButton()
    private lateinit var prom: PromHelper
    private lateinit var anMin: Animation
    private lateinit var anMax: Animation
    private lateinit var menu: NeoMenu
    private var tvPromTime: View? = null
    private lateinit var tip: Tip
    private val helper: BrowserHelper
        get() = model.helper!!
    private lateinit var binding: BrowserActivityBinding
    private lateinit var content: BrowserContentBinding
    private val model: BrowserModel by lazy {
        ViewModelProvider(this).get(BrowserModel::class.java)
    }
    private val positionOnPage: Float
        get() = content.wvBrowser.run {
            scrollY.toFloat() / scale / contentHeight.toFloat()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        binding = BrowserActivityBinding.inflate(layoutInflater)
        content = BrowserContentBinding.bind(binding.root.findViewById(R.id.content_browser))
        setContentView(binding.root)
        if (model.helper == null)
            model.init(this)
        initViews()
        setViews()
        restoreState(savedInstanceState)
        model.state.observe(this, this)
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
        helper.zoom = (content.wvBrowser.scale * 100.0).toInt()
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
            model.openLink(link, true)
            intent.getStringExtra(Const.SEARCH)?.let {
                helper.setSearchString(it)
            }
        } else {
            model.openLink(helper.link, true)
            pos = state.getFloat(DataBase.PARAGRAPH, pos)
        }

        if (helper.isSearch) {
            with(content) {
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
            content.wvBrowser.findNext(true)
            i++
        }
        else while (i > prog) {
            content.wvBrowser.findNext(false)
            i--
        }
    }

    private fun restorePosition(pos: Float) {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                with(content.wvBrowser) {
                    post {
                        scrollTo(0, (pos * scale * contentHeight.toFloat()).toInt())
                    }
                }
            }
        }, 500)
    }

    private fun closeSearch() {
        closeKeyboard()
        tip.hide()
        with(content) {
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
            content.wvBrowser.findAllAsync(s.substring(0, s.indexOf(Const.N)))
        else
            content.wvBrowser.findAllAsync(s)
    }

    private fun initViews() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.isVisible = false
        val im = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        softKeyboard = SoftKeyboard(content.root, im)
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

        val pref = getSharedPreferences(MainActivity::class.java.simpleName, MODE_PRIVATE)
        if (pref.getBoolean(Const.COUNT_IN_MENU, true)) {
            val tv = binding.navView.getHeaderView(0).findViewById(R.id.tvPromTimeInMenu) as View
            prom = PromHelper(tv)
        } else {
            tvPromTime = binding.tvPromTime
            prom = PromHelper(tvPromTime)
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
        with(content) {
            initTheme()
            etSearch.requestLayout()
            root.requestLayout()
            wvBrowser.settings.builtInZoomControls = true
            wvBrowser.settings.displayZoomControls = false
            wvBrowser.settings.javaScriptEnabled = true
            wvBrowser.settings.allowContentAccess = true
            wvBrowser.settings.allowFileAccess = true
            wvBrowser.addJavascriptInterface(NeoInterface(model), "NeoInterface")
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
        } else if (content.pSearch.isVisible) {
            closeSearch()
        } else if (!model.onBackBrowser()) {
            super.onBackPressed()
        }
    }

    private fun setViews() {
        content.wvBrowser.webViewClient =
            WebClient(this)
        content.wvBrowser.setOnScrollChangeListener { _, _, scrollY: Int, _, _ ->
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
        content.wvBrowser.setOnTouchListener { view: View?, event: MotionEvent ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                if (!helper.isNoMenu && content.pSearch.isVisible.not())
                    binding.fabMenu.startAnimation(anMin)
                if (prom.isProm)
                    tvPromTime?.startAnimation(anMin)
            } else if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                if (!helper.isNoMenu && content.pSearch.isVisible.not()) {
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
                content.wvBrowser.setInitialScale((content.wvBrowser.scale * 100.0).toInt())
            }
            false
        }
        with(binding) {
            fabMenu.setOnClickListener { drawerLayout.openDrawer(Gravity.LEFT) }
            fabTop.setOnClickListener { content.wvBrowser.scrollTo(0, 0) }
            fabBottom.setOnClickListener {
                with(content.wvBrowser) {
                    scrollTo(0, (contentHeight * scale).toInt())
                }
            }
        }
        content.etSearch.setOnKeyListener { _, keyCode: Int, keyEvent: KeyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == EditorInfo.IME_ACTION_SEARCH
            ) {
                if (content.etSearch.length() > 0) {
                    closeKeyboard()
                    helper.setSearchString(content.etSearch.text.toString())
                    findRequest()
                }
                return@setOnKeyListener true
            }
            false
        }
        content.bPrev.setOnClickListener {
            if (helper.prevSearch()) {
                content.etSearch.setText(helper.request)
                findRequest()
                return@setOnClickListener
            }
            closeKeyboard()
            initSearch()
            helper.downProg()
            content.wvBrowser.findNext(false)
        }
        content.bNext.setOnClickListener { view: View? ->
            if (helper.nextSearch()) {
                content.etSearch.setText(helper.request)
                findRequest()
                return@setOnClickListener
            }
            closeKeyboard()
            initSearch()
            helper.upProg()
            content.wvBrowser.findNext(true)
        }
        content.bClose.setOnClickListener { closeSearch() }
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
                model.load()
            else status.onClick()
        }
    }

    private fun closeKeyboard() {
        softKeyboard.closeSoftKeyboard()
    }

    private fun initTheme() = content.run {
        model.lightTheme = helper.isLightTheme
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
                model.load()
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
            R.id.nav_search -> with(content) {
                if (pSearch.isVisible) closeSearch()
                binding.fabMenu.isVisible = false
                pSearch.isVisible = true
                etSearch.post { etSearch.requestFocus() }
                softKeyboard.openSoftKeyboard()
            }
            R.id.nav_marker -> {
                val marker = Intent(applicationContext, MarkerActivity::class.java)
                marker.putExtra(Const.LINK, helper.link)
                marker.putExtra(Const.PLACE, positionOnPage * 100f)
                if (helper.isSearch) marker.putExtra(
                    Const.DESCTRIPTION, getString(R.string.search_for)
                            + " “" + helper.request + "”"
                )
                startActivity(marker)
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
                content.wvBrowser.clearCache(true)
                model.openPage(false)
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun getPageTitle(): String {
        val t = content.wvBrowser.title
        if (t.isNullOrEmpty())
            return getString(R.string.default_title)
        return t
    }

    private fun setCheckItem(item: MenuItem, check: Boolean) {
        item.setIcon(if (check) R.drawable.check_transparent else R.drawable.none)
    }

    fun onPageFinished() {
        val unread = UnreadHelper()
        unread.deleteLink(helper.link)
        model.addJournal()
    }

    fun initSearch() {
        if (helper.isSearch)
            findRequest()
    }

    fun openLink(url: String) {
        helper.clearSearch()
        model.openLink(url, true)
    }

    fun onBack() {
        model.openPage(false)
    }

    override fun onChanged(state: NeoState) {
        when (state) {
            NeoState.Loading -> {
                content.wvBrowser.clearCache(true)
                status.setLoad(true)
                status.loadText()
            }
            is SuccessPage -> {
                finishLoading()
                if (state.timeInSeconds > 0)
                    status.checkTime(state.timeInSeconds)
                content.wvBrowser.loadUrl(state.url)
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
}
package ru.neosvet.vestnewage.view.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.databinding.BrowserActivityBinding
import ru.neosvet.vestnewage.helper.BrowserHelper
import ru.neosvet.vestnewage.helper.MainHelper
import ru.neosvet.vestnewage.network.ConnectObserver
import ru.neosvet.vestnewage.network.ConnectWatcher
import ru.neosvet.vestnewage.network.NeoClient
import ru.neosvet.vestnewage.utils.*
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

class BrowserActivity : AppCompatActivity(), Observer<NeoState>, ConnectObserver {
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
        val theme: MenuItem,
        val buttons: MenuItem,
        val refresh: MenuItem,
        val share: MenuItem
    )

    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding.content.etSearch)
    }
    private val status = StatusButton()
    private lateinit var prom: PromUtils
    private lateinit var menu: NeoMenu
    private var navIsTop = false
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
        if (savedInstanceState != null) // for logo in topBar
            ScreenUtils.init(this)
        setBars()
        setViews()
        setContent()
        initTheme()
        toiler.state.observe(this, this)
        restoreState(savedInstanceState)
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
        } else if (pos > 0f)
            restorePosition(pos)

        if (ErrorUtils.isNotEmpty()) {
            blocked()
            status.setError(ErrorUtils.getMessage())
        }
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
        tip = Tip(this, binding.tvFinish)
        status.init(this, binding.pStatus)

        val pref = getSharedPreferences(MainHelper.TAG, MODE_PRIVATE)
        prom = if (pref.getBoolean(Const.PROM_FLOAT, false))
            PromUtils(binding.tvPromTimeFloat)
        else
            PromUtils(binding.tvPromTimeHead)
    }

    override fun onBackPressed() {
        when {
            status.isVisible -> {
                ErrorUtils.clear()
                status.setError(null)
                unblocked()
            }
            binding.bottomBar.isScrolledDown ->
                binding.bottomBar.performShow()
            binding.content.pSearch.isVisible ->
                closeSearch()
            toiler.onBackBrowser().not() ->
                super.onBackPressed()
        }
    }

    private fun setViews() = binding.run {
        bBack.setOnClickListener { finish() }
        svBrowser.setOnScrollChangeListener { _, _, scrollY: Int, _, _ ->
            if (!helper.isNavButton) return@setOnScrollChangeListener
            if (scrollY > 300) {
                fabNav.setImageResource(R.drawable.ic_top)
                navIsTop = true
            } else {
                fabNav.setImageResource(R.drawable.ic_bottom)
                navIsTop = false
            }
        }
        fabNav.setOnClickListener {
            if (navIsTop) {
                svBrowser.scrollTo(0, 0)
                topBar.setExpanded(true)
                bottomBar.performShow()
            } else {
                bottomBar.performHide()
                topBar.setExpanded(false)
                svBrowser.scrollTo(0, content.wvBrowser.height)
            }
        }
        if (helper.isNavButton)
            setCheckItem(menu.buttons, true)
        else
            fabNav.isVisible = false
        status.setClick {
            if (toiler.isRun)
                toiler.cancel()
            else {
                unblocked()
                status.onClick()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setContent() = binding.content.run {
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
        wvBrowser.webViewClient = WebClient(this@BrowserActivity)
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
        bNext.setOnClickListener {
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
        etSearch.doAfterTextChanged {
            bClear.isVisible = it?.isNotEmpty() ?: false
        }
        bClear.setOnClickListener { etSearch.setText("") }
    }

    private fun initTheme() = binding.content.run {
        toiler.lightTheme = helper.isLightTheme
        val context = this@BrowserActivity
        if (helper.isLightTheme) {
            etSearch.setTextColor(ContextCompat.getColor(context, android.R.color.black))
            root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
            menu.theme.title = getString(R.string.dark_theme)
        } else {
            etSearch.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            menu.theme.title = getString(R.string.light_theme)
        }
    }

    private fun setBars() = binding.run {
        bottomBar.menu.let {
            menu = NeoMenu(
                refresh = it.getItem(4),
                share = it.getItem(1),
                buttons = it.getItem(0).subMenu.getItem(0),
                theme = it.getItem(0).subMenu.getItem(1),
            )
        }
        ivHead.setOnClickListener {
            if (menu.refresh.isVisible)
                Lib.openInApps(NeoClient.SITE + helper.link, null)
            else
                Lib.openInApps(NeoClient.SITE, null)
        }
        if (ScreenUtils.type == ScreenUtils.Type.PHONE_LAND)
            ivHead.setImageResource(R.drawable.headland)
        else if (ScreenUtils.isTablet)
            ivHead.setImageResource(R.drawable.headtablet)
        svBrowser.post {
            content.root.updateLayoutParams<ViewGroup.LayoutParams> {
                height = svBrowser.height
            }
        }
        bottomBar.setBackgroundResource(R.drawable.panel_bg)
        bottomBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.nav_refresh ->
                    toiler.load()
                R.id.nav_share ->
                    helper.sharePage(this@BrowserActivity, getPageTitle())
                R.id.nav_buttons -> {
                    helper.isNavButton = helper.isNavButton.not()
                    setCheckItem(it, helper.isNavButton)
                    fabNav.isVisible = helper.isNavButton
                }
                R.id.nav_search -> with(content) {
                    topBar.setExpanded(false)
                    if (pSearch.isVisible) closeSearch()
                    pSearch.isVisible = true
                    etSearch.post { etSearch.requestFocus() }
                    softKeyboard.show()
                }
                R.id.nav_marker -> {
                    val des = if (helper.isSearch)
                        getString(R.string.search_for) + " “" + helper.request + "”"
                    else ""
                    MarkerActivity.addByPos(
                        this@BrowserActivity,
                        helper.link,
                        positionOnPage * 100f,
                        des
                    )
                }
                R.id.nav_opt_scale, R.id.nav_src_scale -> {
                    helper.zoom = if (it.itemId == R.id.nav_opt_scale) 0 else 100
                    helper.save()
                    openReader(helper.link, null)
                    finish()
                }
                R.id.nav_theme -> {
                    helper.isLightTheme = helper.isLightTheme.not()
                    initTheme()
                    content.wvBrowser.clearCache(true)
                    toiler.openPage(false)
                }
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun getPageTitle(): String {
        val t = binding.content.wvBrowser.title
        if (t.isNullOrEmpty())
            return getString(R.string.default_title)
        return t
    }

    private fun setCheckItem(item: MenuItem, check: Boolean) {
        item.setIcon(if (check) R.drawable.checkbox_simple else R.drawable.uncheckbox_simple)
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
                blocked()
                binding.content.wvBrowser.clearCache(true)
                status.setLoad(true)
                status.loadText()
            }
            NeoState.NoConnected -> {
                finishLoading()
                ConnectWatcher.subscribe(this)
                ConnectWatcher.showMessage()
            }
            is SuccessPage -> {
                finishLoading()
                binding.content.wvBrowser.loadUrl(state.url)
                menu.refresh.isVisible = state.isOtkr.not()
                menu.share.isVisible = state.isOtkr.not()
                restoreSearch()
            }
            is MessageState ->
                Lib.showToast(state.message)
            is NeoState.Error ->
                status.setError(state.throwable.localizedMessage)
            else -> {}
        }
    }

    private fun finishLoading() {
        if (status.isVisible)
            status.setLoad(false)
        unblocked()
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

    fun blocked() {
        binding.bottomBar.isVisible = false
        binding.fabNav.isVisible = false
    }

    fun unblocked() {
        binding.bottomBar.isVisible = true
        binding.fabNav.isVisible = helper.isNavButton
    }
}
package ru.neosvet.vestnewage.view.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helper.CabinetHelper
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.InsetsUtils
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.basic.StatusButton
import ru.neosvet.vestnewage.view.basic.defIndent
import ru.neosvet.vestnewage.view.browser.CabinetClient
import ru.neosvet.vestnewage.view.dialog.MessageDialog

class CabinetActivity : AppCompatActivity(), CabinetClient.Parent {
    companion object {
        fun openPage(link: String) {
            val intent = Intent(App.context, CabinetActivity::class.java)
            intent.putExtra(Const.LINK, link)
            if (App.context !is Activity) intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            App.context.startActivity(intent)
        }
    }

    private lateinit var wvBrowser: WebView
    private lateinit var status: StatusButton
    private var twoPointers = false
    private val client = CabinetClient(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.cabinet_activity)
        initView()
        val firstUrl = CabinetHelper.codingUrl(Urls.MainSite + intent.getStringExtra(Const.LINK))
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        manager.setAcceptThirdPartyCookies(wvBrowser, true)
        manager.removeAllCookies {
            manager.setCookie(Urls.MainSite, CabinetHelper.cookie)
            if (CabinetHelper.isAlterPath)
                manager.setCookie(Urls.AlterUrl, CabinetHelper.cookie)
            wvBrowser.loadUrl(firstUrl)
        }
    }

    override fun onDestroy() {
        client.cancel()
        wvBrowser.stopLoading()
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun initView() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.visibility = View.VISIBLE
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        this.title = ""
        wvBrowser = findViewById(R.id.wvBrowser)
        wvBrowser.settings.apply {
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            allowContentAccess = true
            allowFileAccess = true
        }
        client.init(wvBrowser)
        wvBrowser.clearCache(true)
        wvBrowser.clearHistory()
        wvBrowser.setOnTouchListener { _, event: MotionEvent ->
            if (event.pointerCount == 2)
                twoPointers = true
            else if (twoPointers) {
                twoPointers = false
                wvBrowser.setInitialScale(client.scale)
            }
            false
        }
        status = StatusButton(this, findViewById(R.id.pStatus))
        if (!ScreenUtils.isTabletLand && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            initInsetsUtils(toolbar)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initInsetsUtils(bar: View) {
        val utils = InsetsUtils(bar, this)
        utils.applyInsets = { insets ->
            val m = insets.top - baseContext.defIndent
            bar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = m
            }
            wvBrowser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin += m
            }

            if (utils.isSideNavBar) {
                bar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = insets.left
                    rightMargin = insets.right
                }
                wvBrowser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = insets.left
                    rightMargin = insets.right
                }
            }
            findViewById<View>(R.id.pStatus).updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin += insets.left
                rightMargin += insets.right
                bottomMargin += insets.bottom
            }
            true
        }
        utils.init(window)
    }

    override fun startLoad() {
        wvBrowser.isVisible = false
        status.setLoad(true)
        title = ""
    }

    override fun finishLoad(title: String?) {
        status.setLoad(false)
        if (title == null) {
            val alert = MessageDialog(this)
            alert.setTitle(getString(R.string.error))
            alert.setMessage(getString(R.string.cab_fail))
            alert.setRightButton(getString(android.R.string.ok)) { alert.dismiss() }
            alert.show(null)
        } else {
            wvBrowser.isVisible = true
            this.title = if (title.contains(":"))
                title.substring(title.indexOf(":") + 3)
            else title
        }
    }
}
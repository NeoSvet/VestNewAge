package ru.neosvet.vestnewage.view.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import okhttp3.Request
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helper.CabinetHelper
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.basic.StatusButton
import ru.neosvet.vestnewage.view.dialog.MessageDialog

class CabinetActivity : AppCompatActivity() {
    companion object {
        private const val SCRIPT =
            "var id=setInterval(';',1); for(var i=0;i<id;i++) window.clearInterval(i); var s=document.getElementById('rcol').innerHTML;s=s.substring(s.indexOf('/d')+5);" +
                    "s=s.substring(0,s.indexOf('hr2')-12); document.body.innerHTML='<style>#rcol A:link{color:#fff; text-decoration:underline;}</style><div id=\"rcol\" style=\"padding-top:10px\" name=\"top\">'+s+'</div>';"

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
    private var currentScale = 1f
    private var firstUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.cabinet_activity)
        initView()
        status.setLoad(true)
        firstUrl = CabinetHelper.codingUrl(Urls.MainSite + intent.getStringExtra(Const.LINK))
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        manager.setAcceptThirdPartyCookies(wvBrowser, true)
        manager.removeAllCookies {
            manager.setCookie(Urls.MainSite, CabinetHelper.cookie)
            if (CabinetHelper.alterUrl.isNotEmpty()) {
                manager.setCookie(CabinetHelper.alterUrl, CabinetHelper.alterCookie)
                manager.setCookie(CabinetHelper.alterUrl, CabinetHelper.cookie)
            }
            wvBrowser.loadUrl(firstUrl)
        }
    }

    override fun onDestroy() {
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
        wvBrowser.webViewClient = CabinetClient()
        wvBrowser.clearCache(true)
        wvBrowser.clearHistory()
        wvBrowser.setOnTouchListener { _, event: MotionEvent ->
            if (event.pointerCount == 2)
                twoPointers = true
            else if (twoPointers) {
                twoPointers = false
                wvBrowser.setInitialScale((currentScale * 100.0).toInt())
            }
            false
        }
        status = StatusButton(this, findViewById(R.id.pStatus))
    }

    private inner class CabinetClient : WebViewClient() {
        private val act: Activity
            get() = this@CabinetActivity

        override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
            super.onScaleChanged(view, oldScale, newScale)
            currentScale = newScale
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            return if (CabinetHelper.alterUrl.isEmpty())
                super.shouldInterceptRequest(view, request)
            else try {
                val url = request.url.toString()
                val req = Request.Builder()
                    .url(if (url.contains(Urls.MainSite)) CabinetHelper.codingUrl(url) else url)
                    .addHeader(NetConst.USER_AGENT, App.context.packageName)
                    .addHeader(NetConst.COOKIE, CabinetHelper.alterCookie)
                    .addHeader(NetConst.COOKIE, CabinetHelper.cookie)
                    .build()
                val client = CabinetHelper.createHttpClient()
                val response = client.newCall(req).execute()
                val responseInputStream = response.body.byteStream()
                WebResourceResponse(null, null, responseInputStream)
            } catch (e: Exception) {
                super.shouldInterceptRequest(view, request)
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (url.contains("#")) return
            status.setLoad(true)
            act.title = ""
            view.isVisible = false
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (url.contains("#") || act.isDestroyed) return
            if (!url.contains(Urls.MainSite)) {
                if (url.contains("mailto")) {
                    Urls.openInApps(url)
                    wvBrowser.loadUrl(firstUrl)
                    return
                }
                if (CabinetHelper.alterUrl.isEmpty() || !url.contains(CabinetHelper.alterUrl)) {
                    wvBrowser.loadUrl(firstUrl)
                    return
                }
            }
            if (url.contains(".html")) {
                wvBrowser.title?.let { t ->
                    if (!t.contains(":")) {
                        status.setLoad(false)
                        val alert = MessageDialog(act)
                        alert.setTitle(getString(R.string.error))
                        alert.setMessage(getString(R.string.cab_fail))
                        alert.setRightButton(getString(android.R.string.ok)) { alert.dismiss() }
                        alert.show(null)
                        return
                    }
                    wvBrowser.evaluateJavascript(SCRIPT) {
                        status.setLoad(false)
                        wvBrowser.isVisible = true
                    }
                    act.title = t.substring(t.indexOf(":") + 3)
                }
            }
            super.onPageFinished(view, url)
        }
    }
}
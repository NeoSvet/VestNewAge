package ru.neosvet.vestnewage.view.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Request
import okhttp3.internal.charset
import org.intellij.lang.annotations.Language
import ru.neosvet.vestnewage.helper.CabinetHelper
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.network.Urls

class CabinetClient(
    private val parent: Parent
) : WebViewClient() {
    companion object {
        @Language("JS")
        private const val SCRIPT = """
           var id=setInterval(';',1); for(var i=0;i<id;i++) window.clearInterval(i);
           var s=document.getElementById('rcol').innerHTML;
           s=s.substring(s.indexOf('/d')+5); s=s.substring(0,s.indexOf('hr2')-12);
           document.body.innerHTML='<style>#rcol A:link{color:#fff; text-decoration:underline;}</style>'
           +'<div id="rcol" style="padding-top:10px" name="top">'+s+'</div>';
"""
    }

    interface Parent {
        fun startLoad()
        fun finishLoad(title: String?)
    }

    var scale: Int = 100
        private set
    private var firstUrl = ""
    private val inspector = RequestInspector()
    private var isCanceled = false

    fun init(view: WebView) {
        view.webViewClient = this
        view.addJavascriptInterface(inspector, RequestInspector.INTERFACE_NAME)
    }

    fun cancel() {
        isCanceled = true
    }

    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
        super.onScaleChanged(view, oldScale, newScale)
        scale = (newScale * 100.0).toInt()
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (isCanceled) return null
        if (CabinetHelper.isAlterPath) try {
            val url = request.url.toString()
            val builder = Request.Builder()
                .url(if (url.contains(Urls.MainSite)) CabinetHelper.codingUrl(url) else url)
                .addHeader(NetConst.COOKIE, CabinetHelper.cookie)
            request.requestHeaders.forEach {
                if (it.key != NetConst.COOKIE)
                    builder.addHeader(it.key, it.value)
            }
            if (request.method == "POST") inspector.findRecordedRequestForUrl(url)?.let {
                builder.method(request.method, it.body)
            }
            val client = CabinetHelper.createHttpClient()
            val response = client.newCall(builder.build()).execute()
            var type = response.body.contentType().toString()
            if (type.contains(";"))
                type = type.substring(0, type.indexOf(";"))
            val charset = if (type.startsWith("text/"))
                response.body.contentType().charset().name()
            else null
            val result = WebResourceResponse(
                type, //mimeType
                charset, //encoding
                response.body.byteStream() //data
            )
            result.setStatusCodeAndReasonPhrase(response.code, response.message)
            return result
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        isCanceled = false
        if (url.contains("#")) return
        if (CabinetHelper.isAlterPath)
            RequestInspector.enabledRequestInspection(view)
        parent.startLoad()
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (isCanceled || url.contains("#")) return
        if (!url.contains(Urls.MainSite)) {
            if (url.contains("mailto")) {
                Urls.openInApps(url)
                view.loadUrl(firstUrl)
                return
            }
            if (!url.contains(CabinetHelper.ALTER_URL)) {
                view.loadUrl(firstUrl)
                return
            }
        }
        if (view.title?.contains(":") == true) {
            view.evaluateJavascript(SCRIPT, null)
            parent.finishLoad(view.title)
        } else parent.finishLoad(null) //error
        super.onPageFinished(view, url)
    }
}
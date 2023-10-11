package ru.neosvet.vestnewage.view.browser

import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import ru.neosvet.vestnewage.network.Urls.openInApps
import ru.neosvet.vestnewage.network.Urls.openInBrowser

class WebClient(
    private val parent: Parent,
    private val packageName: String
) : WebViewClient() {

    interface Parent {
        fun openLink(link: String)
        fun onBack()
        fun onPageFinished(isLocal: Boolean)
        fun setScale(scale: Float)
    }

    companion object {
        private const val FILES = "file"
    }

    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
        super.onScaleChanged(view, oldScale, newScale)
        parent.setScale(newScale)
    }

    private fun getLink(url: String) =
        if (url.contains(packageName)) // страница во внутренем хранилище
            url.substring(url.indexOf("age") + 10)
        else url.substring(url.indexOf(FILES) + 8)

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
//for API 23, shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) from 24+
        view.visibility = View.GONE
        if (url.contains(FILES)) {
            parent.openLink(getLink(url))
            return true
        }
        if (url.contains("http")) {
            parent.onBack()
            openInBrowser(url)
        } else if (url.contains("mailto")) {
            parent.onBack()
            openInApps(url)
        } else parent.openLink(url)
        return true
    }

    override fun onPageFinished(view: WebView, url: String) {
        view.visibility = View.VISIBLE
        parent.onPageFinished(url.contains(FILES))
    }
}
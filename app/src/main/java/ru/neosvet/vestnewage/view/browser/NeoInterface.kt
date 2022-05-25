package ru.neosvet.vestnewage.view.browser

import android.webkit.JavascriptInterface
import ru.neosvet.vestnewage.viewmodel.BrowserToiler

class NeoInterface(private val browser: BrowserToiler) {
    @JavascriptInterface
    fun NextPage() {
        browser.nextPage()
    }

    @JavascriptInterface
    fun PrevPage() {
        browser.prevPage()
    }
}
package ru.neosvet.vestnewage.view.browser

import android.webkit.JavascriptInterface
import ru.neosvet.vestnewage.model.BrowserModel

class NeoInterface(private val browser: BrowserModel) {
    @JavascriptInterface
    fun NextPage() {
        browser.nextPage()
    }

    @JavascriptInterface
    fun PrevPage() {
        browser.prevPage()
    }
}
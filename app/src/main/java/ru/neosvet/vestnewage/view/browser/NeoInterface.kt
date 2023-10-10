package ru.neosvet.vestnewage.view.browser

import android.webkit.JavascriptInterface

class NeoInterface(private val parent: Parent) {

    interface Parent {
        fun changePage(next: Boolean)
    }
    @JavascriptInterface
    fun NextPage() {
        parent.changePage(true)
    }

    @JavascriptInterface
    fun PrevPage() {
        parent.changePage(false)
    }
}
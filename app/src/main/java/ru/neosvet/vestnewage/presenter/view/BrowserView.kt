package ru.neosvet.vestnewage.presenter.view

interface BrowserView {
    fun openPage(url: String)
    fun startLoading()
    fun endLoading()
    fun tipEndList()
    fun isOtrkSite()
    fun checkTime(timeInSeconds: Long)
    fun onError(throwable: Throwable)
}
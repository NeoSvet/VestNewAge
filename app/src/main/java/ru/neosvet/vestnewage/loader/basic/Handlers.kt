package ru.neosvet.vestnewage.loader.basic

interface LoadHandler {
    fun setMax(value: Int)
    fun upProg()
    fun postMessage(value: String)
}

interface LoadHandlerLite {
    fun postPercent(value: Int)
}
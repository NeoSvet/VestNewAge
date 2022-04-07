package ru.neosvet.vestnewage.model.state

sealed class BrowserState {
    object Loading : BrowserState()
    object EndList : BrowserState()
    data class Page(
        val url: String,
        val timeInSeconds: Long,
        val isOtkr: Boolean = false
    ) : BrowserState()

    data class Error(val throwable: Throwable) : BrowserState()
}

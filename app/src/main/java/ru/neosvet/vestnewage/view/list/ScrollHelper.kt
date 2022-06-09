package ru.neosvet.vestnewage.view.list

import androidx.recyclerview.widget.RecyclerView

class ScrollHelper(
    val events: (Events) -> Unit
) {
    private var view: RecyclerView? = null

    enum class Events {
        SCROLL_END, SCROLL_START
    }

    fun attach(rv: RecyclerView) {
        view = rv
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(view, dx, dy)
                val value = view.computeVerticalScrollOffset() + view.computeVerticalScrollExtent()
                if (value < view.height) return
                val max = view.computeVerticalScrollRange()
                if (value >= max)
                    events.invoke(Events.SCROLL_END)
                else
                    events.invoke(Events.SCROLL_START)
            }
        })
    }

    fun deAttach() {
        view?.clearOnScrollListeners()
        view = null
    }
}
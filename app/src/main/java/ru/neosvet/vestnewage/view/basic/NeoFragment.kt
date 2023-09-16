package ru.neosvet.vestnewage.view.basic

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.list.ScrollHelper
import ru.neosvet.vestnewage.view.list.TouchHelper
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

abstract class NeoFragment : Fragment() {
    @JvmField
    protected var act: MainActivity? = null
    protected val neotoiler: NeoToiler by lazy {
        initViewModel()
    }
    private var scroll: ScrollHelper? = null
    private var root: View? = null
    private var connectWatcher: Job? = null
    protected var isBlocked = false

    abstract val title: String

    abstract fun initViewModel(): NeoToiler

    abstract fun onViewCreated(savedInstanceState: Bundle?)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        root = view
        onViewCreated(savedInstanceState)
        runObserve()
    }

    private fun runObserve() {
        lifecycleScope.launch {
            neotoiler.state.collect {
                onChangedState(it)
            }
        }
        neotoiler.start(requireContext())
    }

    fun disableUpdateRoot() {
        root = null
    }

    fun updateRoot(newHeight: Int) {
        root?.updateLayoutParams<ViewGroup.LayoutParams> {
            height = newHeight
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        act = (activity as MainActivity).apply {
            setFragment(this@NeoFragment)
            title = this@NeoFragment.title
        }
    }

    override fun onDestroyView() {
        scroll?.deAttach()
        act = null
        super.onDestroyView()
    }

    open fun onBackPressed(): Boolean {
        if (isBlocked) {
            onStatusClick()
            return false
        }
        return true
    }

    open fun onAction(title: String) {}

    protected abstract fun onChangedOtherState(state: NeoState)

    private fun onChangedState(state: NeoState) {
        if (isDetached) return
        when (state) {
            is BasicState.Progress ->
                act?.status?.setProgress(state.percent)

            BasicState.Loading ->
                setStatus(true)

            BasicState.NoConnected ->
                noConnected()

            is BasicState.Error -> {
                setStatus(false)
                act?.setError(state)
            }

            else -> onChangedOtherState(state)
        }
    }

    open fun startLoad() {
        if (isBlocked) return
        if (OnlineObserver.isOnline.value.not()) {
            noConnected()
            return
        }
        neotoiler.load()
    }

    private fun noConnected() {
        connectWatcher = lifecycleScope.launch {
            OnlineObserver.isOnline.collect {
                connectChanged(it)
            }
        }
        if (OnlineObserver.needShowMessage())
            act?.showToast(getString(R.string.no_connected))
        setStatus(false)
    }

    private fun connectChanged(connected: Boolean) {
        if (connected) {
            act?.runOnUiThread {
                setStatus(true)
                neotoiler.load()
            }
            connectWatcher?.cancel()
        }
    }

    open fun setStatus(load: Boolean) {
        isBlocked = load
        act?.run {
            if (load) {
                blocked()
                status.loadText()
                status.setLoad(true)
            } else if (status.isCrash.not()) {
                if (status.isVisible)
                    status.setLoad(false)
                unblocked()
            }
        }
    }

    fun onStatusClick() {
        if (isBlocked) {
            neotoiler.cancel()
            setStatus(false)
        }
    }

    protected fun setListEvents(list: RecyclerView, onlyLimit: Boolean = true) {
        scroll = ScrollHelper {
            when (it) {
                ScrollHelper.Events.SCROLL_END ->
                    act?.hideBottomArea()

                ScrollHelper.Events.SCROLL_START ->
                    act?.showBottomArea()
            }
        }.apply { attach(list) }
        val touch = TouchHelper(onlyLimit) {
            when (it) {
                TouchHelper.Events.LIST_LIMIT ->
                    act?.hideBottomArea()

                TouchHelper.Events.SWIPE_LEFT ->
                    swipeLeft()

                TouchHelper.Events.SWIPE_RIGHT ->
                    swipeRight()
            }
        }
        touch.attach(list)
    }

    open fun swipeLeft() {}

    open fun swipeRight() {}

    fun resetError() {
        neotoiler.clearStates()
    }

    protected fun setUpdateTime(time: Long, tv: TextView) {
        if (time == 0L) {
            tv.text = ""
            return
        }
        val diff = DateUnit.getDiffDate(System.currentTimeMillis(), time)
        val s = getString(R.string.loaded) + diff + getString(R.string.back)
        tv.text = s
    }
}
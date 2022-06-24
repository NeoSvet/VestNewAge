package ru.neosvet.vestnewage.view.basic

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.network.ConnectObserver
import ru.neosvet.vestnewage.network.ConnectWatcher
import ru.neosvet.vestnewage.utils.StateUtils
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.list.ScrollHelper
import ru.neosvet.vestnewage.view.list.TouchHelper
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler

abstract class NeoFragment : Fragment(), ConnectObserver, StateUtils.Host {
    @JvmField
    protected var act: MainActivity? = null
    protected val neotoiler: NeoToiler by lazy {
        initViewModel()
    }
    private var scroll: ScrollHelper? = null
    private var root: View? = null
    private val stateUtils: StateUtils by lazy {
        StateUtils(this, neotoiler)
    }

    override val scope: LifecycleCoroutineScope
        get() = lifecycleScope

    abstract val title: String

    abstract fun initViewModel(): NeoToiler

    abstract fun onViewCreated(savedInstanceState: Bundle?)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        root = view
        stateUtils.runObserve()
        onViewCreated(savedInstanceState)
        if (neotoiler.isRun) setStatus(true)
        stateUtils.restore()
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
        act = activity as MainActivity
        act?.setFragment(this)
        act?.title = title
    }

    override fun onDestroyView() {
        ConnectWatcher.unSubscribe()
        scroll?.deAttach()
        act = null
        super.onDestroyView()
    }

    open fun onBackPressed(): Boolean {
        if (neotoiler.isRun) {
            onStatusClick()
            return false
        }
        return true
    }

    open fun onAction(title: String) {}

    abstract fun onChangedOtherState(state: NeoState)

    override fun onChangedState(state: NeoState) {
        when (state) {
            is NeoState.None ->
                return
            is NeoState.Progress ->
                act?.status?.setProgress(state.percent)
            NeoState.Loading ->
                setStatus(true)
            NeoState.NoConnected ->
                noConnected()
            is NeoState.Error -> {
                act?.setError(state.throwable.localizedMessage)
                setStatus(false)
            }
            else ->
                onChangedOtherState(state)
        }
    }

    open fun startLoad() {
        if (neotoiler.isRun) return
        if (ConnectWatcher.connected.not()) {
            noConnected()
            return
        }
        neotoiler.load()
    }

    private fun noConnected() {
        ConnectWatcher.subscribe(this)
        ConnectWatcher.showMessage()
        setStatus(false)
    }

    override fun connectChanged(connected: Boolean) {
        if (connected) {
            act?.runOnUiThread {
                setStatus(true)
                neotoiler.load()
            }
            ConnectWatcher.unSubscribe()
        }
    }

    open fun setStatus(load: Boolean) {
        act?.run {
            if (load) {
                act?.blocked()
                status.loadText()
                status.setLoad(true)
            } else if (status.isCrash.not()) {
                if (status.isVisible)
                    status.setLoad(false)
                act?.unblocked()
            }
        }
    }

    fun onStatusClick() {
        if (neotoiler.isRun) {
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
        neotoiler.clearSecondaryStates()
    }
}
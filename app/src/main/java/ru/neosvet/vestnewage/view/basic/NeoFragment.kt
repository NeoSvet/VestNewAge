package ru.neosvet.vestnewage.view.basic

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.network.ConnectObserver
import ru.neosvet.vestnewage.network.ConnectWatcher
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.list.ScrollHelper
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.ProgressState

abstract class NeoFragment : Fragment(), Observer<NeoState>, ConnectObserver {
    @JvmField
    protected var act: MainActivity? = null
    protected val neotoiler: NeoToiler by lazy {
        initViewModel()
    }
    private var jobShow: Job? = null

    abstract val title: String

    abstract fun initViewModel(): NeoToiler

    abstract fun onViewCreated(savedInstanceState: Bundle?)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        act?.status?.setClick { onStatusClick(false) }
        onViewCreated(savedInstanceState)
        act?.let {
            neotoiler.state.observe(it, this)
        }
        if (neotoiler.isRun) setStatus(true)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        act = activity as MainActivity
        act?.setFragment(this)
        act?.title = title
    }

    override fun onDestroyView() {
        ConnectWatcher.unSubscribe()
        act = null
        super.onDestroyView()
    }

    open fun onBackPressed(): Boolean {
        if (act?.status?.isCrash == true || neotoiler.isRun) {
            onStatusClick(true)
            return false
        }
        return true
    }

    abstract fun onChangedState(state: NeoState)

    override fun onChanged(state: NeoState) {
        when (state) {
            is ProgressState ->
                act?.status?.setProgress(state.percent)
            NeoState.Loading ->
                setStatus(true)
            NeoState.NoConnected ->
                noConnected()
            is NeoState.Error -> {
                act?.status?.setError(state.throwable.localizedMessage)
                setStatus(false)
            }
            else ->
                onChangedState(state)
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
        Lib.showToast(getString(R.string.no_connected))
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
                status.loadText()
                status.setLoad(true)
            } else {
                if (status.isVisible && status.isCrash.not())
                    status.setLoad(false)
            }
        }
    }

    open fun onStatusClick(reset: Boolean) {
        if (neotoiler.isRun) {
            neotoiler.cancel()
            setStatus(false)
            return
        }
        if (reset) {
            act?.status?.setError(null)
            setStatus(false)
            return
        }
        act?.run {
            if (!status.onClick() && status.isTime)
                startLoad()
        }
    }

    protected val animMinFinished: Boolean
        get() = act?.status?.startMin() == false

    protected val animMaxFinished: Boolean
        get() = act?.status?.startMax() == false

    protected fun setButtonsHider(rv: RecyclerView) {
        val scroll = ScrollHelper {
            when (it) {
                ScrollHelper.Events.SCROLL_END ->
                    hideButtons()
                ScrollHelper.Events.SCROLL_START ->
                    showButtons()
            }
        }
        scroll.attach(rv)
    }

    private fun showButtons() {
        if (animMaxFinished)
            act?.startShowButtons()
    }

    private fun hideButtons() {
        if (animMinFinished) {
            act?.startHideButtons()
            jobShow?.cancel()
            jobShow = lifecycleScope.launch {
                delay(1000)
                showButtons()
            }
        }
    }
}
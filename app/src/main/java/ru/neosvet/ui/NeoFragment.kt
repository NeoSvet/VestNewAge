package ru.neosvet.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.work.Data
import ru.neosvet.vestnewage.activity.MainActivity
import ru.neosvet.vestnewage.helpers.ProgressHelper
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.ProgressState

abstract class NeoFragment : Fragment(), Observer<NeoState> {
    @JvmField
    protected var act: MainActivity? = null
    protected val neomodel: NeoViewModel by lazy {
        initViewModel()
    }

    abstract fun initViewModel(): NeoViewModel

    abstract fun onViewCreated(savedInstanceState: Bundle?)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        act?.status?.setClick { onStatusClick(false) }
        onViewCreated(savedInstanceState)
        act?.let {
            neomodel.state.observe(it, this)
        }
        if (neomodel.isRun) setStatus(true)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        act = activity as MainActivity
        act?.setFragment(this)
    }

    override fun onDestroyView() {
        act = null
        super.onDestroyView()
    }

    open fun onBackPressed(): Boolean {
        if (act?.status?.isCrash == true || neomodel.isRun) {
            onStatusClick(true)
            return false
        }
        if (ProgressHelper.isBusy()) {
            neomodel.cancel()
            ProgressHelper.cancelled()
            return false
        }
        return true
    }

    open fun onChanged(data: Data) {}

    abstract fun onChangedState(state: NeoState)

    override fun onChanged(state: NeoState) {
        when (state) {
            is ProgressState ->
                act?.status?.setProgress(state.percent)
            NeoState.Loading ->
                setStatus(true)
            is NeoState.Error -> {
                setStatus(false)
                act?.status?.setError(state.throwable.localizedMessage)
            }
            else ->
                onChangedState(state)
        }
    }

    open fun startLoad() {
        if (neomodel.isRun) return
        neomodel.load()
    }

    open fun setStatus(load: Boolean) {
        act?.run {
            if (load) {
                ProgressHelper.setBusy(true)
                status.loadText()
                status.setLoad(true)
            } else {
                ProgressHelper.setBusy(false)
                if (status.isVisible)
                    status.setLoad(false)
            }
        }
    }

    open fun onStatusClick(reset: Boolean) {
        if (neomodel.isRun) {
            neomodel.cancel()
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
}
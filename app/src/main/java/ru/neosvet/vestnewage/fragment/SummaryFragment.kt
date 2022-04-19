package ru.neosvet.vestnewage.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import ru.neosvet.ui.NeoFragment
import ru.neosvet.utils.Const
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.databinding.SummaryFragmentBinding
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.ProgressHelper
import ru.neosvet.vestnewage.list.ListAdapter
import ru.neosvet.vestnewage.model.SummaryModel
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.ProgressState
import ru.neosvet.vestnewage.model.basic.SuccessList

class SummaryFragment : NeoFragment(), Observer<NeoState> {
    private var binding: SummaryFragmentBinding? = null
    private lateinit var adSummary: ListAdapter
    private val model: SummaryModel by lazy {
        ViewModelProvider(this).get(SummaryModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = SummaryFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        act.title = getString(R.string.rss)
        model.init(requireContext())
        setViews()
        restoreState(savedInstanceState)
        model.state.observe(act, this)
        if (model.isRun) setStatus(true)
    }

    override fun setStatus(load: Boolean) {
        if (load) {
            ProgressHelper.setBusy(true)
            act.status.setLoad(true)
            binding?.fabRefresh?.isVisible = false
        } else {
            ProgressHelper.setBusy(false)
            act.status.setLoad(false)
            binding?.fabRefresh?.isVisible = true
        }
    }

    private fun restoreState(state: Bundle?) {
        if (state != null) {
            model.openList(false)
            return
        }
        val f = Lib.getFile(Const.RSS)
        if (f.exists()) {
            binding?.fabRefresh?.isVisible =
                !act.status.checkTime(f.lastModified() / DateHelper.SEC_IN_MILLS)
            model.openList(true)
        } else
            startLoad()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        adSummary = ListAdapter(act)
        lvSummary.adapter = adSummary
        act.fab = fabRefresh
        fabRefresh.setOnClickListener { startLoad() }
        act.status.setClick { onStatusClick(false) }
        lvSummary.onItemClickListener = OnItemClickListener { _, _, pos: Int, _ ->
            if (act.checkBusy()) return@OnItemClickListener
            openReader(adSummary.getItem(pos).link, null)
        }
        lvSummary.setOnTouchListener { _, motionEvent: MotionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                if (!act.status.startMin()) act.startAnimMin()
            } else if (motionEvent.action == MotionEvent.ACTION_UP
                || motionEvent.action == MotionEvent.ACTION_CANCEL
            ) {
                if (!act.status.startMax()) act.startAnimMax()
            }
            false
        }
    }

    override fun onStatusClick(reset: Boolean) {
        if (model.isRun) {
            model.cancel()
            return
        }
        if (reset) {
            act.status.setError(null)
            return
        }
        if (!act.status.onClick() && act.status.isTime)
            startLoad()
    }

    override fun startLoad() {
        if (model.isRun) return
        model.load()
    }

    override fun onChanged(state: NeoState) {
        when (state) {
            is ProgressState ->
                act.status.setProgress(state.percent)
            NeoState.Loading ->
                setStatus(true)
            is SuccessList -> {
                setStatus(false)
                adSummary.setItems(state.list)
                binding?.lvSummary?.smoothScrollToPosition(0)
                act.updateNew()
            }
            is NeoState.Error ->
                act.status.setError(state.throwable.localizedMessage)
        }
    }
}
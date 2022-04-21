package ru.neosvet.vestnewage.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import ru.neosvet.ui.NeoFragment
import ru.neosvet.utils.Const
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.databinding.SummaryFragmentBinding
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.list.ListAdapter
import ru.neosvet.vestnewage.model.SummaryModel
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.SuccessList

class SummaryFragment : NeoFragment() {
    private var binding: SummaryFragmentBinding? = null
    private val adSummary: ListAdapter by lazy {
        ListAdapter(requireContext())
    }
    private val model: SummaryModel
        get() = neomodel as SummaryModel
    override val title: String
        get() = getString(R.string.rss)

    override fun initViewModel(): NeoViewModel =
        ViewModelProvider(this).get(SummaryModel::class.java).apply { init(requireContext()) }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = SummaryFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(savedInstanceState: Bundle?) {
        setViews()
        restoreState(savedInstanceState)
    }

    override fun setStatus(load: Boolean) {
        super.setStatus(load)
        binding?.fabRefresh?.isVisible = !load
    }

    private fun restoreState(state: Bundle?) {
        if (state != null) {
            model.openList(false)
            return
        }
        val f = Lib.getFile(Const.RSS)
        if (f.exists()) {
            act?.run {
                val time = f.lastModified() / DateHelper.SEC_IN_MILLS
                binding?.fabRefresh?.isVisible = !status.checkTime(time)
            }
            model.openList(true)
        } else
            startLoad()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setViews() = binding?.run {
        lvSummary.adapter = adSummary
        act?.fab = fabRefresh
        fabRefresh.setOnClickListener { startLoad() }
        lvSummary.onItemClickListener = OnItemClickListener { _, _, pos: Int, _ ->
            if (model.isRun) return@OnItemClickListener
            openReader(adSummary.getItem(pos).link, null)
        }
        lvSummary.setOnTouchListener { _, motionEvent: MotionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                if (animMinFinished) act?.startAnimMin()
            } else if (motionEvent.action == MotionEvent.ACTION_UP
                || motionEvent.action == MotionEvent.ACTION_CANCEL
            ) {
                if (animMaxFinished) act?.startAnimMax()
            }
            false
        }
    }

    override fun onChangedState(state: NeoState) {
        if (state is SuccessList) {
            setStatus(false)
            adSummary.setItems(state.list)
            binding?.lvSummary?.smoothScrollToPosition(0)
            act?.updateNew()
        }
    }
}
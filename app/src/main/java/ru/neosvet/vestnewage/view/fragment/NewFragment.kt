package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.databinding.NewFragmentBinding
import ru.neosvet.vestnewage.viewmodel.NewToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.Ready
import ru.neosvet.vestnewage.viewmodel.basic.SuccessList
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.list.ListAdapter

class NewFragment : NeoFragment() {
    private var binding: NewFragmentBinding? = null
    private val adNew: ListAdapter by lazy {
        ListAdapter(requireContext())
    }
    private val toiler: NewToiler
        get() = neotoiler as NewToiler
    override val title: String
        get() = getString(R.string.new_section)
    private var openedReader = false

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(NewToiler::class.java).apply { init(requireActivity()) }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = NewFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(savedInstanceState: Bundle?) {
        initView()
        if (savedInstanceState == null)
            toiler.openList()
    }

    override fun onResume() {
        super.onResume()
        if (openedReader) {
            openedReader = false
            toiler.openList()
        }
    }

    override fun onChangedState(state: NeoState) {
        when (state) {
            is Ready ->
                emptyList()
            is SuccessList -> {
                if (state.list.isEmpty())
                    emptyList()
                else
                    adNew.setItems(state.list)
            }
        }
        act?.updateNew()
    }

    private fun emptyList() {
        adNew.clear()
        adNew.notifyDataSetChanged()
        binding?.run {
            tvEmptyNew.isVisible = true
            fabClear.isVisible = false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView() = binding?.run {
        act?.fab = fabClear
        fabClear.setOnClickListener {
            toiler.clearList()
        }

        lvNew.adapter = adNew
        lvNew.onItemClickListener = OnItemClickListener { _, _, pos: Int, _ ->
            val item = adNew.getItem(pos)
            if (item.title.contains(getString(R.string.ad))) {
                toiler.openAd(item, pos)
            } else if (item.link != "") {
                toiler.needOpen = true
                openedReader = true
                openReader(item.link, null)
            }
        }
        lvNew.setOnTouchListener { _, motionEvent: MotionEvent ->
            if (adNew.count == 0) return@setOnTouchListener false
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
}
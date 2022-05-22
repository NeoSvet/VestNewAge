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
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.databinding.NewFragmentBinding
import ru.neosvet.vestnewage.list.ListAdapter
import ru.neosvet.vestnewage.model.NewModel
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.Ready
import ru.neosvet.vestnewage.model.basic.SuccessList

class NewFragment : NeoFragment() {
    private var binding: NewFragmentBinding? = null
    private val adNew: ListAdapter by lazy {
        ListAdapter(requireContext())
    }
    private val model: NewModel
        get() = neomodel as NewModel
    override val title: String
        get() = getString(R.string.new_section)
    private var openedReader = false

    override fun initViewModel(): NeoViewModel =
        ViewModelProvider(this).get(NewModel::class.java).apply { init(requireActivity()) }

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
            model.openList()
    }

    override fun onResume() {
        super.onResume()
        if (openedReader) {
            openedReader = false
            model.openList()
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
            model.clearList()
        }

        lvNew.adapter = adNew
        lvNew.onItemClickListener = OnItemClickListener { _, _, pos: Int, _ ->
            val item = adNew.getItem(pos)
            if (item.title.contains(getString(R.string.ad))) {
                model.openAd(item, pos)
            } else if (item.link != "") {
                model.needOpen = true
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
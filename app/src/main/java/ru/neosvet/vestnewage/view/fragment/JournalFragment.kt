package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.databinding.JournalFragmentBinding
import ru.neosvet.vestnewage.viewmodel.JournalToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.Ready
import ru.neosvet.vestnewage.viewmodel.basic.Success
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.activity.MarkerActivity.Companion.addByPar
import ru.neosvet.vestnewage.view.basic.Tip
import ru.neosvet.vestnewage.view.list.paging.PagingAdapter

class JournalFragment : Fragment(), Observer<NeoState> {
    private val toiler: JournalToiler by lazy {
        ViewModelProvider(this).get(JournalToiler::class.java).apply { init(requireContext()) }
    }
    private var binding: JournalFragmentBinding? = null
    private val adapter: PagingAdapter by lazy {
        PagingAdapter(this::onItemClick, this::onItemLongClick, this::finishedList)
    }

    private var act: MainActivity? = null
    private lateinit var tip: Tip
    private lateinit var anMin: Animation
    private lateinit var anMax: Animation

    override fun onAttach(context: Context) {
        act = activity as MainActivity?
        super.onAttach(context)
    }

    override fun onDestroyView() {
        act = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = JournalFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toiler.preparing()
        initViews()
        initAnim()
        activity?.let {
            it.title = getString(R.string.journal)
            toiler.state.observe(it, this)
        }
        if (toiler.offset > 0)
            binding?.rvJournal?.smoothScrollToPosition(toiler.offset)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initViews() = binding?.run {
        tip = Tip(act, tvFinish)
        fabClear.setOnClickListener {
            fabClear.isVisible = false
            tvEmptyJournal.isVisible = true
            toiler.clear()
            adapter.submitData(lifecycle, PagingData.empty())
        }
        rvJournal.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvJournal.adapter = adapter
        lifecycleScope.launch {
            toiler.paging().collect {
                adapter.submitData(lifecycle, it)
            }
        }

        rvJournal.setOnTouchListener { _, motionEvent: MotionEvent ->
            if (adapter.itemCount == 0) return@setOnTouchListener false
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                fabClear.startAnimation(anMin)
            } else if (motionEvent.action == MotionEvent.ACTION_UP
                || motionEvent.action == MotionEvent.ACTION_CANCEL
            ) {
                fabClear.isVisible = true
                fabClear.startAnimation(anMax)
            }
            false
        }
    }

    private fun initAnim() {
        anMin = AnimationUtils.loadAnimation(act, R.anim.minimize)
        anMin.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                binding?.fabClear?.isVisible = false
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize)
    }

    private fun onItemClick(index: Int, item: ListItem) {
        var s = item.des
        if (s.contains(getString(R.string.rnd_stih))) {
            val i = s.indexOf(Const.N, s.indexOf(getString(R.string.rnd_stih))) + 1
            s = s.substring(i)
            Lib.showToast(getString(R.string.long_press_for_mark))
        } else s = null
        openReader(item.link, s)
    }

    private fun onItemLongClick(index: Int, item: ListItem): Boolean {
        var des = item.des
        var par = ""
        var i = des.indexOf(getString(R.string.rnd_stih))
        if (i > -1 && i < des.lastIndexOf(Const.N)) {
            par = des.substring(des.indexOf(Const.N, i) + 1)
            i = des.indexOf("«")
            des = des.substring(i, des.indexOf(Const.N, i) - 1)
        } else if (des.contains("«")) {
            des = des.substring(des.indexOf("«"))
        } else des = des.substring(des.indexOf("(") + 1, des.indexOf(")"))
        addByPar(
            requireContext(),
            item.link, par, des
        )
        return true
    }

    private fun finishedList() {
        binding?.tvFinish?.text = if (toiler.loading)
            getString(R.string.load)
        else getString(R.string.finish_list)
        tip.show()
    }

    override fun onChanged(state: NeoState) {
        when (state) {
            Success ->
                tip.hideAnimated()
            Ready -> binding?.run {
                tvEmptyJournal.isVisible = true
                fabClear.isVisible = false
            }
        }
    }
}
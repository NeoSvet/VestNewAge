package ru.neosvet.vestnewage.fragment

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
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.ui.Tip
import ru.neosvet.utils.Const
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.BrowserActivity.Companion.openReader
import ru.neosvet.vestnewage.activity.MainActivity
import ru.neosvet.vestnewage.activity.MarkerActivity.Companion.addByPar
import ru.neosvet.vestnewage.databinding.JournalFragmentBinding
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.list.RecyclerAdapter
import ru.neosvet.vestnewage.model.JournalModel
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.Ready
import ru.neosvet.vestnewage.model.basic.SuccessList

class JournalFragment : Fragment(), Observer<NeoState> {
    private val model: JournalModel by lazy {
        ViewModelProvider(this).get(JournalModel::class.java)
    }
    private var binding: JournalFragmentBinding? = null
    private val adapter: RecyclerAdapter by lazy {
        RecyclerAdapter(this::onItemClick, this::onItemLongClick)
    }
    private var act: MainActivity? = null
    private lateinit var tip: Tip
    private lateinit var anMin: Animation
    private lateinit var anMax: Animation
    private var scrollToFirst = false

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
        initViews()
        initAnim()
        activity?.let {
            it.title = getString(R.string.journal)
            model.state.observe(it, this)
        }
        if (savedInstanceState == null)
            model.init(requireContext())
    }

    override fun onResume() {
        model.load()
        super.onResume()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initViews() = binding?.run {
        tip = Tip(act, tvFinish)
        fabPrev.setOnClickListener {
            model.prevPage()
        }
        fabNext.setOnClickListener {
            model.nextPage()
        }
        fabClear.setOnClickListener {
            fabClear.isVisible = false
            tvEmptyJournal.isVisible = true
            model.clear()
            adapter.clear()
            fabPrev.isVisible = false
            fabNext.isVisible = false
        }
        rvJournal.layoutManager = GridLayoutManager(requireContext(), 1)
        rvJournal.adapter = adapter
        rvJournal.setOnTouchListener { _, motionEvent: MotionEvent ->
            if (adapter.itemCount == 0) return@setOnTouchListener false
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                fabClear.startAnimation(anMin)
                if (fabNext.isVisible) {
                    fabPrev.startAnimation(anMin)
                    fabNext.startAnimation(anMin)
                }
            } else if (motionEvent.action == MotionEvent.ACTION_UP
                || motionEvent.action == MotionEvent.ACTION_CANCEL
            ) {
                fabClear.isVisible = true
                fabClear.startAnimation(anMax)
                if (model.isCanPaging) {
                    fabPrev.isVisible = true
                    fabNext.isVisible = true
                    fabPrev.startAnimation(anMax)
                    fabNext.startAnimation(anMax)
                }
            }
            false
        }
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

    private fun initAnim() {
        anMin = AnimationUtils.loadAnimation(act, R.anim.minimize)
        anMin.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                binding?.run {
                    fabClear.isVisible = false
                    fabPrev.isVisible = false
                    fabNext.isVisible = false
                }
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        anMax = AnimationUtils.loadAnimation(act, R.anim.maximize)
    }

    override fun onChanged(state: NeoState) {
        when (state) {
            Ready ->
                tip.show()
            is SuccessList ->
                updateList(state.list)
        }
    }

    private fun updateList(list: List<ListItem>) = binding?.run {
        tip.hide()
        adapter.setItems(list)
        scrollToFirst = true
        rvJournal.smoothScrollToPosition(0)
        if (list.isEmpty()) {
            fabClear.isVisible = false
            tvEmptyJournal.isVisible = true
        } else fabClear.isVisible = true
        if (model.isCanPaging) {
            fabPrev.isVisible = true
            fabNext.isVisible = true
        }
    }

    private fun onItemClick(index: Int, item: ListItem) {
        var s = item.des
        if (s.contains(getString(R.string.rnd_stih))) {
            val i = s.indexOf(Const.N, s.indexOf(getString(R.string.rnd_stih))) + 1
            s = s.substring(i)
            Lib.showToast(getString(R.string.long_press_for_mark))
        } else s = null
        openReader(item.link, s)
        model.reset()
        adapter.clear()
    }
}
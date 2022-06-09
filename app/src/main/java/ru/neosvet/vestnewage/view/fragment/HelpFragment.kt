package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.list.HelpAdapter
import ru.neosvet.vestnewage.view.list.ScrollHelper
import ru.neosvet.vestnewage.view.list.TouchHelper
import ru.neosvet.vestnewage.viewmodel.HelpToiler
import ru.neosvet.vestnewage.viewmodel.basic.ListEvent
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.UpdateList

class HelpFragment : Fragment(), Observer<NeoState> {
    companion object {
        fun newInstance(section: Int): HelpFragment =
            HelpFragment().apply {
                arguments = Bundle().apply {
                    putInt(Const.TAB, section)
                }
            }
    }

    private val toiler: HelpToiler by lazy {
        ViewModelProvider(this).get(HelpToiler::class.java)
    }
    private val adapter: HelpAdapter by lazy {
        HelpAdapter(toiler, toiler.list)
    }
    private var act: MainActivity? = null
    private var jobAnim: Job? = null
    private var scroll: ScrollHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.help_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initList(view)
        activity?.let {
            it.title = getString(R.string.help)
            toiler.state.observe(it, this)
        }
        restoreState(savedInstanceState)
    }

    override fun onDestroyView() {
        scroll?.deAttach()
        act = null
        super.onDestroyView()
    }

    override fun onAttach(context: Context) {
        act = activity as MainActivity
        super.onAttach(context)
    }

    override fun onDetach() {
        act = null
        super.onDetach()
    }

    private fun initList(container: View) {
        val rvHelp = container.findViewById(R.id.rvHelp) as RecyclerView
        rvHelp.layoutManager = GridLayoutManager(requireContext(), 1)
        rvHelp.adapter = adapter
        scroll = ScrollHelper {
            when (it) {
                ScrollHelper.Events.SCROLL_END ->
                    hideButtons()
                ScrollHelper.Events.SCROLL_START ->
                    showButtons()
            }
        }
        scroll?.attach(rvHelp)
        val swipe = TouchHelper {
            if (it != TouchHelper.Events.LIST_LIMIT)
                return@TouchHelper
            act?.run {
                hideBottomPanel()
                jobAnim?.cancel()
                jobAnim = lifecycleScope.launch {
                    delay(1000)
                    showBottomPanel()
                }
            }
        }
        swipe.attach(rvHelp)
    }

    private fun showButtons() = act?.run {
        startShowButton()
        checkBottomPanel()
    }

    private fun hideButtons() {
        act?.startHideButton()
        jobAnim?.cancel()
        jobAnim = lifecycleScope.launch {
            delay(1000)
            showButtons()
        }
    }

    private fun restoreState(state: Bundle?) {
        if (state == null) {
            val section = arguments?.getInt(Const.TAB) ?: -1
            toiler.init(requireActivity(), section)
        } else {
            toiler.restore()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onChanged(state: NeoState) = with(state) {
        if (this is UpdateList) {
            when (event) {
                ListEvent.RELOAD ->
                    adapter.notifyDataSetChanged()
                ListEvent.CHANGE ->
                    adapter.updateItem(index)
                ListEvent.REMOTE -> {
                    val sendIntent = Intent(Intent.ACTION_SEND)
                    sendIntent.type = "text/plain"
                    sendIntent.putExtra(
                        Intent.EXTRA_TEXT, getString(R.string.title_app)
                                + getString(R.string.url_on_app)
                    )
                    startActivity(sendIntent)
                }
            }
        }
    }
}
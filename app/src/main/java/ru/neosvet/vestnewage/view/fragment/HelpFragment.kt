package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.model.HelpModel
import ru.neosvet.vestnewage.model.basic.ListEvent
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.UpdateList
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.view.list.HelpAdapter

class HelpFragment : Fragment(), Observer<NeoState> {
    companion object {
        fun newInstance(section: Int): HelpFragment =
            HelpFragment().apply {
                arguments = Bundle().apply {
                    putInt(Const.TAB, section)
                }
            }
    }

    private val model: HelpModel by lazy {
        ViewModelProvider(this).get(HelpModel::class.java)
    }
    private val adapter: HelpAdapter by lazy {
        HelpAdapter(model, model.list)
    }

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
            model.state.observe(it, this)
        }
        restoreState(savedInstanceState)
    }

    private fun initList(container: View) {
        val rvHelp = container.findViewById(R.id.rvHelp) as RecyclerView
        rvHelp.layoutManager = GridLayoutManager(requireContext(), 1)
        rvHelp.adapter = adapter
    }

    private fun restoreState(state: Bundle?) {
        if (state == null) {
            val section = arguments?.getInt(Const.TAB) ?: -1
            model.init(requireActivity(), section)
        } else {
            model.restore()
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
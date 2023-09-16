package ru.neosvet.vestnewage.view.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.HelpItem
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.list.HelpAdapter
import ru.neosvet.vestnewage.view.list.ScrollHelper
import ru.neosvet.vestnewage.viewmodel.HelpToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.HelpState
import ru.neosvet.vestnewage.viewmodel.state.ListState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

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
        ViewModelProvider(this)[HelpToiler::class.java]
    }
    private val adapter = HelpAdapter(toiler::onItemClick)
    private var act: MainActivity? = null
    private var scroll: ScrollHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.list_fragment, container, false).also {
        initView(it)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.let {
            it.title = getString(R.string.help)
            toiler.state.observe(it, this)
        }
        if (adapter.itemCount == 0) {
            val section = arguments?.getInt(Const.TAB) ?: -1
            toiler.init(requireActivity(), section)
        } else
            toiler.restoreList()
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

    private fun initView(container: View) {
        val rv = container.findViewById(R.id.rvList) as RecyclerView
        rv.layoutManager = GridLayoutManager(requireContext(), 1)
        rv.adapter = adapter
        rv.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = resources.getDimension(R.dimen.content_margin_bottom).toInt()
        }
    }

    override fun onChanged(value: NeoState) {
        when (value) {
            is HelpState.Primary ->
                adapter.setItems(value.list)

            is ListState.Update<*> ->
                adapter.update(value.index, value.item as HelpItem)

            is BasicState.Message ->
                Lib.openInApps(Const.mailto + value.message, null)

            is HelpState.Open -> when (value.type) {
                HelpState.Type.PRIVACY ->
                    Lib.openInApps(Urls.WebPage + "privacy.html", null)

                HelpState.Type.SITE ->
                    Lib.openInApps(Urls.WebPage, null)

                HelpState.Type.TELEGRAM ->
                    Lib.openInApps("https://t.me/+nUS5nlrZsvM3MTEy", null)

                HelpState.Type.CHANGELOG ->
                    Lib.openInApps(Urls.WebPage + "changelog.html", null)

                HelpState.Type.GOOGLE ->
                    shareAppLink(getString(R.string.url_on_google))

                HelpState.Type.HUAWEI ->
                    shareAppLink(getString(R.string.url_on_huawei))
            }
        }
    }

    private fun shareAppLink(link: String) {
        val builder = AlertDialog.Builder(requireContext(), R.style.NeoDialog)
            .setMessage(getString(R.string.link_on_app))
            .setPositiveButton(
                getString(R.string.share)
            ) { _, _ ->
                val sendIntent = Intent(Intent.ACTION_SEND)
                sendIntent.type = "text/plain"
                sendIntent.putExtra(
                    Intent.EXTRA_TEXT, getString(R.string.title_app) + " $link"
                )
                startActivity(sendIntent)
            }
            .setNeutralButton(
                getString(R.string.copy)
            ) { _, _ ->
                Lib.copyAddress(link)
            }
            .setNegativeButton(
                getString(R.string.open)
            ) { _, _ ->
                Lib.openInApps(link, null)
            }
        builder.create().show()
    }
}
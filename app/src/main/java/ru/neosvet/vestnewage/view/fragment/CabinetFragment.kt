package ru.neosvet.vestnewage.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.CabinetScreen
import ru.neosvet.vestnewage.databinding.CabinetFragmentBinding
import ru.neosvet.vestnewage.helper.CabinetHelper
import ru.neosvet.vestnewage.network.Urls
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.CabinetActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.dialog.MessageDialog
import ru.neosvet.vestnewage.view.list.CabinetAdapter
import ru.neosvet.vestnewage.viewmodel.CabinetToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.CabinetState
import ru.neosvet.vestnewage.viewmodel.state.NeoState
import java.util.regex.Pattern

class CabinetFragment : NeoFragment(), CabinetAdapter.Host {
    companion object {
        private const val I_EMAIL = 0
        private const val I_PASSWORD = 1
        //private const val I_ALTER_PATH = 2
    }

    private var binding: CabinetFragmentBinding? = null
    private val adapter = CabinetAdapter(this)
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding!!.rvList)
    }
    private val toiler: CabinetToiler
        get() = neotoiler as CabinetToiler
    private val patternEmail =
        Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE)
    private var screen = CabinetScreen.LOGIN

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this)[CabinetToiler::class.java]

    override val title: String
        get() = getString(R.string.cabinet)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = CabinetFragmentBinding.inflate(inflater, container, false).also {
        binding = it
    }.root

    override fun onViewCreated(savedInstanceState: Bundle?) {
        setToolbar()
        initMain()
    }

    private fun setToolbar() = binding?.run {
        act?.setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    override fun onChangedInsets(insets: android.graphics.Insets) {
        binding?.run { rvList.updatePadding(bottom = App.CONTENT_BOTTOM_INDENT) }
    }

    override fun onChangedOtherState(state: NeoState) {
        setStatus(false)
        when (state) {
            is BasicState.Message -> {
                val alert = MessageDialog(requireActivity())
                alert.setTitle(getString(R.string.error))
                alert.setMessage(state.message)
                alert.setRightButton(getString(android.R.string.ok)) { alert.dismiss() }
                alert.show(null)
            }

            is CabinetState.Primary -> binding?.run {
                screen = state.screen
                adapter.setItems(state.list)
                if (screen == CabinetScreen.WORDS)
                    toolbar.title = getString(R.string.select_status)
                else toolbar.title = getString(R.string.cabinet_title)
                if (screen == CabinetScreen.LOGIN) {
                    act?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
                    checkReadyEnter()
                } else {
                    act?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    if (screen == CabinetScreen.CABINET)
                        act?.setAction(R.drawable.ic_exit)
                    else act?.setAction(R.drawable.ic_close)
                }
                initLayoutManager()
            }
        }
    }

    private fun initLayoutManager() {
        val span = if (screen == CabinetScreen.CABINET) 1
        else ScreenUtils.span
        binding?.rvList?.layoutManager = GridLayoutManager(requireContext(), span)
    }

    override fun onBackPressed() = if (screen == CabinetScreen.LOGIN)
        true
    else {
        toiler.exit()
        false
    }

    private fun initMain() = binding?.run {
        rvList.layoutManager = GridLayoutManager(requireContext(), ScreenUtils.span)
        rvList.adapter = adapter
        setListEvents(rvList)
    }

    private fun checkReadyEnter() {
        val matcher = patternEmail.matcher(adapter.getText(I_EMAIL))
        val isReady = matcher.matches() && adapter.getText(I_PASSWORD).length > 5
        act?.setAction(if (isReady) R.drawable.ic_ok else 0)
    }

    override fun onDestroyView() {
        toiler.forget(adapter.getCheck(I_EMAIL).not(), adapter.getCheck(I_PASSWORD).not())
        binding = null
        super.onDestroyView()
    }

    private fun subLogin() {
        if (isBlocked) return
        softKeyboard.hide()
        if (adapter.itemCount == 1) {
            adapter.clear()
            toiler.loginScreen()
            return
        }
        setStatus(true)
        val email = adapter.getText(I_EMAIL)
        val password = adapter.getText(I_PASSWORD)
        toiler.login(email, password)
        if (adapter.getCheck(I_EMAIL))
            toiler.save(email, if (adapter.getCheck(I_PASSWORD)) password else "")
    }

    override fun onClickItem(index: Int, item: BasicItem) {
        if (isBlocked) return
        when (screen) {
            CabinetScreen.LOGIN -> {
                if (index == 5) return //additional
                val link = when (index) {
                    6 -> {
                        Urls.openInApps("http://neosvet.ucoz.ru/vna/vpn.html")
                        return
                    }

                    7 -> "sendpass.html"
                    8 -> "register.html"
                    9 -> "reginfo.html"
                    10 -> "regstat.html"
                    else -> "trans.html"
                }
                CabinetActivity.openPage(link)
            }

            CabinetScreen.CABINET -> {
                when (index) {
                    0 -> if (item.des == getString(R.string.select_status)) {
                        setStatus(true)
                        toiler.getListWord()
                    } else act?.showToast(getString(R.string.send_unlivable))

                    1 -> CabinetActivity.openPage("edinenie/anketa.html")
                    2 -> CabinetActivity.openPage("edinenie/edinomyshlenniki.html")
                }
            }

            CabinetScreen.WORDS -> {
                setStatus(true)
                toiler.selectWord(index, item.title)
            }
        }
    }

    override fun onAction(title: String) {
        if (screen == CabinetScreen.LOGIN)
            subLogin()
        else toiler.exit()
    }

    override fun onCheckItem(index: Int, value: Boolean) {
        toiler.setCheck(index, value)
        when {
            index == 2 && !value -> { // is rem email, off rem password
                adapter.setCheck(I_PASSWORD, false)
                toiler.setCheck(3, false)
            }

            index == 3 && value -> { // is rem password, on rem email
                adapter.setCheck(I_EMAIL, true)
                toiler.setCheck(2, true)
            }

            index == 4 -> { // is alter path
                CabinetHelper.cookie = ""
                CabinetHelper.isAlterPath = value
            }
        }
    }

    override fun onTextItem(index: Int, value: String) {
        toiler.setText(index, value)
        checkReadyEnter()
    }
}
package ru.neosvet.vestnewage.view.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.BasicItem
import ru.neosvet.vestnewage.data.CabinetScreen
import ru.neosvet.vestnewage.databinding.CabinetFragmentBinding
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.CabinetActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.CabinetToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.CabinetState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

class CabinetFragment : NeoFragment() {
    private var binding: CabinetFragmentBinding? = null
    private val adapter = RecyclerAdapter(this::onItemClick)
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding!!.login.etPassword)
    }
    private val toiler: CabinetToiler
        get() = neotoiler as CabinetToiler
    private var screen = CabinetScreen.LOGIN
    private var isReadyLogin = false

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
        initLogin()
    }

    private fun setToolbar() = binding?.run {
        act?.setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    override fun onChangedOtherState(state: NeoState) {
        setStatus(false)
        when (state) {
            is BasicState.Message -> {
                val alert = CustomDialog(act)
                alert.setTitle(getString(R.string.error))
                alert.setMessage(state.message)
                alert.setRightButton(getString(android.R.string.ok)) { alert.dismiss() }
                alert.show(null)
            }

            is CabinetState.Primary -> binding?.run {
                screen = state.screen
                if (screen == CabinetScreen.WORDS)
                    toolbar.title = getString(R.string.select_status)
                else toolbar.title = getString(R.string.cabinet_title)
                if (screen == CabinetScreen.LOGIN) {
                    act?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
                    login.root.isVisible = true
                    act?.setAction(R.drawable.ic_ok)
                    rvList.layoutManager = GridLayoutManager(requireContext(), 1)
                    checkReadyEnter()
                } else {
                    act?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    login.root.isVisible = false
                    if (screen == CabinetScreen.CABINET)
                        act?.setAction(R.drawable.ic_exit)
                    else act?.setAction(R.drawable.ic_close)
                    initLayoutManager()
                }
                adapter.setItems(state.list)
            }

            is CabinetState.AuthPair -> binding?.login?.run {
                if (state.login.isNotEmpty()) {
                    cbRemEmail.isChecked = true
                    etEmail.setText(state.login)
                }
                if (state.password.isNotEmpty()) {
                    cbRemPassword.isChecked = true
                    etPassword.setText(state.password)
                }
            }
        }
    }

    private fun initLayoutManager() {
        val span = if (screen == CabinetScreen.WORDS)
            ScreenUtils.span
        else 1 //CABINET
        binding?.rvList?.layoutManager = GridLayoutManager(requireContext(), span)
    }

    override fun onBackPressed() = if (screen == CabinetScreen.LOGIN)
        true
    else {
        toiler.exit()
        false
    }

    private fun initMain() = binding?.run {
        rvList.layoutManager = GridLayoutManager(requireContext(), 1)
        rvList.adapter = adapter
        setListEvents(rvList)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initLogin() = binding?.login?.run {
        etEmail.doAfterTextChanged {
            checkReadyEnter()
            bClearEmail.isVisible = it?.isNotEmpty() ?: false
        }
        etPassword.doAfterTextChanged {
            checkReadyEnter()
            bClearPassword.isVisible = it?.isNotEmpty() ?: false
        }
        etPassword.setOnKeyListener { _, keyCode: Int, keyEvent: KeyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == EditorInfo.IME_ACTION_GO
            ) {
                if (isReadyLogin) subLogin()
                return@setOnKeyListener true
            }
            false
        }
        cbRemEmail.setOnCheckedChangeListener { _, check: Boolean ->
            cbRemPassword.isEnabled = check
            if (!check) cbRemPassword.isChecked = false
        }
        bClearEmail.setOnClickListener { etEmail.setText("") }
        bClearPassword.setOnClickListener { etPassword.setText("") }
        root.setOnTouchListener { _, _ ->
            act?.hideBottomArea()
            return@setOnTouchListener false
        }
    }

    private fun check(email: String, password: String) {
        isReadyLogin = if (email.length > 5 && password.length > 5) {
            email.contains("@") && email.contains(".")
        } else false
    }

    private fun checkReadyEnter() = binding?.login?.run {
        check(etEmail.text.toString(), etPassword.text.toString())
        act?.setAction(if (isReadyLogin) R.drawable.ic_ok else 0)
    }

    override fun onDestroyView() {
        binding?.login?.run {
            toiler.forget(cbRemEmail.isChecked.not(), cbRemPassword.isChecked.not())
        }
        binding = null
        super.onDestroyView()
    }

    private fun subLogin() {
        if (isBlocked) return
        if (adapter.itemCount == 1) {
            adapter.clear()
            toiler.loginScreen()
            return
        }
        binding?.login?.run {
            setStatus(true)
            softKeyboard.hide()
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            toiler.login(email, password)
            if (cbRemEmail.isChecked)
                toiler.save(email, if (cbRemPassword.isChecked) password else "")
        }
    }

    private fun onItemClick(index: Int, item: BasicItem) {
        if (isBlocked) return
        when (screen) {
            CabinetScreen.LOGIN -> {
                val s = when (index) {
                    0 -> {
                        Lib.openInApps("http://neosvet.ucoz.ru/vna/vpn.html", null)
                        return
                    }

                    1 -> "sendpass.html"
                    2 -> "register.html"
                    3 -> "reginfo.html"
                    4 -> "regstat.html"
                    else -> "trans.html"
                }
                CabinetActivity.openPage(s)
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
}
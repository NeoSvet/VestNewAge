package ru.neosvet.vestnewage.view.fragment

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.databinding.CabinetFragmentBinding
import ru.neosvet.vestnewage.helper.CabinetHelper
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.CabinetActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import ru.neosvet.vestnewage.view.list.RecyclerAdapter
import ru.neosvet.vestnewage.viewmodel.CabinetToiler
import ru.neosvet.vestnewage.viewmodel.basic.MessageState
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.SuccessList

class CabinetFragment : NeoFragment() {
    private var binding: CabinetFragmentBinding? = null
    private val adapter: RecyclerAdapter = RecyclerAdapter(this::onItemClick)
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding!!.login.etPassword)
    }
    private val toiler: CabinetToiler
        get() = neotoiler as CabinetToiler
    private val helper: CabinetHelper
        get() = toiler.helper

    override fun initViewModel(): NeoToiler =
        ViewModelProvider(this).get(CabinetToiler::class.java).apply { init(requireContext()) }

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
        initMain()
        initLogin()
        restoreState(savedInstanceState)
    }

    override fun onChangedState(state: NeoState) {
        setStatus(false)
        when (state) {
            is MessageState -> {
                val alert = CustomDialog(act)
                alert.setTitle(getString(R.string.error))
                alert.setMessage(state.message)
                alert.setRightButton(getString(android.R.string.ok)) { alert.dismiss() }
                alert.show(null)
            }
            is SuccessList -> {
                binding?.run {
                    if (toiler.screen == CabinetToiler.Screen.LOGIN) {
                        login.root.isVisible = true
                        act?.setAction(R.drawable.ic_ok)
                        rvList.layoutManager = GridLayoutManager(requireContext(), 1)
                        checkReadyEnter()
                    } else {
                        login.root.isVisible = false
                        act?.setAction(R.drawable.ic_exit)
                        initLayoutManager()
                    }
                }
                adapter.setItems(state.list)
            }
            else -> {}
        }
    }

    private fun initLayoutManager() {
        val span = if (toiler.screen == CabinetToiler.Screen.WORDS)
            ScreenUtils.span
        else 1 //CABINET
        binding?.rvList?.layoutManager = GridLayoutManager(requireContext(), span)
    }

    override fun onBackPressed(): Boolean = toiler.onBack()

    private fun restoreState(state: Bundle?) {
        if (state != null) {
            toiler.restoreScreen()
        } else {
            val p = helper.getAuthPair()
            binding?.login?.run {
                if (p.first.isNotEmpty()) {
                    cbRemEmail.isChecked = true
                    etEmail.setText(p.first)
                }
                if (p.second.isNotEmpty()) {
                    cbRemPassword.isChecked = true
                    etPassword.setText(p.second)
                }
            }
            toiler.loginScreen()
        }
    }

    private fun initMain() = binding?.run {
        rvList.layoutManager = GridLayoutManager(requireContext(), 1)
        rvList.adapter = adapter
        setListEvents(rvList)
    }

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
                if (toiler.isReadyLogin) subLogin()
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
        root.setOnTouchListener{_, event: MotionEvent ->
            act?.hideBottomArea()
            return@setOnTouchListener false
        }
    }

    private fun checkReadyEnter() = binding?.login?.run {
        toiler.check(etEmail.text.toString(), etPassword.text.toString())
        act?.setAction(if (toiler.isReadyLogin) R.drawable.ic_ok else 0)
    }

    override fun onDestroyView() {
        binding?.login?.run {
            helper.forget(cbRemPassword.isChecked.not(), cbRemEmail.isChecked.not())
        }
        binding = null
        super.onDestroyView()
    }

    private fun subLogin() {
        if (toiler.isRun) return
        if (adapter.itemCount == 1) {
            adapter.clear()
            toiler.loginScreen()
        }
        binding?.login?.run {
            setStatus(true)
            softKeyboard.hide()
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            toiler.login(email, password)
            if (cbRemEmail.isChecked) {
                helper.save(email, if (cbRemPassword.isChecked) password else "")
            }
        }
    }

    private fun onItemClick(index: Int, item: ListItem) {
        if (toiler.isRun) return
        when (toiler.screen) {
            CabinetToiler.Screen.LOGIN -> {
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
            CabinetToiler.Screen.CABINET -> {
                when (index) {
                    0 -> if (item.des == getString(R.string.select_status)) {
                        setStatus(true)
                        toiler.getListWord()
                    } else Lib.showToast(getString(R.string.send_unlivable))
                    1 -> CabinetActivity.openPage("edinenie/anketa.html")
                    2 -> CabinetActivity.openPage("edinenie/edinomyshlenniki.html")
                }
            }
            CabinetToiler.Screen.WORDS -> {
                setStatus(true)
                toiler.selectWord(index, item.title)
            }
        }
    }

    override fun onAction(title: String) {
        if (toiler.screen == CabinetToiler.Screen.LOGIN)
            subLogin()
        else toiler.exit()
    }
}
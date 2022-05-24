package ru.neosvet.vestnewage.view.fragment

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
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.databinding.CabinetFragmentBinding
import ru.neosvet.vestnewage.helper.CabinetHelper
import ru.neosvet.vestnewage.model.CabinetModel
import ru.neosvet.vestnewage.model.basic.MessageState
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.SuccessList
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.view.activity.CabinetActivity
import ru.neosvet.vestnewage.view.basic.NeoFragment
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.dialog.CustomDialog
import ru.neosvet.vestnewage.view.list.RecyclerAdapter

class CabinetFragment : NeoFragment() {
    private var binding: CabinetFragmentBinding? = null
    private val adapter: RecyclerAdapter = RecyclerAdapter(this::onItemClick)
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(binding!!.login.etPassword)
    }
    private val model: CabinetModel
        get() = neomodel as CabinetModel
    private val helper: CabinetHelper
        get() = model.helper

    override fun initViewModel(): NeoViewModel =
        ViewModelProvider(this).get(CabinetModel::class.java).apply { init(requireContext()) }

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
                    if (model.type == CabinetModel.Type.LOGIN) {
                        login.root.isVisible = true
                        fabEnter.isVisible = true
                        fabExit.isVisible = false
                        rvList.layoutManager = GridLayoutManager(requireContext(), 1)
                    } else {
                        login.root.isVisible = false
                        fabEnter.isVisible = false
                        fabExit.isVisible = true
                        initLayoutManager()
                    }
                }
                adapter.setItems(state.list)
            }
        }
    }

    private fun initLayoutManager() {
        val span = if (model.type == CabinetModel.Type.WORDS)
            ScreenUtils.span
        else 1 //CABINET
        binding?.rvList?.layoutManager = GridLayoutManager(requireContext(), span)
    }

    override fun onBackPressed(): Boolean = model.onBack()

    private fun restoreState(state: Bundle?) {
        if (state != null) {
            model.restoreScreen()
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
            model.loginScreen()
        }
    }

    private fun initMain() = binding?.run {
        rvList.layoutManager = GridLayoutManager(requireContext(), 1)
        rvList.adapter = adapter
        fabEnter.setOnClickListener { subLogin() }
        fabExit.setOnClickListener { model.exit() }
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
                if (binding?.fabEnter?.isVisible == true) subLogin()
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
    }

    private fun checkReadyEnter() = binding?.run {
        val ready = login.run {
            if (etEmail.length() > 5 && etPassword.length() > 5) {
                (etEmail.text.toString().contains("@")
                        && etEmail.text.toString().contains("."))
            } else false
        }
        fabEnter.isVisible = ready
    }

    override fun onDestroyView() {
        binding?.login?.run {
            helper.forget(cbRemPassword.isChecked.not(), cbRemEmail.isChecked.not())
        }
        binding = null
        super.onDestroyView()
    }

    private fun subLogin() {
        if (model.isRun) return
        if (adapter.itemCount == 1) {
            adapter.clear()
            model.loginScreen()
        }
        binding?.login?.run {
            setStatus(true)
            softKeyboard.hide()
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            model.login(email, password)
            if (cbRemEmail.isChecked) {
                helper.save(email, if (cbRemPassword.isChecked) password else "")
            }
        }
    }

    private fun onItemClick(index: Int, item: ListItem) {
        if (model.isRun) return
        when (model.type) {
            CabinetModel.Type.LOGIN -> {
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
            CabinetModel.Type.CABINET -> {
                when (index) {
                    0 -> if (item.des == getString(R.string.select_status)) {
                        setStatus(true)
                        model.getListWord()
                    } else Lib.showToast(getString(R.string.send_unlivable))
                    1 -> CabinetActivity.openPage("edinenie/anketa.html")
                    2 -> CabinetActivity.openPage("edinenie/edinomyshlenniki.html")
                }
            }
            CabinetModel.Type.WORDS -> {
                setStatus(true)
                model.selectWord(index, item.title)
            }
        }
    }
}
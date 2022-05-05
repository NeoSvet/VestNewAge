package ru.neosvet.vestnewage.fragment

import android.app.Service
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView.OnItemClickListener
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import ru.neosvet.ui.NeoFragment
import ru.neosvet.ui.SoftKeyboard
import ru.neosvet.ui.dialogs.CustomDialog
import ru.neosvet.utils.Lib
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.CabinetActivity
import ru.neosvet.vestnewage.databinding.CabinetFragmentBinding
import ru.neosvet.vestnewage.helpers.CabinetHelper
import ru.neosvet.vestnewage.list.ListAdapter
import ru.neosvet.vestnewage.model.CabinetModel
import ru.neosvet.vestnewage.model.basic.MessageState
import ru.neosvet.vestnewage.model.basic.NeoState
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.SuccessList

class CabinetFragment : NeoFragment() {
    private var binding: CabinetFragmentBinding? = null
    private val adMain: ListAdapter by lazy {
        ListAdapter(requireContext())
    }
    private lateinit var softKeyboard: SoftKeyboard
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
                    } else {
                        login.root.isVisible = false
                        fabEnter.isVisible = false
                        fabExit.isVisible = true
                    }
                }
                adMain.setItems(state.list)
            }
        }
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
        lvList.adapter = adMain
        lvList.onItemClickListener =
            OnItemClickListener { _, _, pos: Int, _ ->
                if (model.isRun) return@OnItemClickListener
                when (model.type) {
                    CabinetModel.Type.LOGIN -> {
                        val s = when (pos) {
                            0 -> {
                                Lib.openInApps("http://neosvet.ucoz.ru/vna/vpn.html", null)
                                return@OnItemClickListener
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
                        when (pos) {
                            0 -> if (adMain.getItem(pos).des ==
                                getString(R.string.select_status)
                            ) {
                                setStatus(true)
                                model.getListWord()
                            } else Lib.showToast(getString(R.string.send_unlivable))
                            1 -> CabinetActivity.openPage("edinenie/anketa.html")
                            2 -> CabinetActivity.openPage("edinenie/edinomyshlenniki.html")
                            else -> {}
                        }
                    }
                    CabinetModel.Type.WORDS -> {
                        setStatus(true)
                        model.selectWord(pos, adMain.getItem(pos).title)
                    }
                }
            }
        fabEnter.setOnClickListener { subLogin() }
        fabExit.setOnClickListener { model.onBack() }
    }

    private fun initLogin() = binding?.login?.run {
        val im = act!!.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager
        softKeyboard = SoftKeyboard(root, im)

        val textWatcher: TextWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val ready = if (etEmail.length() > 5 && etPassword.length() > 5) {
                    (etEmail.text.toString().contains("@")
                            && etEmail.text.toString().contains("."))
                } else false
                binding?.fabEnter?.isVisible = ready
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        }
        etEmail.addTextChangedListener(textWatcher)
        etPassword.addTextChangedListener(textWatcher)
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
    }

    override fun onDestroyView() {
        binding?.login?.run {
            helper.forget(cbRemPassword.isChecked.not(), cbRemEmail.isChecked.not())
        }
        binding = null
        super.onDestroyView()
    }

    private fun subLogin() {
        softKeyboard.closeSoftKeyboard()
        if (model.isRun) return
        if (adMain.count == 1) {
            adMain.clear()
            model.loginScreen()
        }
        binding?.login?.run {
            setStatus(true)
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            model.login(email, password)
            if (cbRemEmail.isChecked) {
                helper.save(email, if (cbRemPassword.isChecked) password else "")
            }
        }
    }
}
package ru.neosvet.vestnewage.view.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.databinding.MarkerActivityBinding
import ru.neosvet.vestnewage.helper.MarkerHelper
import ru.neosvet.vestnewage.storage.PageStorage
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.view.basic.NeoToast
import ru.neosvet.vestnewage.view.basic.ResizeAnim
import ru.neosvet.vestnewage.view.basic.SoftKeyboard
import ru.neosvet.vestnewage.view.list.CheckAdapter
import ru.neosvet.vestnewage.viewmodel.MarkerToiler
import ru.neosvet.vestnewage.viewmodel.basic.NeoState

@SuppressLint("DefaultLocale")
class MarkerActivity : AppCompatActivity() {
    companion object {
        @JvmStatic
        fun addByPos(context: Context, link: String, pos: Float, des: String) {
            val marker = Intent(context, MarkerActivity::class.java)
            marker.putExtra(Const.LINK, link)
            marker.putExtra(Const.PLACE, pos)
            marker.putExtra(Const.DESCTRIPTION, des)
            context.startActivity(marker)
        }

        @JvmStatic
        fun addByPar(context: Context, link: String, par: String, des: String) {
            val marker = Intent(context, MarkerActivity::class.java)
            marker.putExtra(Const.LINK, link)
            if (par.isNotEmpty()) {
                val p = parToNumList(link, par)
                if (p.indexOf(Const.COMMA) > 0)
                    marker.putExtra(Const.PAGE, p)
                else if (p.isNotEmpty())
                    marker.putExtra(DataBase.PARAGRAPH, p.toInt())
            }
            marker.putExtra(Const.DESCTRIPTION, des)
            context.startActivity(marker)
        }

        private fun parToNumList(link: String, par: String): String {
            val p = if (par.contains("<")) Lib.withOutTags(par) else par
            val storage = PageStorage()
            storage.open(link)
            val cursor = storage.getParagraphs(storage.getPageId(link))
            val s = StringBuilder()
            if (cursor.moveToFirst()) {
                var n = 1
                do {
                    val t = Lib.withOutTags(cursor.getString(0))
                    if (p.contains(t)) {
                        s.append(n)
                        s.append(", ")
                    }
                    n++
                } while (cursor.moveToNext())
            }
            cursor.close()
            storage.close()
            if (s.isNotEmpty())
                s.delete(s.length - 2, s.length)
            return s.toString()
        }
    }

    private val toiler: MarkerToiler by lazy {
        ViewModelProvider(this).get(MarkerToiler::class.java).apply { init(baseContext) }
    }
    private val adPar: CheckAdapter by lazy {
        CheckAdapter(list = helper.parsList, onChecked = helper::checkPars)
    }
    private val adCol: CheckAdapter by lazy {
        CheckAdapter(list = helper.colsList, onChecked = helper::checkCols)
    }
    private lateinit var binding: MarkerActivityBinding
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(mainLayout)
    }
    private val mainLayout: View
        get() = binding.content.root
    private val helper: MarkerHelper
        get() = toiler.helper
    private val toast: NeoToast by lazy {
        NeoToast(binding.tvToast, null)
    }
    private var density = 0f
    private var heightDialog = 0
    private var hasError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MarkerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        density = resources.displayMetrics.density
        initActivity()
        initContent()
        restoreState(savedInstanceState)
        lifecycleScope.launch {
            toiler.state.collect {
                onChangedState(it)
            }
        }
    }

    private fun onChangedState(state: NeoState) {
        when (state) {
            NeoState.Success ->
                showData()
            NeoState.Ready -> if (helper.title.isEmpty()) {
                hasError = true
                toast.autoHide = false
                toast.show(getString(R.string.not_load_page))
            }
            is NeoState.Error -> {
                hasError = true
                val builder = AlertDialog.Builder(this, R.style.NeoDialog)
                    .setTitle(getString(R.string.error))
                    .setMessage(state.message)
                    .setPositiveButton(
                        getString(R.string.send)
                    ) { _, _ ->
                        Lib.openInApps(Const.mailto + state.information, null)
                    }
                    .setNegativeButton(
                        getString(android.R.string.cancel)
                    ) { dialog, _ -> dialog.dismiss() }
                builder.create().show()
            }
            else -> {}
        }
    }

    @SuppressLint("Range")
    private fun restoreState(state: Bundle?) {
        setResult(RESULT_CANCELED)
        if (state == null) {
            toiler.open(intent)
            return
        }
        showData()
        binding.run {
            when (helper.type) {
                MarkerHelper.Type.NONE -> Unit
                MarkerHelper.Type.POS -> {
                    mainLayout.isVisible = false
                    pPos.layoutParams.height = heightDialog
                    pPos.requestLayout()
                    pPos.isVisible = true
                    tvPos.text = helper.getPosText(helper.newPos)
                    sbPos.progress = (helper.newPos * 10).toInt()
                }
                MarkerHelper.Type.PAR -> {
                    rvList.adapter = adPar
                    mainLayout.isVisible = false
                    rvList.layoutParams.height = heightDialog
                    rvList.requestLayout()
                    rvList.isVisible = true
                }
                MarkerHelper.Type.COL -> {
                    rvList.adapter = adCol
                    mainLayout.isVisible = false
                    rvList.layoutParams.height = heightDialog
                    rvList.requestLayout()
                    rvList.isVisible = true
                }
            }
        }
    }

    private fun initActivity() = binding.run {
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        heightDialog = metrics.heightPixels -
                (resources.getInteger(R.integer.top_minus) * density).toInt()
        rvList.layoutManager = GridLayoutManager(this@MarkerActivity, 1)
        sbPos.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val f = progress / 10f
                tvPos.text = String.format(getString(R.string.format_scroll_position), f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                tvPos.text = newProgPos()
            }
        })
        fabOk.setOnClickListener {
            if (hasError) {
                super.onBackPressed()
                return@setOnClickListener
            }
            when (helper.type) {
                MarkerHelper.Type.NONE -> {
                    if (content.etCol.isFocused) {
                        softKeyboard.hide()
                        createCol(content.etCol.text.toString())
                        content.etCol.clearFocus()
                    } else if (content.etDes.isFocused) {
                        content.etDes.clearFocus()
                        softKeyboard.hide()
                    } else
                        saveMarker()
                }
                MarkerHelper.Type.PAR ->
                    saveSelectedPar()
                MarkerHelper.Type.POS ->
                    saveSelectedPos()
                MarkerHelper.Type.COL ->
                    saveSelectedCol()
            }
        }
        bMinus.setOnClickListener {
            val s = tvPos.text.toString()
            var t = s
            while (sbPos.progress > 4 && s == t) {
                sbPos.progress = sbPos.progress - 5
                t = newProgPos()
            }
            tvPos.text = t
        }
        bPlus.setOnClickListener {
            val s = tvPos.text.toString()
            var t = s
            while (sbPos.progress < 996 && s == t) {
                sbPos.progress = sbPos.progress + 5
                t = newProgPos()
            }
            tvPos.text = t
        }
    }

    @SuppressLint("Range")
    private fun initContent() = binding.content.run {
        rPar.setOnClickListener {
            if (hasError) return@setOnClickListener
            if (rPar.isChecked) {
                helper.isPar = true
                helper.updateSel()
                tvSel.text = helper.sel
            }
        }
        rPos.setOnClickListener {
            if (hasError) return@setOnClickListener
            if (rPos.isChecked) {
                helper.isPar = false
                helper.updateSel()
                tvSel.text = helper.sel
            }
        }
        tvSel.setOnClickListener {
            if (hasError) return@setOnClickListener
            binding.run {
                if (helper.isPar) {
                    helper.type = MarkerHelper.Type.PAR
                    rvList.adapter = adPar
                    showView(rvList)
                } else {
                    helper.type = MarkerHelper.Type.POS
                    sbPos.progress = (helper.pos * 10).toInt()
                    tvPos.text = helper.posText
                    showView(pPos)
                }
            }
        }
        tvCol.setOnClickListener {
            if (hasError) return@setOnClickListener
            helper.type = MarkerHelper.Type.COL
            binding.rvList.adapter = adCol
            showView(binding.rvList)
        }
        etCol.setOnKeyListener { _, keyCode: Int, keyEvent: KeyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == EditorInfo.IME_ACTION_GO
            ) {
                if (!hasError) createCol(etCol.text.toString())
                return@setOnKeyListener true
            }
            false
        }
        etDes.doAfterTextChanged {
            bClearDes.isVisible = it?.isNotEmpty() ?: false
        }
        bClearDes.setOnClickListener { etDes.setText("") }
        etCol.doAfterTextChanged {
            bClearCol.isVisible = it?.isNotEmpty() ?: false
        }
        bClearCol.setOnClickListener { etCol.setText("") }
    }

    private fun createCol(title: String) {
        if (title.isEmpty()) return
        helper.checkTitleCol(title)?.let {
            toast.show(it)
            return@createCol
        }
        toiler.addCol(title)
        softKeyboard.hide()
        binding.content.etCol.setText("")
        helper.cols += ", $title"
        binding.content.tvCol.text = helper.cols
    }

    private fun showView(view: View) {
        softKeyboard.hide()
        mainLayout.isVisible = false
        view.isVisible = true
        val anim = ResizeAnim(
            view,
            false,
            heightDialog
        )
        anim.duration = 800
        view.clearAnimation()
        view.startAnimation(anim)
    }

    private fun saveSelectedCol() {
        val s = helper.getColList()
        if (s == null) {
            toast.show(getString(R.string.need_set_check))
            return
        }
        helper.cols = s
        binding.content.tvCol.text = s
        hideView(binding.rvList)
    }

    private fun saveSelectedPos() {
        helper.pos = helper.newPos
        helper.posText = helper.getPosText()
        helper.updateSel()
        binding.content.tvSel.text = helper.sel
        hideView(binding.pPos)
    }

    private fun saveSelectedPar() {
        val s = helper.getParList()
        if (s == null) {
            toast.show(getString(R.string.need_set_check))
            return
        }
        helper.sel = s
        binding.content.tvSel.text = s
        hideView(binding.rvList)
    }

    private fun saveMarker() {
        setResult(RESULT_OK)
        toiler.finish(binding.content.etDes.text.toString())
    }

    private fun newProgPos(): String {
        val f = binding.sbPos.progress / 10f
        helper.newPos = f
        return helper.getPosText(f)
    }

    override fun onBackPressed() {
        if (hasError) {
            super.onBackPressed()
            return
        }
        when (helper.type) {
            MarkerHelper.Type.NONE ->
                super.onBackPressed()
            MarkerHelper.Type.POS ->
                hideView(binding.pPos)
            MarkerHelper.Type.PAR -> {
                hideView(binding.rvList)
                helper.setParList()
            }
            MarkerHelper.Type.COL -> {
                hideView(binding.rvList)
                helper.setColList()
            }
        }
    }

    private fun hideView(view: View) {
        val anim = ResizeAnim(view, false, 10)
        anim.duration = 600
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                view.isVisible = false
                helper.type = MarkerHelper.Type.NONE
                mainLayout.isVisible = true
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        view.clearAnimation()
        view.startAnimation(anim)
    }

    private fun showData() = binding.content.run {
        tvTitle.text = helper.title
        if (helper.isPar)
            rPar.isChecked = true
        else
            rPos.isChecked = true
        tvSel.text = helper.sel
        etDes.setText(helper.des)
        tvCol.text = helper.cols
    }
}
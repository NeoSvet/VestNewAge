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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DataBase
import ru.neosvet.vestnewage.data.MarkerScreen
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
import ru.neosvet.vestnewage.viewmodel.state.BasicState
import ru.neosvet.vestnewage.viewmodel.state.MarkerState
import ru.neosvet.vestnewage.viewmodel.state.NeoState

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
        ViewModelProvider(this)[MarkerToiler::class.java]
    }
    private lateinit var binding: MarkerActivityBinding
    private val softKeyboard: SoftKeyboard by lazy {
        SoftKeyboard(mainLayout)
    }
    private val mainLayout: View
        get() = binding.content.root

    private lateinit var helper: MarkerHelper
    private val toast: NeoToast by lazy {
        NeoToast(binding.tvToast, null)
    }
    private var newPos = 0f
    private var heightDialog = 0
    private var hasError = false
    private var screen = MarkerScreen.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MarkerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity()
        initContent()
        setResult(RESULT_CANCELED)
        if (savedInstanceState == null) intent?.let {
            toiler.setArgument(it)
        }
        runObserve()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.run {
            toiler.setStatus(
                MarkerState.Status(
                    screen = screen,
                    selection = content.tvSel.text.toString(),
                    positionText = tvPos.text.toString(),
                    position = sbPos.progress
                )
            )
        }
        super.onSaveInstanceState(outState)
    }

    private fun runObserve() {
        lifecycleScope.launch {
            toiler.state.collect {
                onChangedState(it)
            }
        }
        toiler.start(this)
    }

    private fun onChangedState(state: NeoState) {
        when (state) {
            BasicState.NotLoaded -> {
                hasError = true
                toast.autoHide = false
                toast.show(getString(R.string.not_load_page))
            }

            is BasicState.Message ->
                toast.show(state.message)

            BasicState.Ready ->
                finish()

            is MarkerState.Primary ->
                showData(state)

            is MarkerState.Text -> when (state.type) {
                MarkerState.TextType.SEL ->
                    binding.content.tvSel.text = state.text

                MarkerState.TextType.COL -> {
                    softKeyboard.hide()
                    binding.content.etCol.setText("")
                    binding.content.tvCol.text = state.text
                }
            }

            is MarkerState.Status ->
                restoreStatus(state)

            is BasicState.Error -> {
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
        }
    }

    private fun restoreStatus(state: MarkerState.Status) = binding.run {
        screen = state.screen
        content.tvSel.text = state.selection
        when (screen) {
            MarkerScreen.NONE -> {}
            MarkerScreen.POSITION -> {
                mainLayout.isVisible = false
                pPos.layoutParams.height = heightDialog
                pPos.requestLayout()
                pPos.isVisible = true
                tvPos.text = state.positionText
                sbPos.progress = state.position
            }

            MarkerScreen.PARAGRAPH -> {
                rvList.adapter = CheckAdapter(
                    list = helper.parsList,
                    onChecked = helper::checkPars
                )
                mainLayout.isVisible = false
                rvList.layoutParams.height = heightDialog
                rvList.requestLayout()
                rvList.isVisible = true
            }

            MarkerScreen.COLLECTION -> {
                rvList.adapter = CheckAdapter(
                    list = helper.colsList,
                    onChecked = helper::checkCols
                )
                mainLayout.isVisible = false
                rvList.layoutParams.height = heightDialog
                rvList.requestLayout()
                rvList.isVisible = true
            }
        }
    }

    private fun initActivity() = binding.run {
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val density = resources.displayMetrics.density
        val topMinus = resources.getInteger(R.integer.top_minus)
        heightDialog = metrics.heightPixels - (topMinus * density).toInt()
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
                finish()
                return@setOnClickListener
            }
            when (screen) {
                MarkerScreen.NONE -> {
                    if (content.etCol.isFocused) {
                        softKeyboard.hide()
                        toiler.createCollection(content.etCol.text.toString())
                        content.etCol.clearFocus()
                    } else if (content.etDes.isFocused) {
                        content.etDes.clearFocus()
                        softKeyboard.hide()
                    } else
                        saveMarker()
                }

                MarkerScreen.PARAGRAPH -> {
                    toiler.saveParList()
                    hideView(binding.rvList)
                }

                MarkerScreen.POSITION -> {
                    toiler.savePosition(newPos)
                    hideView(binding.pPos)
                }

                MarkerScreen.COLLECTION -> {
                    toiler.saveColList()
                    hideView(binding.rvList)
                }
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
            if (rPar.isChecked)
                toiler.select(true)
        }
        rPos.setOnClickListener {
            if (hasError) return@setOnClickListener
            if (rPos.isChecked)
                toiler.select(false)
        }
        tvSel.setOnClickListener {
            if (hasError) return@setOnClickListener
            binding.run {
                if (rPar.isChecked) {
                    screen = MarkerScreen.PARAGRAPH
                    toiler.openParagraph()
                    showView(rvList)
                } else {
                    screen = MarkerScreen.POSITION
                    toiler.openPosition()
                    showView(pPos)
                }
            }
        }
        tvCol.setOnClickListener {
            if (hasError) return@setOnClickListener
            screen = MarkerScreen.COLLECTION
            toiler.openCollection()
            showView(binding.rvList)
        }
        etCol.setOnKeyListener { _, keyCode: Int, keyEvent: KeyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == EditorInfo.IME_ACTION_GO
            ) {
                if (!hasError) toiler.createCollection(etCol.text.toString())
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

    private fun showView(view: View) {
        softKeyboard.hide()
        mainLayout.isVisible = false
        view.isVisible = true
        val anim = ResizeAnim(view, false, heightDialog)
        anim.duration = 800
        view.clearAnimation()
        view.startAnimation(anim)
    }

    private fun saveMarker() {
        setResult(RESULT_OK)
        toiler.finish(binding.content.etDes.text.toString())
    }

    private fun newProgPos(): String {
        newPos = binding.sbPos.progress / 10f
        return helper.getPosText(newPos)
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (hasError) {
                finish()
                return
            }
            when (screen) {
                MarkerScreen.NONE ->
                    finish()

                MarkerScreen.POSITION ->
                    hideView(binding.pPos)

                MarkerScreen.PARAGRAPH -> {
                    hideView(binding.rvList)
                    toiler.restoreParList()
                }

                MarkerScreen.COLLECTION -> {
                    hideView(binding.rvList)
                    toiler.restoreColList()
                }
            }
        }
    }

    private fun hideView(view: View) {
        screen = MarkerScreen.NONE
        val anim = ResizeAnim(view, false, 10)
        anim.duration = 600
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                view.isVisible = false
                mainLayout.isVisible = true
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        view.clearAnimation()
        view.startAnimation(anim)
    }

    private fun showData(state: MarkerState.Primary) = binding.content.run {
        helper = state.helper
        if (tvTitle == null)
            supportActionBar?.title = getString(R.string.marker) + ": " + state.title
        else tvTitle.text = state.title
        if (state.isPar)
            rPar.isChecked = true
        else
            rPos.isChecked = true
        tvSel.text = state.sel
        etDes.setText(state.des)
        tvCol.text = state.cols
    }
}

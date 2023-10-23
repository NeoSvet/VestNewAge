package ru.neosvet.vestnewage.view.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.databinding.TipActivityBinding
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.TipUtils
import ru.neosvet.vestnewage.view.basic.NeoSnackbar
import java.util.Stack

class TipActivity : AppCompatActivity() {
    companion object {
        @JvmStatic
        fun showTip(type: TipUtils.Type, hasOff: Boolean = false) {
            val intent = Intent(App.context, TipActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(Const.MODE, type)
            intent.putExtra(Const.END, hasOff)
            App.context.startActivity(intent)
        }
    }

    private lateinit var binding: TipActivityBinding
    private lateinit var tips: Stack<TipUtils.Unit>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TipActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!intent.hasExtra(Const.MODE))
            finish()
        tips = TipUtils.getUnit(intent.getSerializableExtra(Const.MODE) as TipUtils.Type)
        nextTip()
        binding.btnOk.setOnClickListener {
            if (tips.empty()) finish()
            else nextTip()
        }
        if (intent.getBooleanExtra(Const.END, true)) {
            binding.btnOffAll.setOnClickListener {
                tips.clear()
                TipUtils.offAll()
                NeoSnackbar().show(binding.tvTip, getString(R.string.look_tips_in_help))
            }
        } else binding.btnOffAll.isVisible = false
    }

    private fun nextTip() = binding.run {
        val tip = tips.pop()
        tvTip.text = tip.message
        ivTip.setImageResource(tip.imgId)
        val parent = ConstraintLayout.LayoutParams.PARENT_ID
        ivTip.updateLayoutParams<ConstraintLayout.LayoutParams> {
            if (tip.alignV == TipUtils.Vertical.BOTTOM) {
                bottomToTop = btnOk.id
                topToTop = -1
            } else {
                bottomToTop = -1
                topToTop = parent
            }
            if (tip.alignH == TipUtils.Horizontal.LEFT || tip.alignH == TipUtils.Horizontal.CENTER)
                startToStart = parent
            else startToStart = -1
            if (tip.alignH == TipUtils.Horizontal.RIGHT || tip.alignH == TipUtils.Horizontal.CENTER)
                endToEnd = parent
            else endToEnd = -1
        }

        if (tip.addArrow) {
            if (tip.alignV == TipUtils.Vertical.TOP)
                ivArrow.setImageResource(R.drawable.ic_top)
            else ivArrow.setImageResource(R.drawable.ic_bottom)
            ivArrow.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToTop = -1
                if (tip.alignV == TipUtils.Vertical.TOP) {
                    topToBottom = ivTip.id
                    bottomToTop = -1
                } else {
                    topToBottom = -1
                    bottomToTop = ivTip.id
                }
                if (tip.alignH == TipUtils.Horizontal.LEFT || tip.alignH == TipUtils.Horizontal.CENTER)
                    startToStart = parent
                else startToStart = -1
                if (tip.alignH == TipUtils.Horizontal.RIGHT || tip.alignH == TipUtils.Horizontal.CENTER)
                    endToEnd = parent
                else endToEnd = -1
            }
            ivArrow.isVisible = true
            tvTip.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToTop = -1
                if (tip.alignV == TipUtils.Vertical.BOTTOM) {
                    topToBottom = -1
                    bottomToTop = ivArrow.id
                } else {
                    topToBottom = ivArrow.id
                    bottomToTop = btnOk.id
                }
            }
        } else {
            tvTip.updateLayoutParams<ConstraintLayout.LayoutParams> {
                if (tip.alignV == TipUtils.Vertical.BOTTOM) {
                    topToTop = parent
                    topToBottom = -1
                    bottomToTop = ivTip.id
                } else {
                    topToTop = -1
                    topToBottom = ivTip.id
                    bottomToTop = -1
                }
            }
        }
    }
}
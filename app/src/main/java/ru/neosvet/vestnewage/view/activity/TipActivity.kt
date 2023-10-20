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

class TipActivity : AppCompatActivity() {
    companion object {

        @JvmStatic
        fun showTip(type: TipUtils.Type) {
            val intent = Intent(App.context, TipActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(Const.MODE, type)
            App.context.startActivity(intent)
        }
    }

    private lateinit var binding: TipActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TipActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!intent.hasExtra(Const.MODE))
            finish()
        initTip(TipUtils.getUnit(intent.getSerializableExtra(Const.MODE) as TipUtils.Type))
        binding.btnOk.setOnClickListener {
            finish()
        }
        binding.btnOffAll.setOnClickListener {
            TipUtils.offAll()
            NeoSnackbar().show(binding.tvTip, getString(R.string.look_tips_in_help))
        }
    }

    private fun initTip(tip: TipUtils.Unit) = binding.run {
        tvTip.text = tip.message
        ivTip.setImageResource(tip.imgId)
        val parent = ConstraintLayout.LayoutParams.PARENT_ID
        ivTip.updateLayoutParams<ConstraintLayout.LayoutParams> {
            if (tip.alignV == TipUtils.Vertical.BOTTOM) {
                bottomToTop = btnOk.id
                topToTop = -1
            }
            if (tip.alignH == TipUtils.Horizontal.LEFT || tip.alignH == TipUtils.Horizontal.CENTER)
                startToStart = parent
            else
                startToStart = -1
            if (tip.alignH == TipUtils.Horizontal.RIGHT || tip.alignH == TipUtils.Horizontal.CENTER)
                endToEnd = parent
        }

        if (tip.addArrow) {
            if (tip.alignV == TipUtils.Vertical.TOP)
                ivArrow.setImageResource(R.drawable.ic_top)
            else
                ivArrow.setImageResource(R.drawable.ic_bottom)
            ivArrow.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToTop = -1
                if (tip.alignV == TipUtils.Vertical.TOP)
                    topToBottom = ivTip.id
                else
                    bottomToTop = ivTip.id
                if (tip.alignH == TipUtils.Horizontal.CENTER)
                    endToEnd = parent
                else if (tip.alignH == TipUtils.Horizontal.RIGHT) {
                    startToStart = -1
                    endToEnd = parent
                }
            }
            ivArrow.isVisible = true
            tvTip.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToTop = -1
                if (tip.alignV == TipUtils.Vertical.BOTTOM) {
                    bottomToBottom = -1
                    bottomToTop = ivArrow.id
                } else
                    topToBottom = ivArrow.id
            }
        } else {
            tvTip.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToTop = -1
                if (tip.alignV == TipUtils.Vertical.BOTTOM) {
                    bottomToBottom = -1
                    bottomToTop = ivTip.id
                } else
                    topToBottom = ivTip.id
            }
        }
    }
}
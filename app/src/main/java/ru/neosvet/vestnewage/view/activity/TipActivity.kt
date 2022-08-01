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

enum class AlignV {
    TOP, BOTTOM
}

enum class AlignH {
    LEFT, CENTER, RIGHT
}

data class TipUnit(
    val message: String,
    val imgId: Int,
    val alignH: AlignH,
    val alignV: AlignV,
    val addArrow: Boolean
)

enum class TipName {
    MAIN_STAR, CALENDAR, BROWSER_PANEL
}

class TipActivity : AppCompatActivity() {
    companion object {
        @JvmStatic
        fun showTip(name: TipName) {
            val intent = Intent(App.context, TipActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(Const.MODE, name)
            App.context.startActivity(intent)
        }

    }

    private lateinit var binding: TipActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TipActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!intent.hasExtra(Const.MODE))
            onBackPressed()
        initTip(getUnit())
        binding.fabClose.setOnClickListener {
            onBackPressed()
        }
    }

    private fun initTip(tip: TipUnit) = binding.run {
        tvTip.text = tip.message
        ivTip.setImageResource(tip.imgId)
        ivTip.updateLayoutParams<ConstraintLayout.LayoutParams> {
            val parent = ConstraintLayout.LayoutParams.PARENT_ID
            if (tip.alignV == AlignV.BOTTOM) {
                bottomToBottom = parent
                topToBottom = -1
            }
            if (tip.alignH == AlignH.LEFT || tip.alignH == AlignH.CENTER)
                startToStart = parent
            else
                startToStart = -1
            if (tip.alignH == AlignH.RIGHT || tip.alignH == AlignH.CENTER)
                endToEnd = parent
        }

        if (tip.addArrow) {
            if (tip.alignV == AlignV.TOP)
                ivArrow.setImageResource(R.drawable.ic_top)
            else
                ivArrow.setImageResource(R.drawable.ic_bottom)
            ivArrow.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToTop = -1
                if (tip.alignV == AlignV.TOP)
                    topToBottom = ivTip.id
                else
                    bottomToTop = ivTip.id
            }
            ivArrow.isVisible = true
            tvTip.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToTop = -1
                bottomToBottom = -1
                if (tip.alignV == AlignV.TOP)
                    topToBottom = ivArrow.id
                else
                    bottomToTop = ivArrow.id
            }
        }
    }

    private fun getUnit(): TipUnit =
        when (intent.getSerializableExtra(Const.MODE) as TipName) {
            TipName.MAIN_STAR -> TipUnit(
                message = getString(R.string.tip_main),
                imgId = R.drawable.tip_main,
                alignH = AlignH.RIGHT,
                alignV = AlignV.BOTTOM,
                addArrow = true
            )
            TipName.CALENDAR -> TipUnit(
                message = getString(R.string.tip_calendar),
                imgId = R.drawable.tip_calendar,
                alignH = AlignH.CENTER,
                alignV = AlignV.TOP,
                addArrow = true
            )
            TipName.BROWSER_PANEL -> TipUnit(
                message = getString(R.string.tip_browser),
                imgId = R.drawable.tip_browser,
                alignH = AlignH.CENTER,
                alignV = AlignV.BOTTOM,
                addArrow = false
            )
        }
}
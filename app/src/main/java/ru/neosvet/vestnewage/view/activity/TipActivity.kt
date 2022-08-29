package ru.neosvet.vestnewage.view.activity

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.snackbar.Snackbar
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
    MAIN_STAR, CALENDAR, BROWSER_PANEL, BROWSER_FULLSCREEN, SEARCH
}

class TipActivity : AppCompatActivity() {
    companion object {
        const val TAG = "tip"
        private var pref: SharedPreferences? = null

        @JvmStatic
        fun showTipIfNeed(name: TipName) {
            if (pref == null)
                pref = App.context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
            pref?.let {
                if (it.getBoolean(name.toString(), true)) {
                    showTip(name)
                    val editor = it.edit()
                    editor.putBoolean(name.toString(), false)
                    editor.apply()
                }
            }
        }

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
        binding.btnOk.setOnClickListener {
            onBackPressed()
        }
        binding.btnOffAll.setOnClickListener {
            val editor = pref!!.edit()
            TipName.values().forEach {
                editor.putBoolean(it.toString(), false)
            }
            editor.apply()
            Snackbar.make(
                binding.tvTip,
                getString(R.string.look_tips_in_help),
                Snackbar.LENGTH_SHORT
            ).setBackgroundTint(getColor(R.color.colorPrimary))
                .setActionTextColor(getColor(R.color.colorAccentLight))
                .setAction(android.R.string.ok) {
                    onBackPressed()
                }.show()
        }
    }

    private fun initTip(tip: TipUnit) = binding.run {
        tvTip.text = tip.message
        ivTip.setImageResource(tip.imgId)
        val parent = ConstraintLayout.LayoutParams.PARENT_ID
        ivTip.updateLayoutParams<ConstraintLayout.LayoutParams> {
            if (tip.alignV == AlignV.BOTTOM) {
                bottomToTop = btnOk.id
                topToTop = -1
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
                if (tip.alignH == AlignH.CENTER)
                    endToEnd = parent
                else if (tip.alignH == AlignH.RIGHT) {
                    startToStart = -1
                    endToEnd = parent
                }
            }
            ivArrow.isVisible = true
            tvTip.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToTop = -1
                if (tip.alignV == AlignV.BOTTOM) {
                    bottomToBottom = -1
                    bottomToTop = ivArrow.id
                } else
                    topToBottom = ivArrow.id
            }
        } else {
            tvTip.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topToTop = -1
                if (tip.alignV == AlignV.BOTTOM) {
                    bottomToBottom = -1
                    bottomToTop = ivTip.id
                } else
                    topToBottom = ivTip.id
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
            TipName.BROWSER_FULLSCREEN -> TipUnit(
                message = getString(R.string.tip_browser2),
                imgId = R.drawable.tip_browser2,
                alignH = AlignH.LEFT,
                alignV = AlignV.TOP,
                addArrow = true
            )
            TipName.SEARCH -> TipUnit(
                message = getString(R.string.tip_search),
                imgId = R.drawable.tip_search,
                alignH = AlignH.LEFT,
                alignV = AlignV.TOP,
                addArrow = true
            )
        }
}
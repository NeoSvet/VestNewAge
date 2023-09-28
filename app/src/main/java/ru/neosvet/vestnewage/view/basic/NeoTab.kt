package ru.neosvet.vestnewage.view.basic

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.view.list.TabAdapter

class NeoTab @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private lateinit var root: View
    private var isHorizontal: Boolean = true
    private lateinit var adapter: TabAdapter
    lateinit var btnPrev: ImageButton
        private set
    lateinit var btnNext: ImageButton
        private set
    lateinit var rvTab: RecyclerView
        private set
    var isBlocked = false
    private var onOutSelect: ((Int) -> Unit)? = null
    var selectedIndex: Int
        get() = adapter.selected
        set(value) = select(value)
    val selectedStart: Boolean
        get() = selectedIndex == 0
    val selectedEnd: Boolean
        get() = selectedIndex == adapter.itemCount - 1
    val count: Int
        get() = adapter.itemCount

    init {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        if (attrs != null) initOrientation(attrs)
        root = if (isHorizontal)
            inflate(context, R.layout.tab_layout_horizontal, this)
        else inflate(context, R.layout.tab_layout_vertical, this)

        btnNext = root.findViewById(R.id.btn_next)
        btnPrev = root.findViewById(R.id.btn_prev)
        rvTab = root.findViewById(R.id.rv_tab)
        if (isHorizontal) {
            rvTab.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            rvTab.addItemDecoration(
                DividerItemDecoration(context, DividerItemDecoration.HORIZONTAL)
            )
        } else {
            rvTab.layoutManager = GridLayoutManager(context, 1)
            rvTab.addItemDecoration(
                DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            )
        }

        adapter = TabAdapter(this::onItem)
        rvTab.adapter = adapter

        btnPrev.setOnClickListener {
            if (isBlocked) return@setOnClickListener
            val i = selectedIndex
            if (i > 0) select(i - 1)
            else select(adapter.itemCount - 1)
            onOutSelect?.invoke(selectedIndex)
        }
        btnNext.setOnClickListener {
            if (isBlocked) return@setOnClickListener
            val i = selectedIndex + 1
            if (i == adapter.itemCount) select(0)
            else select(i)
            onOutSelect?.invoke(selectedIndex)
        }
    }

    @SuppressLint("Recycle", "CustomViewStyleable")
    private fun initOrientation(attrs: AttributeSet) {
        val attributes = context.obtainStyledAttributes(
            attrs, androidx.appcompat.R.styleable.LinearLayoutCompat, 0, 0
        )
        isHorizontal = attributes.getInt(
            androidx.appcompat.R.styleable.LinearLayoutCompat_android_orientation,
            0
        ) == 0
    }

    private fun select(index: Int) {
        if (selectedIndex == index) return
        adapter.select(index)
        rvTab.smoothScrollToPosition(index)
    }

    fun setItems(list: List<String>, selectedIndex: Int) {
        if (list.size != adapter.itemCount) adapter.setItems(list)
        this.selectedIndex = selectedIndex
    }

    fun setOnChangeListener(onChange: (Int) -> Unit) {
        onOutSelect = onChange
    }

    fun setDescription(prev: String, next: String) {
        btnPrev.contentDescription = prev
        btnNext.contentDescription = next
    }

    private fun onItem(index: Int) {
        if (index == selectedIndex || isBlocked) return
        select(index)
        onOutSelect?.invoke(index)
    }

    fun change(plus: Boolean) {
        if (isBlocked) return
        val i = selectedIndex + if (plus) 1 else -1
        selectedIndex = if (i < 0) count - 1
        else if (i == count) 0 else i
        onOutSelect?.invoke(selectedIndex)
    }

    fun fixWidth(m: Float) {
        if (isHorizontal) return
        val w = ((btnPrev.measuredWidth + btnNext.measuredWidth) * m).toInt()
        root.updateLayoutParams<ViewGroup.LayoutParams> {
            width = w
        }
        rvTab.updateLayoutParams<ViewGroup.LayoutParams> {
            width = w
        }
    }

    fun limitedWidth(scope: LifecycleCoroutineScope) {
        scope.launch {
            delay(50)
            var w = context.fromDpi(R.dimen.update_width_land) +
                    context.fromDpi(R.dimen.def_indent) + context.fromDpi(R.dimen.half_indent)
            w = (root.parent as ViewGroup).measuredWidth - w
            if (root.measuredWidth > w)
                root.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = w
                }
        }
    }
}
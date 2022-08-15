package ru.neosvet.vestnewage.helper

import android.annotation.SuppressLint
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.MenuItem
import ru.neosvet.vestnewage.data.Section
import ru.neosvet.vestnewage.network.NetConst
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.ScreenUtils
import ru.neosvet.vestnewage.utils.UnreadUtils
import ru.neosvet.vestnewage.view.activity.BrowserActivity
import ru.neosvet.vestnewage.view.activity.MainActivity
import ru.neosvet.vestnewage.view.activity.TipActivity
import ru.neosvet.vestnewage.view.activity.TipName
import ru.neosvet.vestnewage.view.basic.Tip
import ru.neosvet.vestnewage.view.dialog.DownloadDialog
import ru.neosvet.vestnewage.view.fragment.MenuFragment
import ru.neosvet.vestnewage.view.list.MenuAdapter

class MainHelper(private val act: MainActivity) {
    companion object {
        const val TAG = "Main"
    }

    enum class ActionType {
        MENU, ACTION
    }

    var type = ActionType.MENU
        private set
    lateinit var tipAction: Tip
        private set
    lateinit var fabAction: FloatingActionButton
        private set
    private val adAction = MenuAdapter(this::onActionClick)
    var actionIcon: Int = R.drawable.star
        private set
    var shownActionMenu = false
        private set
    var shownDwnDialog = false
        private set
    var tvTitle: TextView? = null
    var bottomBar: BottomAppBar? = null
    var topBar: AppBarLayout? = null
    var svMain: NestedScrollView? = null
    val bottomAreaIsHide: Boolean
        get() = fabAction.isVisible.not() && bottomBar?.isVisible == false
    var frMenu: MenuFragment? = null
    var tvNew: TextView? = null
    lateinit var pStatus: View
        private set
    lateinit var tvPromTimeFloat: TextView
        private set
    lateinit var tvToast: TextView
        private set

    val unread = UnreadUtils()
    var countNew: Int = 0
    var curSection = Section.SETTINGS
    var prevSection: Section? = null
    val newId: Int
        get() = unread.getNewId(countNew)
    private val pref = act.getSharedPreferences(TAG, AppCompatActivity.MODE_PRIVATE)
    val isFloatPromTime: Boolean
        get() = pref.getBoolean(Const.PROM_FLOAT, false)
    var isSideMenu: Boolean = false
        private set

    fun initViews() {
        pStatus = act.findViewById(R.id.pStatus)
        tvToast = act.findViewById(R.id.tvToast)
        tvPromTimeFloat = act.findViewById(R.id.tvPromTimeFloat)
        fabAction = act.findViewById(R.id.fabAction)
        val rvAction = act.findViewById<RecyclerView>(R.id.rvAction)
        tipAction = Tip(act, rvAction)
        tipAction.autoHide = false
        rvAction.layoutManager = GridLayoutManager(act, 1)
        rvAction.adapter = adAction

        fabAction.setOnClickListener {
            if (type == ActionType.MENU)
                showActionMenu()
            else
                act.onAction(TAG)
        }
        val ivHeadBack = act.findViewById<ImageView>(R.id.ivHeadBack)
        if (ScreenUtils.type == ScreenUtils.Type.TABLET_PORT)
            ivHeadBack.setImageResource(R.drawable.head_back_tablet)
        ivHeadBack.setOnClickListener {
            Lib.openInApps(NetConst.SITE, null)
        }

        isSideMenu = ScreenUtils.isTabletLand
        if (isSideMenu) {
            act.findViewById<View>(R.id.btnProm).setOnClickListener {
                BrowserActivity.openReader(Const.PROM_LINK, null)
            }
            return
        }
        tvTitle = act.findViewById(R.id.tvTitle)
        bottomBar = act.findViewById(R.id.bottomBar)
        bottomBar?.setBackgroundResource(R.drawable.panel_bg)

        svMain = act.findViewById(R.id.svMain)
        topBar = act.findViewById(R.id.topBar)

        if (ScreenUtils.isLand) {
            ivHeadBack.setImageResource(R.drawable.head_back_land)
            val tv = act.findViewById<View>(R.id.tvGodWords)
            val p = tv.paddingBottom
            tv.setPadding(p, p, tv.paddingEnd, p)
        }
    }

    private var isFirst: Boolean? = null
    val isFirstRun: Boolean
        get() = isFirst ?: initFirst()

    private fun initFirst(): Boolean {
        isFirst = pref.getBoolean(Const.FIRST, true)
        if (isFirst!!) {
            val editor = pref.edit()
            editor.putBoolean(Const.FIRST, false)
            editor.apply()
        }
        return isFirst!!
    }

    fun getFirstSection(): Section {
        val startScreen = pref.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR)
        return when {
            startScreen == Const.SCREEN_MENU && ScreenUtils.isTablet.not() ->
                Section.MENU
            startScreen == Const.SCREEN_SUMMARY ->
                Section.SUMMARY
            else -> //startScreen == Const.SCREEN_CALENDAR || !isFullMenu ->
                Section.CALENDAR
        }
    }

    fun startWithNew(): Boolean {
        countNew = unread.count
        val startNew = pref.getBoolean(Const.START_NEW, false)
        return startNew && countNew > 0
    }

    fun checkNew() = tvNew?.run {
        isVisible = countNew > 0
        if (isVisible) text = countNew.toString()
    }

    fun setNewValue() {
        frMenu?.setNew(newId)
    }

    fun updateNew() {
        val k = unread.count
        if (countNew == k) return
        countNew = k
        frMenu?.setNew(newId)
        checkNew()
    }

    fun setMenuFragment() {
        if (frMenu == null) {
            val fragmentTransaction = act.supportFragmentManager.beginTransaction()
            frMenu = MenuFragment().also {
                fragmentTransaction.replace(R.id.menu_fragment, it).commit()
            }
        }
        frMenu?.let {
            it.setSelect(curSection)
            it.setNew(newId)
        }
    }

    fun hideActionMenu() {
        if (shownActionMenu.not()) return
        shownActionMenu = false
        act.unblocked()
        tipAction.hide()
    }

    fun changeSection(section: Section, savePrev: Boolean) {
        hideActionMenu()
        adAction.clear()
        prevSection = if (savePrev) curSection else null
        curSection = section
        when (section) {
            Section.NEW ->
                setActionIcon(R.drawable.ic_clear)
            Section.JOURNAL ->
                setActionIcon(R.drawable.ic_clear)
            Section.SUMMARY ->
                setActionIcon(R.drawable.ic_refresh)
            Section.SEARCH ->
                setActionIcon(R.drawable.ic_settings)
            else ->
                setActionIcon(R.drawable.star)
        }
    }

    private fun onActionClick(index: Int, item: MenuItem) {
        hideActionMenu()
        if (index == 0) {
            showDownloadDialog()
            return
        }
        if (index == adAction.itemCount - 1)
            return
        act.onAction(item.title)
    }

    fun showDownloadDialog() {
        shownDwnDialog = true
        val dialog = DownloadDialog(act).apply {
            setOnDismissListener { shownDwnDialog = false }
        }
        dialog.show()
    }

    @SuppressLint("NonConstantResourceId", "NotifyDataSetChanged")
    private fun showActionMenu() {
        shownActionMenu = true
        act.blocked()
        tipAction.show()
        if (adAction.itemCount > 0) return
        adAction.addItem(R.drawable.ic_download, act.getString(R.string.download_))
        when (curSection) {
            Section.SITE, Section.CALENDAR -> {
                adAction.addItem(R.drawable.ic_refresh, act.getString(R.string.refresh))
            }
            Section.BOOK -> {
                adAction.addItem(R.drawable.ic_book, act.getString(R.string.rnd_verse))
                adAction.addItem(R.drawable.ic_book, act.getString(R.string.rnd_epistle))
                adAction.addItem(R.drawable.ic_book, act.getString(R.string.rnd_poem))
                adAction.addItem(R.drawable.ic_refresh, act.getString(R.string.refresh))
            }
            Section.MARKERS -> {
                adAction.addItem(R.drawable.ic_marker, act.getString(R.string.export))
                adAction.addItem(R.drawable.ic_marker, act.getString(R.string.import_))
                adAction.addItem(R.drawable.ic_edit, act.getString(R.string.edit))
            }
            else -> {}
        }
        adAction.addItem(R.drawable.ic_close, act.getString(R.string.close))
        adAction.notifyDataSetChanged()
    }

    fun getYear(link: String): Int {
        try {
            var s = link
            s = s.substring(s.lastIndexOf("/") + 1, s.lastIndexOf("."))
            return s.toInt()
        } catch (ignored: Exception) {
        }
        return 2016
    }

    fun setActionIcon(icon: Int) {
        actionIcon = if (icon == 0) R.drawable.star else icon
        type = if (actionIcon == R.drawable.star) {
            checkTip()
            ActionType.MENU
        } else ActionType.ACTION
        fabAction.setImageDrawable(ContextCompat.getDrawable(act, actionIcon))
        bottomBar?.requestLayout()
    }

    private fun checkTip() {
        if (pref.getBoolean(Const.TIP, true).not()) return
        val editor = pref.edit()
        editor.putBoolean(Const.TIP, false)
        editor.apply()
        TipActivity.showTip(TipName.MAIN_STAR)
    }
}
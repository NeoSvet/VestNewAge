package ru.neosvet.vestnewage.helpers

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.allViews
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import ru.neosvet.ui.Tip
import ru.neosvet.utils.Const
import ru.neosvet.utils.ScreenUtils
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.activity.MainActivity
import ru.neosvet.vestnewage.fragment.MenuFragment
import ru.neosvet.vestnewage.service.LoaderService

class MainHelper(private val act: MainActivity) {
    enum class MenuType {
        FLOAT, SIDE, FULL
    }

    var navView: NavigationView? = null
    var frMenu: MenuFragment? = null
    lateinit var menuDownload: Tip
    lateinit var bDownloadAll: TextView
    lateinit var bDownloadIt: TextView
    lateinit var tvNew: TextView
    lateinit var pStatus: View
    lateinit var pDownload: View
    var toolbar: Toolbar? = null
    lateinit var tvPromTime: TextView
    var drawer: DrawerLayout? = null
    lateinit var tabLayout: TabLayout

    val unread = UnreadHelper()
    var countNew: Int = unread.count
    var curId = 0
    var prevId = 0
    val hasPrevId: Boolean
        get() = prevId != 0
    val newId: Int
        get() = unread.getNewId(countNew)
    private val pref: SharedPreferences = act.getSharedPreferences(
        MainActivity::class.java.simpleName,
        AppCompatActivity.MODE_PRIVATE
    )
    var menuType = MenuType.FLOAT
    val isCountInMenu: Boolean
        get() = pref.getBoolean(Const.COUNT_IN_MENU, true)
    val isSideMenu: Boolean
        get() = menuType == MenuType.SIDE

    fun initViews() {
        navView = act.findViewById(R.id.nav_view)
        bDownloadAll = act.findViewById(R.id.bDownloadAll)
        bDownloadIt = act.findViewById(R.id.bDownloadIt)
        tvNew = act.findViewById(R.id.tvNew)
        pStatus = act.findViewById(R.id.pStatus)
        pDownload = act.findViewById(R.id.pDownload)
        toolbar = act.findViewById(R.id.toolbar)
        tvPromTime = act.findViewById(R.id.tvPromTime)
        tabLayout = act.findViewById(R.id.tablayout)
        menuDownload = Tip(act, pDownload)

        val content = act.findViewById<View>(android.R.id.content).rootView as View
        content.allViews.forEach {
            if (it is DrawerLayout) {
                drawer = it
                return@forEach
            }
        }

        bDownloadAll.setOnClickListener {
            menuDownload.hide()
            LoaderService.postCommand(
                LoaderService.DOWNLOAD_ALL, ""
            )
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

    fun getFirstFragment(): Int {
//        val startScreen = pref.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR)
//        firstFragment = when (startScreen) {
//            Const.SCREEN_MENU -> R.id.menu_fragment
//            Const.SCREEN_SUMMARY -> R.id.nav_rss
//            else -> R.id.nav_calendar
//        }
        val startNew = pref.getBoolean(Const.START_NEW, false)
        if (startNew && countNew > 0)
            return R.id.nav_new

        val startScreen = pref.getInt(Const.START_SCEEN, Const.SCREEN_CALENDAR)
        menuType = if (ScreenUtils.isTabletLand)
            MenuType.SIDE
        else
            MenuType.FLOAT
        return when {
            startScreen == Const.SCREEN_MENU && ScreenUtils.isTablet.not() -> {
                menuType = MenuType.FULL
                R.id.menu_fragment
            }
            startScreen == Const.SCREEN_SUMMARY ->
                R.id.nav_rss
            else -> //startScreen == Const.SCREEN_CALENDAR || !isMenuMode ->
                R.id.nav_calendar
        }
    }

    fun checkNew(): Boolean {
        if (countNew > 0 && (curId == R.id.nav_new || curId == R.id.nav_rss ||
                    curId == R.id.nav_site || curId == R.id.nav_calendar)
        ) {
            tvNew.isVisible = true
            return true
        }
        tvNew.isVisible = false
        return false
    }

    fun setNewValue() {
        tvNew.text = countNew.toString()
        navView?.menu?.getItem(0)?.setIcon(newId) ?: frMenu?.setNew(newId)
    }

    fun updateNew() {
        val k = unread.count
        if (countNew == k) return
        countNew = k
        navView?.menu?.getItem(0)?.setIcon(newId) ?: frMenu?.setNew(newId)
        tvNew.text = countNew.toString()
        if (checkNew())
            tvNew.startAnimation(AnimationUtils.loadAnimation(act, R.anim.blink))
    }

    fun setMenuFragment() {
        if (frMenu == null) {
            val fragmentTransaction = act.supportFragmentManager.beginTransaction()
            frMenu = MenuFragment().also {
                fragmentTransaction.replace(R.id.menu_fragment, it).commit()
            }
        }
        frMenu?.let {
            it.setSelect(curId)
            it.setNew(newId)
        }
    }

    fun changeId(id: Int, savePrev: Boolean) {
        menuDownload.hide()
        prevId = if (savePrev) curId else 0
        curId = id
    }

    @SuppressLint("NonConstantResourceId")
    fun showDownloadMenu() {
        if (menuDownload.isShow)
            menuDownload.hide()
        else {
            if (ProgressHelper.isBusy()) return
            with(bDownloadIt) {
                when (curId) {
                    R.id.nav_site -> {
                        isVisible = true
                        text = act.getString(R.string.download_it_main)
                    }
                    R.id.nav_calendar -> {
                        isVisible = true
                        text = act.getString(R.string.download_it_calendar)
                    }
                    R.id.nav_book -> {
                        isVisible = true
                        text = act.getString(R.string.download_it_book)
                    }
                    else -> isVisible = false
                }
            }
            menuDownload.show()
        }
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
}
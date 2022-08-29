package ru.neosvet.vestnewage.viewmodel

import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.data.DateUnit
import ru.neosvet.vestnewage.data.ListItem
import ru.neosvet.vestnewage.helper.SummaryHelper
import ru.neosvet.vestnewage.loader.SummaryLoader
import ru.neosvet.vestnewage.loader.page.PageLoader
import ru.neosvet.vestnewage.utils.Const
import ru.neosvet.vestnewage.utils.Lib
import ru.neosvet.vestnewage.utils.percent
import ru.neosvet.vestnewage.viewmodel.basic.NeoState
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import java.io.BufferedReader
import java.io.FileReader

class SummaryToiler : NeoToiler() {
    private var sBack: String = ""

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, SummaryHelper.TAG)
        .build()

    fun init(context: Context) {
        if (sBack.isEmpty())
            sBack = context.getString(R.string.back)
    }

    override suspend fun doLoad() {
        val loader = SummaryLoader()
        loader.loadList(true)
        val summaryHelper = SummaryHelper()
        summaryHelper.updateBook()
        val list = openList()
        postState(NeoState.ListValue(list))
        if (isRun)
            loadPages(list)
    }

    private fun isNeedReload(): Boolean {
        val f = Lib.getFile(Const.RSS)
        return !f.exists() || DateUnit.isLongAgo(f.lastModified())
    }

    private suspend fun loadPages(pages: List<ListItem>) {
        val loader = PageLoader()
        currentLoader = loader
        var i = 0
        while (i < pages.size && isRun) {
            loader.download(pages[i].link, false)
            i++
            postState(NeoState.Progress(i.percent(pages.size)))
        }
        loader.finish()
        isRun = false
        postState(NeoState.Success)
    }

    fun openList(loadIfNeed: Boolean) {
        this.loadIfNeed = loadIfNeed
        scope.launch {
            val list = openList()
            postState(NeoState.ListValue(list))
            if (loadIfNeed && (list.isEmpty() || isNeedReload())) {
                reLoad()
            }
        }
    }

    private suspend fun openList(): List<ListItem> {
        val list = mutableListOf<ListItem>()
        val now = System.currentTimeMillis()
        val file = Lib.getFile(Const.RSS)
        postState(NeoState.LongValue(file.lastModified()))
        val br = BufferedReader(FileReader(file))
        var title: String? = br.readLine()
        var d: String
        while (title != null) {
            val link = br.readLine()
            d = br.readLine()
            val time = br.readLine()
            d = DateUnit.getDiffDate(now, time.toLong()) +
                    sBack + Const.N + d
            list.add(ListItem(title, link).apply {
                des = d
            })
            title = br.readLine()
        }
        br.close()
        return list
    }
}
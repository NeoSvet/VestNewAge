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
import ru.neosvet.vestnewage.viewmodel.basic.NeoToiler
import ru.neosvet.vestnewage.viewmodel.basic.ProgressState
import ru.neosvet.vestnewage.viewmodel.basic.SuccessList
import java.io.BufferedReader
import java.io.FileReader

class SummaryToiler : NeoToiler() {
    private var sBack: String = ""

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
        mstate.postValue(SuccessList(list))
        loadPages(list)
    }

    private fun loadPages(pages: List<ListItem>) {
        val loader = PageLoader()
        var cur = 0
        pages.forEach { item ->
            loader.download(item.link, false)
            if (isRun.not())
                return@forEach
            cur++
            mstate.postValue(ProgressState(cur.percent(pages.size)))
        }
        loader.finish()
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, SummaryHelper.TAG)
        .build()

    fun openList(loadIfNeed: Boolean) {
        this.loadIfNeed = loadIfNeed
        scope.launch {
            val list = openList()
            if (loadIfNeed && list.isEmpty())
                reLoad()
            else
                mstate.postValue(SuccessList(list))
        }
    }

    private fun openList(): List<ListItem> {
        val list = mutableListOf<ListItem>()
        val dateNow = DateUnit.initNow()
        val br = BufferedReader(FileReader(Lib.getFile(Const.RSS)))
        var title: String? = br.readLine()
        var des: String
        while (title != null) {
            val link = br.readLine()
            des = br.readLine()
            val time = br.readLine()
            des = dateNow.getDiffDate(time.toLong()) +
                    sBack + Const.N + des
            list.add(ListItem(title, link).apply {
                setDes(des)
            })
            title = br.readLine()
        }
        br.close()
        return list
    }
}
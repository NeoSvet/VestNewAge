package ru.neosvet.vestnewage.model

import android.content.Context
import androidx.work.Data
import kotlinx.coroutines.launch
import ru.neosvet.utils.Const
import ru.neosvet.utils.Lib
import ru.neosvet.utils.percent
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.helpers.DateHelper
import ru.neosvet.vestnewage.helpers.ProgressHelper
import ru.neosvet.vestnewage.helpers.SummaryHelper
import ru.neosvet.vestnewage.list.ListItem
import ru.neosvet.vestnewage.loader.PageLoader
import ru.neosvet.vestnewage.loader.SummaryLoader
import ru.neosvet.vestnewage.model.basic.NeoViewModel
import ru.neosvet.vestnewage.model.basic.ProgressState
import ru.neosvet.vestnewage.model.basic.SuccessList
import java.io.BufferedReader
import java.io.FileReader

class SummaryModel : NeoViewModel() {
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

    override fun onDestroy() {
    }

    override fun getInputData(): Data = Data.Builder()
        .putString(Const.TASK, "Summary")
        .build()

    fun openList(loadIfNeed: Boolean) {
        this.loadIfNeed = loadIfNeed
        scope.launch {
            val list = openList()
            mstate.postValue(SuccessList(list))
        }
    }

    private fun openList(): List<ListItem> {
        val list = mutableListOf<ListItem>()
        val dateNow = DateHelper.initNow()
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
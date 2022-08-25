package ru.neosvet.vestnewage.data

import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R

sealed class MyException(message: String) : Exception(message) {
    class BaseIsBusy: MyException(App.context.getString(R.string.busy_base_error))
    class SiteUnavailable: MyException(App.context.getString(R.string.site_unavailable))
    class SiteNoResponse: MyException(App.context.getString(R.string.site_no_response))
    class SiteCode(code: Int): MyException(App.context.getString(R.string.site_code) + code)
}
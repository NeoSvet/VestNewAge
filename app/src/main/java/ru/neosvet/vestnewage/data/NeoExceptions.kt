package ru.neosvet.vestnewage.data

import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R

sealed class NeoException(message: String) : Exception(message) {
    class BaseIsBusy: NeoException(App.context.getString(R.string.busy_base_error))
    class SiteUnavailable : NeoException(App.context.getString(R.string.network_issues))
    class SiteNoResponse : NeoException(App.context.getString(R.string.network_issues))
    class SiteCode(code: Int): NeoException(App.context.getString(R.string.site_code).format(code))
}
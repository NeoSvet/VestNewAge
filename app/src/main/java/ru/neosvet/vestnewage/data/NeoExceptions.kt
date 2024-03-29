package ru.neosvet.vestnewage.data

import ru.neosvet.vestnewage.App
import ru.neosvet.vestnewage.R

sealed class NeoException(message: String) : Exception(message) {
    class BaseIsBusy: NeoException(App.context.getString(R.string.busy_base_error))
    //TODO class SiteUnavailable: NeoException(App.context.getString(R.string.site_unavailable))
    class SiteNoResponse: NeoException(App.context.getString(R.string.site_no_response))
    class SiteCode(code: Int): NeoException(App.context.getString(R.string.site_code).format(code))
}
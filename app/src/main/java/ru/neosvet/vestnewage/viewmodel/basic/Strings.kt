package ru.neosvet.vestnewage.viewmodel.basic

data class SearchStrings(
    val format_search_date: String,
    val format_search_proc: String,
    val format_month_no_loaded: String,
    val format_page_no_loaded: String,
    val format_load: String,
    val not_found: String,
    val search_in_results: String,
    val search_mode: Array<String>,
    val format_found: String
)

data class BrowserStrings(
    val copyright: String,
    val downloaded: String,
    val doctrine_pages: String,
    val holy_rus_pages: String,
    val doctrine_future: String,
    val edition_of: String,
    val publication_of: String,
    val toPrev: String,
    val toNext: String,
    val searchReaction: String,
    val not_found_reaction: String,
    val foot_reaction: String
)

data class SiteStrings(
    val novosti: String,
    val mark_read: String,
    val today: String,
    val unread: String,
    val timekeeping: String,
    val path: String
)

data class DevStrings(
    val ad: String,
    val ok: String,
    val url_on_google: String,
    val url_on_huawei: String,
    val open_link: String,
    val access_new_version: String,
    val current_version: String
)

data class HomeStrings(
    val nothing: String,
    val new: String,
    val on_tab: String,
    val never: String,
    val refreshed: String,
    val today_empty: String,
    val yesterday: String,
    val journal: String,
    val calendar: String,
    val summary: String,
    val doctrine: String,
    val academy: String,
    val news: String,
    val book: String,
    val markers: String,
    val precept_human_future: String,
    val additionally_from_tg: String,
    val today_msk: String,
    val back: String,
    val last_post_from: String,
    val last_readed: String,
    val prom_for_soul_unite: String,
    val new_dev_ads: String,
    val last: String,
    val from: String,
    val new_today: String,
    val information: String,
    val help_edit: String
)

data class BookStrings(
    val rnd_epistle: String,
    val rnd_poem: String,
    val rnd_verse: String,
    val alert_rnd: String,
    val try_again: String,
    val from: String,
    val pred_tolk: String
)

data class MarkersStrings(
    val collections: String,
    val no_collections: String,
    val sel_pos: String,
    val sel_par: String,
    val pos_n: String,
    val par_n: String,
    val page_entirely: String,
    val unuse_dot: String,
    val cancel_rename: String,
    val help_edit: String
)

data class MarkerStrings(
    val sel_pos: String,
    val sel_par: String,
    val sel_col: String,
    val page_entirely: String,
    val unuse_dot: String,
    val title_already_used: String,
    val no_collections: String,
    val need_set_check: String
)

data class CabinetStrings(
    val selected_status: String,
    val auth_failed: String,
    val anketa_failed: String,
    val send_status: String,
    val select_status: String,
    val send_unlivable: String
)

data class HelpStrings(
    val srv_info: String,
    val format_info: String,
    val feedback: Array<String>,
    val tips: Array<String>
)

data class JournalStrings(
    val format_opened: String,
    val format_rnd: String,
    val back: String,
    val rnd_poem: String,
    val rnd_epistle: String,
    val rnd_verse: String
)
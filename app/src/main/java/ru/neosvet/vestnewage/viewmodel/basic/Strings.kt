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
    val toPrev: String,
    val toNext: String
)

data class SiteStrings(
    val news_dev: String,
    val novosti: String,
    val back_title: String,
    val back_des: String
)

data class BookStrings(
    val rnd_epistle: String,
    val rnd_poem: String,
    val rnd_verse: String,
    val alert_rnd: String,
    val try_again: String,
    val from: String,
    val month_is_empty: String
)

data class MarkersStrings(
    val collections: String,
    val no_collections: String,
    val sel_pos: String,
    val sel_par: String,
    val pos_n: String,
    val par_n: String,
    val page_entirely: String,
    val not_load_page: String,
    val unuse_dot: String,
    val cancel_rename: String
)

data class MarkerStrings(
    val sel_pos: String,
    val sel_par: String,
    val sel_col: String,
    val page_entirely: String,
    val unuse_dot: String,
    val title_already_used: String,
    val no_collections: String
)

data class CabinetStrings(
    val selected_status: String,
    val anketa_failed: String,
    val send_status: String,
    val select_status: String,
    val send_unlivable: String
)

data class HelpStrings(
    val srv_info: String,
    val write_to_dev: String,
    val link_on_app: String,
    val tg_channel: String,
    val page_app: String,
    val changelog: String,
    val format_info: String
)

data class JournalStrings(
    val format_time_back: String,
    val rnd_poem: String,
    val rnd_epistle: String,
    val rnd_verse: String
)
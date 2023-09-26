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
    val edition_of: String,
    val toPrev: String,
    val toNext: String
)

data class HomeStrings(
    val nothing: String,
    val never: String,
    val refreshed: String,
    val today_empty: String,
    val journal: String,
    val calendar: String,
    val summary: String,
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
    val has_changes: String,
    val no_changes: String
)

data class BookStrings(
    val rnd_epistle: String,
    val rnd_poem: String,
    val rnd_verse: String,
    val alert_rnd: String,
    val try_again: String,
    val from: String
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
    val no_collections: String,
    val need_set_check: String
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
    val format_info: String,
    val feedback: Array<String>,
    val tips: Array<String>
)

data class JournalStrings(
    val format_time_back: String,
    val back: String,
    val rnd_poem: String,
    val rnd_epistle: String,
    val rnd_verse: String
)
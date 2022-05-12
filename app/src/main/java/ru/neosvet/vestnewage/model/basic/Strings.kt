package ru.neosvet.vestnewage.model.basic

data class SearchStrings(
    val format_search_date: String,
    val format_search_proc: String,
    val search_in_results: String,
    val search_mode: Array<String>,
    val format_found: String
)

data class BrowserStrings(
    val page: String,
    val copyright: String,
    val downloaded: String,
    val endList: String,
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
    val rnd_pos: String,
    val rnd_kat: String,
    val rnd_stih: String,
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

data class CabinetStrings(
    val selected_status: String,
    val anketa_failed: String,
    val send_status: String,
    val select_status: String,
    val send_unlivable: String
)
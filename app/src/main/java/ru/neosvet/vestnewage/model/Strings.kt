package ru.neosvet.vestnewage.model

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
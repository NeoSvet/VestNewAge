package ru.neosvet.vestnewage.model

data class SearchStrings(
    val format_search_date: String,
    val format_search_proc: String,
    val search_in_results: String,
    val search_mode: Array<String>,
    val format_found: String
)
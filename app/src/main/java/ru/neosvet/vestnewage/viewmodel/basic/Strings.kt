package ru.neosvet.vestnewage.viewmodel.basic

data class SearchStrings(
    val formatDate: String,
    val formatProc: String,
    val formatMonthNoLoaded: String,
    val formatPageNoLoaded: String,
    val formatLoad: String,
    val notFound: String,
    val searchInResults: String,
    val listMode: List<String>,
    val formatFound: String
)

data class BrowserStrings(
    val copyright: String,
    val downloaded: String,
    val doctrinePages: String,
    val holyRusPages: String,
    val doctrineFuture: String,
    val editionOf: String,
    val publicationOf: String,
    val toPrev: String,
    val toNext: String,
    val searchReaction: String,
    val notFoundReaction: String,
    val footReaction: String
)

data class SiteStrings(
    val novosti: String,
    val markRead: String,
    val today: String,
    val unread: String,
    val timekeeping: String,
    val path: String
)

data class DevStrings(
    val ad: String,
    val ok: String,
    val urlOnGoogle: String,
    val urlOnHuawei: String,
    val openLink: String,
    val accessNewVersion: String,
    val currentVersion: String
)

data class HomeStrings(
    val nothing: String,
    val new: String,
    val onTab: String,
    val never: String,
    val refreshed: String,
    val todayEmpty: String,
    val yesterday: String,
    val journal: String,
    val calendar: String,
    val summary: String,
    val doctrine: String,
    val academy: String,
    val news: String,
    val book: String,
    val markers: String,
    val preceptHumanFuture: String,
    val additionallyFromTg: String,
    val todayMsk: String,
    val back: String,
    val lastPostFrom: String,
    val lastRead: String,
    val promForSoulUnite: String,
    val newDevAds: String,
    val last: String,
    val from: String,
    val newToday: String,
    val information: String,
    val helpEdit: String
)

data class BookStrings(
    val rndEpistle: String,
    val rndPoem: String,
    val rndVerse: String,
    val alertRnd: String,
    val tryAgain: String,
    val from: String,
    val predTolk: String
)

data class MarkersStrings(
    val collections: String,
    val noCollections: String,
    val selectedPosition: String,
    val selectedPar: String,
    val aroundAt: String,
    val parNumbers: String,
    val pageEntirely: String,
    val unusedDot: String,
    val cancelRename: String,
    val helpEdit: String
)

data class MarkerStrings(
    val selectedPosition: String,
    val selectedPar: String,
    val selectedCollections: String,
    val pageEntirely: String,
    val unusedDot: String,
    val titleAlreadyUsed: String,
    val noCollections: String,
    val needSetCheck: String
)

data class CabinetStrings(
    val selectedStatus: String,
    val authFailed: String,
    val profileFailed: String,
    val sendStatus: String,
    val selectStatus: String,
    val sendUnlivable: String
)

data class HelpStrings(
    val srvInfo: String,
    val formatInfo: String,
    val feedback: List<String>,
    val tips: List<String>
)

data class JournalStrings(
    val formatOpened: String,
    val formatRnd: String,
    val back: String,
    val rndPoem: String,
    val rndEpistle: String,
    val rndVerse: String
)
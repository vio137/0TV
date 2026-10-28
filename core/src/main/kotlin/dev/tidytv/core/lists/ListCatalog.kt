package dev.tidytv.core.lists

/** All bundled lists. Rejected lists are kept aside, never offered. */
class ListCatalog(sources: Map<String, String>) {
    val lists: List<DebloatList>
    val rejected: List<ParseResult.Rejected>

    init {
        val results = sources.toSortedMap().map { (id, text) -> ListParser.parse(id, text) }
        lists = results.filterIsInstance<ParseResult.Ok>().map { it.list }
        rejected = results.filterIsInstance<ParseResult.Rejected>()
    }

    fun byId(id: String): DebloatList? = lists.firstOrNull { it.id == id }
}

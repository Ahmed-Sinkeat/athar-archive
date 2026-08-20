package com.atharchive.feature.library

enum class LibraryShelf(val key: String, val label: String) {
    Continue("continue", "واصل القراءة"),
    ReadLater("readLater", "للقراءة لاحقًا"),
    Reading("reading", "أقرأ الآن"),
    Finished("finished", "أنهيت"),
    Downloaded("downloaded", "المحمّلة"),
    Recent("recent", "الأخيرة"),
}

data class LibraryCollectionUi(
    val id: String,
    val title: String,
    val itemCount: Int,
)

data class LibraryWorkUi(
    val id: String,
    val title: String,
    val author: String,
    val collection: String,
    val kind: String,
    val status: LibraryShelf?,
    val progress: Float?,
    val progressLabel: String?,
    val downloaded: Boolean,
    val recentAt: Long?,
    val collectionIds: Set<String>,
)

data class LibraryUiState(
    val works: List<LibraryWorkUi> = emptyList(),
    val collections: List<LibraryCollectionUi> = emptyList(),
) {
    fun worksFor(shelf: LibraryShelf, collectionId: String?): List<LibraryWorkUi> {
        val selected = if (collectionId != null) {
            works.filter { collectionId in it.collectionIds }
        } else {
            when (shelf) {
                LibraryShelf.Continue -> works.filter { (it.progress ?: 0f) in 0.0001f..0.9999f }
                LibraryShelf.ReadLater -> works.filter { it.status == LibraryShelf.ReadLater }
                LibraryShelf.Reading -> works.filter { it.status == LibraryShelf.Reading }
                LibraryShelf.Finished -> works.filter { it.status == LibraryShelf.Finished }
                LibraryShelf.Downloaded -> works.filter(LibraryWorkUi::downloaded)
                LibraryShelf.Recent -> works.filter { it.recentAt != null }
            }
        }
        return when {
            collectionId != null -> selected.sortedBy(LibraryWorkUi::title)
            shelf == LibraryShelf.Recent -> selected.sortedByDescending(LibraryWorkUi::recentAt)
            shelf == LibraryShelf.Continue -> selected.sortedByDescending { it.recentAt ?: 0L }
            else -> selected.sortedBy(LibraryWorkUi::title)
        }
    }
}

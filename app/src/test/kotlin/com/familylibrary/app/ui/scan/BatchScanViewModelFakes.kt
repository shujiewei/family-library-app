package com.familylibrary.app.ui.scan

import com.familylibrary.app.data.cover.IsbnTitleLookup
import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.entity.BookWithLocation
import com.familylibrary.app.data.repository.BatchAddResult
import com.familylibrary.app.data.repository.BatchScanBooksGateway
import kotlinx.coroutines.delay

internal class FakeBatchScanBooksGateway : BatchScanBooksGateway {
    var findByIsbnResult: BookWithLocation? = null
    val addScannedCalls = mutableListOf<Pair<List<Book>, Long>>()
    var addScannedResult: BatchAddResult = BatchAddResult(addedCount = 0, failures = emptyList())

    override suspend fun findByIsbn(isbn: String): BookWithLocation? = findByIsbnResult

    override suspend fun addScannedBooks(books: List<Book>, shelfRowId: Long): BatchAddResult {
        addScannedCalls.add(books to shelfRowId)
        return addScannedResult
    }
}

internal class FakeIsbnTitleLookup : IsbnTitleLookup {
    private val titles = mutableMapOf<String, String?>()
    var lookupDelayMs: Long = 0
    var shouldThrow: Boolean = false

    fun setTitle(isbn: String, title: String?) {
        titles[isbn] = title
    }

    override suspend fun lookupTitle(isbn: String): String? {
        if (lookupDelayMs > 0) delay(lookupDelayMs)
        if (shouldThrow) error("network error")
        return titles[isbn]
    }
}

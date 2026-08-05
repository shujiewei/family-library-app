package com.familylibrary.app.data.repository

import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.entity.BookWithLocation

/** 扫码批量录入所需的图书库访问（便于 ViewModel 单元测试 mock） */
interface BatchScanBooksGateway {
    suspend fun findByIsbn(isbn: String): BookWithLocation?
    suspend fun addScannedBooks(books: List<Book>, shelfRowId: Long): BatchAddResult
}

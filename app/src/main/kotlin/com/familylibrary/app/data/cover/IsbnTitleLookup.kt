package com.familylibrary.app.data.cover

/** ISBN 书名查询（便于 ViewModel 单元测试 mock） */
fun interface IsbnTitleLookup {
    suspend fun lookupTitle(isbn: String): String?
}

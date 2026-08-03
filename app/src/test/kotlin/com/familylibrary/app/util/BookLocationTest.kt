package com.familylibrary.app.util

import com.familylibrary.app.data.entity.BookWithLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class BookLocationTest {

    @Test
    fun locationLabel_withShelfAndRow() {
        val item = BookWithLocation(
            book = com.familylibrary.app.data.entity.Book(title = "三体"),
            bookshelfName = "客厅",
            shelfRowName = "第2排",
        )
        assertEquals("客厅 / 第2排", item.locationLabel())
    }

    @Test
    fun locationLabel_unshelved() {
        val item = BookWithLocation(
            book = com.familylibrary.app.data.entity.Book(title = "三体"),
            bookshelfName = null,
            shelfRowName = null,
        )
        assertEquals("未上架", item.locationLabel())
    }
}

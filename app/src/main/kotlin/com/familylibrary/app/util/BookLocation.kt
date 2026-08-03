package com.familylibrary.app.util

import com.familylibrary.app.data.entity.BookWithLocation

fun BookWithLocation.locationLabel(): String = buildString {
    if (bookshelfName != null) {
        append(bookshelfName)
        if (shelfRowName != null) {
            append(" / ")
            append(shelfRowName)
        }
    } else {
        append("未上架")
    }
}

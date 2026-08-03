package com.familylibrary.app.util

import com.familylibrary.app.data.cover.CoverService

/** 判断是否为占位书名（非真实书名） */
fun isPlaceholderTitle(title: String, isbn: String = ""): Boolean {
    val t = title.trim()
    if (t.isBlank()) return true
    val n = CoverService.normalizeIsbn(isbn)
    if (n.isNotBlank() && (t == n || t.equals("ISBN $n", ignoreCase = true))) return true
    return t.startsWith("ISBN ", ignoreCase = true) && t.length <= 20
}

/** 保存图书时书名是否有效 */
fun hasValidTitle(title: String, isbn: String = ""): Boolean =
    !isPlaceholderTitle(title, isbn)

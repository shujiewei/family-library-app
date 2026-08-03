package com.familylibrary.app.data

/** 归档书架：下架图书统一移入此处，不占正常书架空间 */
object ArchiveConfig {
    const val SHELF_NAME = "归档"
    const val ROW_NAME = "默认"

    fun isArchiveShelf(name: String): Boolean = name == SHELF_NAME
}

package com.familylibrary.app.data.entity

/** 封面来源与拉取状态 */
object CoverMeta {
    const val SOURCE_NONE = "none"
    const val SOURCE_ISBN = "isbn"
    const val SOURCE_CUSTOM = "custom"

    const val STATUS_NONE = "none"
    const val STATUS_LOADING = "loading"
    const val STATUS_OK = "ok"
    const val STATUS_FAILED = "failed"

    fun statusLabel(source: String, status: String, hasCover: Boolean): String = when {
        source == SOURCE_CUSTOM -> "自定义封面"
        status == STATUS_LOADING -> "封面拉取中…"
        status == STATUS_FAILED -> "网络封面拉取失败"
        source == SOURCE_ISBN && hasCover -> "网络封面"
        source == SOURCE_ISBN -> "暂无封面"
        else -> "暂无封面"
    }
}

package com.familylibrary.app.data.cover

/**
 * 从书目 API JSON 解析封面 URL（纯函数，便于单测）。
 */
object CoverUrlResolver {

    /** Open Library books API → cover.large / medium / small */
    fun openLibraryCoverUrls(json: String, isbn: String): List<String> {
        val key = "ISBN:${CoverService.normalizeIsbn(isbn)}"
        if (!json.contains("\"$key\"")) return emptyList()
        return listOf("large", "medium", "small")
            .mapNotNull { size -> extractOpenLibraryCoverSize(json, size) }
            .distinct()
    }

    /** Open Library JSON 里的 isbn_10，用于二次查封面 */
    fun openLibraryIsbn10(json: String): String? {
        val regex = """"isbn_10"\s*:\s*\[\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.let { CoverService.normalizeIsbn(it) }
    }

    /** Google Books volume JSON → 各档缩略图 */
    fun googleBooksCoverUrls(json: String): List<String> {
        if (json.contains("\"totalItems\": 0") || json.contains("\"totalItems\":0")) return emptyList()
        return listOf("extraLarge", "large", "medium", "small", "thumbnail", "smallThumbnail")
            .mapNotNull { field -> extractJsonString(json, field) }
            .distinct()
    }

    /** longitood API: {"url":"https://..."} */
    fun longitoodCoverUrl(json: String): String? {
        if (json.contains("\"error\"")) return null
        return extractJsonString(json, "url")
    }

    private fun extractOpenLibraryCoverSize(json: String, size: String): String? =
        extractJsonString(
            json,
            size,
            """"cover"\s*:\s*\{[^}]*"$size"\s*:\s*"((?:\\.|[^"\\])*)"""",
        )

    private fun extractJsonString(json: String, field: String, pattern: String? = null): String? {
        val regex = (pattern ?: """"$field"\s*:\s*"((?:\\.|[^"\\])*)"""").toRegex()
        return regex.find(json)?.groupValues?.get(1)
            ?.replace("\\/", "/")
            ?.replace("\\u0026", "&")
    }
}

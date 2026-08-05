package com.familylibrary.app.data.cover

/**
 * 纯函数解析 Open Library / Google Books JSON，便于单元测试。
 */
object BookMetadataParser {

    data class ParsedBook(
        val title: String,
        val author: String = "",
        val publisher: String = "",
        val pageCount: Int = 0,
        val description: String = "",
    )

    fun parseOpenLibrary(json: String, isbn: String): ParsedBook? {
        val key = "ISBN:${CoverService.normalizeIsbn(isbn)}"
        if (!json.contains("\"$key\"")) return null
        val title = extractJsonString(json, "title") ?: return null
        return ParsedBook(
            title = title,
            author = extractOpenLibraryAuthors(json),
            publisher = extractOpenLibraryPublishers(json),
            pageCount = extractJsonInt(json, "number_of_pages") ?: 0,
            description = extractJsonString(json, "notes") ?: "",
        )
    }

    fun parseGoogleBooks(json: String): ParsedBook? {
        if (json.contains("\"totalItems\": 0") || json.contains("\"totalItems\":0")) return null
        val title = extractJsonString(json, "title") ?: return null
        return ParsedBook(
            title = title,
            author = extractGoogleAuthors(json),
            publisher = extractJsonString(json, "publisher") ?: "",
            pageCount = extractJsonInt(json, "pageCount") ?: 0,
            description = extractJsonString(json, "description") ?: "",
        )
    }

    private fun extractOpenLibraryAuthors(json: String): String {
        val regex = """"authors"\s*:\s*\[\s*\{[^}]*"name"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
        return regex.findAll(json).map { it.groupValues[1] }.joinToString("、")
    }

    private fun extractOpenLibraryPublishers(json: String): String {
        val regex = """"publishers"\s*:\s*\[\s*\{[^}]*"name"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
        return regex.findAll(json).map { it.groupValues[1] }.joinToString("、")
    }

    private fun extractGoogleAuthors(json: String): String {
        val regex = """"authors"\s*:\s*\[(.*?)]""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val block = regex.find(json)?.groupValues?.get(1) ?: return ""
        return """"([^"]+)"""".toRegex().findAll(block).map { it.groupValues[1] }.joinToString("、")
    }

    private fun extractJsonString(json: String, field: String): String? {
        val regex = """"$field"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
            ?.replace("\\n", "\n")
            ?.replace("\\u0026", "&")
    }

    private fun extractJsonInt(json: String, field: String): Int? {
        val regex = """"$field"\s*:\s*(\d+)""".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }
}

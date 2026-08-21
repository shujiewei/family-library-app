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

    /** Open Library search.json → docs[0] */
    fun parseOpenLibrarySearch(json: String): ParsedBook? {
        if (json.contains("\"numFound\":0") || json.contains("\"numFound\": 0")) return null
        if (!json.contains("\"docs\"")) return null
        val title = extractFirstDocString(json, "title") ?: return null
        return ParsedBook(
            title = title,
            author = extractFirstDocStringArray(json, "author_name"),
            publisher = extractFirstDocStringArray(json, "publisher"),
            pageCount = extractFirstDocInt(json, "number_of_pages_median") ?: 0,
            description = "",
        )
    }

    /** 豆瓣 ISBN 页 HTML（国内中文书优先数据源） */
    fun parseDouban(html: String, isbn: String): ParsedBook? {
        if (html.contains("没有找到关于") || html.contains("条目不存在")) return null
        val normalized = CoverService.normalizeIsbn(isbn)
        if (normalized.isBlank() || !html.contains(normalized)) return null

        val title = extractMetaContent(html, "og:title")
            ?.substringBefore(" (豆瓣)")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: extractDoubanH1(html)
            ?: return null

        return ParsedBook(
            title = title,
            author = extractDoubanField(html, "作者") ?: "",
            publisher = extractDoubanField(html, "出版社") ?: "",
            pageCount = extractDoubanField(html, "页数")?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
            description = "",
        )
    }

    fun doubanCoverUrl(html: String): String? =
        extractMetaContent(html, "og:image")?.takeIf { it.contains("doubanio.com") }

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

    private fun extractFirstDocString(json: String, field: String): String? {
        val docsIndex = json.indexOf("\"docs\"")
        if (docsIndex < 0) return null
        val slice = json.substring(docsIndex)
        return extractJsonString(slice, field)
    }

    private fun extractFirstDocInt(json: String, field: String): Int? {
        val docsIndex = json.indexOf("\"docs\"")
        if (docsIndex < 0) return null
        return extractJsonInt(json.substring(docsIndex), field)
    }

    private fun extractFirstDocStringArray(json: String, field: String): String {
        val docsIndex = json.indexOf("\"docs\"")
        if (docsIndex < 0) return ""
        val slice = json.substring(docsIndex)
        val regex = """"$field"\s*:\s*\[\s*"((?:\\.|[^"\\])*)"""".toRegex()
        return regex.findAll(slice).map { it.groupValues[1] }.joinToString("、")
    }

    private fun extractMetaContent(html: String, property: String): String? {
        val regex =
            """<meta\s+property="$property"\s+content="((?:\\.|[^"\\])*)"""".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.get(1)
            ?.replace("&amp;", "&")
            ?.trim()
    }

    private fun extractDoubanH1(html: String): String? {
        val spanRegex =
            """<h1[^>]*>[\s\S]*?<span[^>]*property="v:itemreviewed"[^>]*>([\s\S]*?)</span>""".toRegex()
        spanRegex.find(html)?.groupValues?.get(1)?.let { return it.trim() }
        val h1Regex = """<h1[^>]*>([\s\S]*?)</h1>""".toRegex()
        return h1Regex.find(html)?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractDoubanField(html: String, label: String): String? {
        val plRegex =
            """<span\s+class="pl">\s*$label\s*</span>\s*:\s*(?:<a[^>]*>)?([^<\n]+)""".toRegex()
        plRegex.find(html)?.groupValues?.get(1)?.trim()?.trimEnd('/')?.trim()?.let { return it }

        val plainRegex = """$label\s*[:：]\s*([^<\n]+?)(?=(?:出版社|出版年|页数|ISBN|装帧)\s*[:：]|$)"""
            .toRegex()
        return plainRegex.find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }
}

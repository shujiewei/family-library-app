package com.familylibrary.app.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Hash {
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun sha256(input: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest("$input$salt".toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, salt: String, hash: String): Boolean =
        sha256(pin, salt) == hash
}

object DateUtil {
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun today(): String = fmt.format(Date())

    fun format(epochMs: Long): String = fmt.format(Date(epochMs))
}

object Json {
    fun string(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    fun stringOrNull(s: String?): String = if (s == null) "null" else string(s)

    fun <T> array(items: List<T>, toJson: (T) -> String): String =
        items.joinToString(prefix = "[", postfix = "]", transform = toJson)

    fun parseObject(json: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val trimmed = json.trim().removePrefix("{").removeSuffix("}")
        if (trimmed.isBlank()) return result
        var i = 0
        val chars = trimmed.toCharArray()
        while (i < chars.size) {
            while (i < chars.size && chars[i].isWhitespace()) i++
            if (i >= chars.size) break
            val keyStart = i + 1
            while (i < chars.size && chars[i] != '"') i++
            val key = trimmed.substring(keyStart, i)
            i++
            while (i < chars.size && chars[i] != ':') i++
            i++
            while (i < chars.size && chars[i].isWhitespace()) i++
            val (value, consumed) = parseValue(trimmed, i)
            result[key] = value
            i = consumed
            while (i < chars.size && (chars[i].isWhitespace() || chars[i] == ',')) i++
        }
        return result
    }

    private fun parseValue(s: String, start: Int): Pair<Any?, Int> {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length) return null to i
        return when (s[i]) {
            '"' -> {
                val sb = StringBuilder()
                i++
                while (i < s.length) {
                    if (s[i] == '\\') {
                        i++
                        if (i < s.length) sb.append(s[i])
                    } else if (s[i] == '"') break
                    else sb.append(s[i])
                    i++
                }
                i++
                sb.toString() to i
            }
            '[' -> {
                val items = mutableListOf<Any?>()
                i++
                while (i < s.length && s[i] != ']') {
                    while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++
                    if (i < s.length && s[i] == ']') break
                    val (v, next) = parseValue(s, i)
                    items.add(v)
                    i = next
                }
                i++
                items to i
            }
            'n' -> {
                i += 4
                null to i
            }
            't' -> {
                i += 4
                true to i
            }
            'f' -> {
                i += 5
                false to i
            }
            else -> {
                val numStart = i
                while (i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+')) i++
                val numStr = s.substring(numStart, i)
                val num = if (numStr.contains('.')) numStr.toDouble() else numStr.toLong()
                num to i
            }
        }
    }
}

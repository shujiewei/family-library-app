package com.familylibrary.app.data.cover

import java.net.HttpURLConnection
import java.net.URL

/** ISBN 查询 / 封面下载共用的 HTTP 工具（浏览器 UA，便于访问豆瓣等站点） */
internal object IsbnHttp {

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 FamilyLibrary/1.1.5"

    fun getString(
        url: String,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
        accept: String = "application/json,*/*",
    ): String? = getBytes(url, connectTimeoutMs, readTimeoutMs, accept)?.toString(Charsets.UTF_8)

    fun getBytes(
        url: String,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
        accept: String = "image/*,application/json,*/*",
    ): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 5_000
}

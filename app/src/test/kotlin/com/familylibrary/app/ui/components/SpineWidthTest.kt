package com.familylibrary.app.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

class SpineWidthTest {

    @Test
    fun shortTitle_narrowSpine() {
        assertTrue(spineWidthDp("三体") in 28..36)
    }

    @Test
    fun longTitle_widerSpine() {
        assertTrue(spineWidthDp("哈利波特与魔法石") > spineWidthDp("三体"))
    }

    @Test
    fun veryLongTitle_cappedAt52() {
        val title = "这是一个非常非常长的书名用来测试书脊最大宽度限制"
        assertTrue(spineWidthDp(title) <= 52)
    }
}

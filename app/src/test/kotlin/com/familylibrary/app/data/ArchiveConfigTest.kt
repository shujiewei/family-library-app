package com.familylibrary.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveConfigTest {

    @Test
    fun archiveShelfName() {
        assertTrue(ArchiveConfig.isArchiveShelf("归档"))
        assertFalse(ArchiveConfig.isArchiveShelf("客厅"))
    }
}

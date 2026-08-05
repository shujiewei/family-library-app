package com.familylibrary.app.ui.scan

import com.familylibrary.app.data.entity.Book
import com.familylibrary.app.data.entity.BookWithLocation
import com.familylibrary.app.data.repository.BatchAddResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchScanViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val targetRowId = 42L
    private val targetLabel = "客厅 / 第一排"
    private val validIsbn = "9780306406157"

    private lateinit var bookGateway: FakeBatchScanBooksGateway
    private lateinit var titleLookup: FakeIsbnTitleLookup
    private lateinit var viewModel: BatchScanViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        bookGateway = FakeBatchScanBooksGateway()
        titleLookup = FakeIsbnTitleLookup()
        viewModel = BatchScanViewModel(
            bookRepo = bookGateway,
            lookup = titleLookup,
            targetRowId = targetRowId,
            targetLabel = targetLabel,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun invalidBarcode_showsMessageAndDoesNotAddItem() = runTest {
        viewModel.onIsbnScanned("12345")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.items.isEmpty())
        assertEquals("条码无法识别为 ISBN：12345", state.lastMessage)
    }

    @Test
    fun scan_lookupTitle_success_readyToSave() = runTest {
        titleLookup.setTitle(validIsbn, "三体")

        viewModel.onIsbnScanned(validIsbn)
        advanceUntilIdle()

        val item = viewModel.state.value.items.single()
        assertEquals(validIsbn, item.isbn)
        assertEquals("三体", item.title)
        assertFalse(item.isLookingUpTitle)
        assertFalse(item.needsManualTitle)
        assertFalse(item.isDuplicate)
        assertEquals(1, viewModel.readyToSaveCount())
        assertEquals("已获取书名：三体", viewModel.state.value.lastMessage)
    }

    @Test
    fun scan_lookupFails_manualTitle_thenSave() = runTest {
        titleLookup.setTitle(validIsbn, null)

        viewModel.onIsbnScanned(validIsbn)
        advanceUntilIdle()

        var item = viewModel.state.value.items.single()
        assertTrue(item.needsManualTitle)
        assertEquals(0, viewModel.readyToSaveCount())

        viewModel.updateItemTitle(validIsbn, "手动书名")
        item = viewModel.state.value.items.single()
        assertEquals("手动书名", item.title)
        assertFalse(item.needsManualTitle)
        assertEquals(1, viewModel.readyToSaveCount())

        bookGateway.addScannedResult = BatchAddResult(addedCount = 1, failures = emptyList())
        var saveResult: BatchAddResult? = null
        viewModel.saveAll { saveResult = it }
        advanceUntilIdle()

        val saved = bookGateway.addScannedCalls.single()
        assertEquals(targetRowId, saved.second)
        assertEquals("手动书名", saved.first.single().title)
        assertEquals(validIsbn, saved.first.single().isbn)
        assertEquals(1, saveResult?.addedCount)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun scan_existingBook_marksDuplicateAndBlocksSave() = runTest {
        bookGateway.findByIsbnResult = BookWithLocation(
            book = Book(id = 9, title = "已有图书", isbn = validIsbn, shelfRowId = 99),
            bookshelfName = "书房",
            shelfRowName = "第二排",
        )

        viewModel.onIsbnScanned(validIsbn)
        advanceUntilIdle()

        val item = viewModel.state.value.items.single()
        assertTrue(item.isDuplicate)
        assertEquals("已有图书", item.title)
        assertEquals(0, viewModel.readyToSaveCount())
        assertTrue(item.duplicateDetail.contains("已在库"))

        viewModel.saveAll { }
        advanceUntilIdle()
        assertTrue(bookGateway.addScannedCalls.isEmpty())
        assertEquals("没有可录入的新书（均为重复）", viewModel.state.value.lastMessage)
    }

    @Test
    fun scan_existingBookOnSameRow_showsAlreadyHereMessage() = runTest {
        bookGateway.findByIsbnResult = BookWithLocation(
            book = Book(id = 9, title = "已在排", isbn = validIsbn, shelfRowId = targetRowId),
            bookshelfName = "客厅",
            shelfRowName = "第一排",
        )

        viewModel.onIsbnScanned(validIsbn)
        advanceUntilIdle()

        val item = viewModel.state.value.items.single()
        assertTrue(item.duplicateDetail.contains("已在当前排"))
    }

    @Test
    fun saveWhileLookingUp_isBlocked() = runTest {
        titleLookup.lookupDelayMs = 5_000
        titleLookup.setTitle(validIsbn, "延迟书名")

        viewModel.onIsbnScanned(validIsbn)
        testDispatcher.scheduler.advanceTimeBy(100)

        val item = viewModel.state.value.items.single()
        assertTrue(item.isLookingUpTitle)
        assertEquals(0, viewModel.readyToSaveCount())

        viewModel.saveAll { }
        assertEquals("还有 1 本正在查询书名，请稍候", viewModel.state.value.lastMessage)
        assertTrue(bookGateway.addScannedCalls.isEmpty())

        advanceUntilIdle()
        assertEquals("延迟书名", viewModel.state.value.items.single().title)
        assertEquals(1, viewModel.readyToSaveCount())
    }

    @Test
    fun lookupThrows_needsManualTitle() = runTest {
        titleLookup.shouldThrow = true

        viewModel.onIsbnScanned(validIsbn)
        advanceUntilIdle()

        val item = viewModel.state.value.items.single()
        assertTrue(item.needsManualTitle)
        assertFalse(item.isLookingUpTitle)
        assertEquals("书名查询失败，请手动输入：$validIsbn", viewModel.state.value.lastMessage)
    }

    @Test
    fun fullFlow_scanLookupSave_clearsSavingFlag() = runTest {
        titleLookup.setTitle(validIsbn, "完整流程测试")
        bookGateway.addScannedResult = BatchAddResult(addedCount = 1, failures = emptyList())

        viewModel.onIsbnScanned(validIsbn)
        advanceUntilIdle()
        assertEquals(1, viewModel.readyToSaveCount())

        var result: BatchAddResult? = null
        viewModel.saveAll { result = it }
        advanceUntilIdle()

        assertEquals(1, result?.addedCount)
        assertFalse(viewModel.state.value.isSaving)
        assertEquals(
            listOf(Book(title = "完整流程测试", isbn = validIsbn)),
            bookGateway.addScannedCalls.single().first,
        )
    }
}

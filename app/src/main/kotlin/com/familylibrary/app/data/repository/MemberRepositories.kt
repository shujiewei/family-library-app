package com.familylibrary.app.data.repository

import com.familylibrary.app.data.dao.FamilyMemberDao
import com.familylibrary.app.data.dao.ReadingRecordDao
import com.familylibrary.app.data.dao.WishlistDao
import com.familylibrary.app.data.entity.FamilyMember
import com.familylibrary.app.data.entity.ReadingRecord
import com.familylibrary.app.data.entity.WishlistItem
import kotlinx.coroutines.flow.Flow

class MemberRepository(private val memberDao: FamilyMemberDao) {
    fun observeAll(): Flow<List<FamilyMember>> = memberDao.observeAll()

    suspend fun add(name: String, colorIndex: Int): Long =
        memberDao.insert(FamilyMember(name = name, colorIndex = colorIndex))

    suspend fun update(member: FamilyMember) = memberDao.update(member)

    suspend fun delete(id: Long) = memberDao.deleteById(id)
}

class ReadingRepository(private val readingDao: ReadingRecordDao) {
    fun observeAll() = readingDao.observeAllWithDetails()
    fun observeByMember(memberId: Long) = readingDao.observeByMember(memberId)
    fun observeMemberStats() = readingDao.observeMemberStats()
    fun observeCategoryStats(memberId: Long) = readingDao.observeCategoryStatsByMember(memberId)

    suspend fun addRecord(record: ReadingRecord): Long = readingDao.insert(record)

    suspend fun deleteRecord(id: Long) = readingDao.deleteById(id)
}

class WishlistRepository(private val wishlistDao: WishlistDao) {
    fun observeAll(): Flow<List<WishlistItem>> = wishlistDao.observeAll()

    suspend fun add(item: WishlistItem): Long = wishlistDao.insert(item)

    suspend fun findByIsbn(isbn: String): WishlistItem? = wishlistDao.findByIsbn(isbn)

    suspend fun update(item: WishlistItem) = wishlistDao.update(item)

    suspend fun delete(ids: List<Long>) {
        if (ids.isNotEmpty()) wishlistDao.deleteByIds(ids)
    }
}

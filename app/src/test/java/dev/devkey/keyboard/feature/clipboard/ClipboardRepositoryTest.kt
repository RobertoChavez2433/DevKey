package dev.devkey.keyboard.feature.clipboard

import dev.devkey.keyboard.data.db.dao.ClipboardHistoryDao
import dev.devkey.keyboard.data.db.entity.ClipboardHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ClipboardRepository].
 *
 * Uses an in-memory fake DAO to verify CRUD, search, and eviction logic
 * without needing Room or Android context.  Assertions check counts and
 * state values only -- never clipboard content (privacy rule).
 */
class ClipboardRepositoryTest {

    private lateinit var fakeDao: FakeClipboardHistoryDao
    private lateinit var repo: ClipboardRepository

    @Before
    fun setUp() {
        fakeDao = FakeClipboardHistoryDao()
        repo = ClipboardRepository(fakeDao)
    }

    // --- Test 1: CRUD cycle ---

    @Test
    fun `CRUD cycle addEntry pin unpin delete clearAll`() = runTest {
        // Add 3 entries.
        repo.addEntry("entry-1")
        repo.addEntry("entry-2")
        repo.addEntry("entry-3")
        assertEquals(3, fakeDao.getCount())

        // Pin the first entry.
        val firstId = fakeDao.entries[0].id
        repo.pin(firstId)
        val pinned = fakeDao.getEntryById(firstId)!!
        assertEquals(true, pinned.isPinned)

        // Unpin it.
        repo.unpin(firstId)
        val unpinned = fakeDao.getEntryById(firstId)!!
        assertEquals(false, unpinned.isPinned)

        // Delete second entry.
        val secondId = fakeDao.entries[1].id
        repo.delete(secondId)
        assertEquals(2, fakeDao.getCount())

        // clearAll removes everything.
        repo.clearAll()
        assertEquals(0, fakeDao.getCount())
    }

    // --- Test 2: search filters correctly ---

    @Test
    fun `search filters entries by query and returns correct count`() = runTest {
        repo.addEntry("alpha-one")
        repo.addEntry("beta-two")
        repo.addEntry("alpha-three")
        repo.addEntry("gamma-four")

        val alphaResults = repo.search("alpha").first()
        assertEquals(2, alphaResults.size)

        val betaResults = repo.search("beta").first()
        assertEquals(1, betaResults.size)

        val noResults = repo.search("zzz-missing").first()
        assertEquals(0, noResults.size)
    }

    // --- Test 3: clearAll preserves pinned entries ---

    @Test
    fun `clearAll preserves pinned entries`() = runTest {
        // Add 5 entries, pin 2 of them.
        for (i in 1..5) {
            repo.addEntry("item-$i")
        }
        assertEquals(5, fakeDao.getCount())

        val id1 = fakeDao.entries[0].id
        val id3 = fakeDao.entries[2].id
        repo.pin(id1)
        repo.pin(id3)

        // clearAll should preserve pinned entries.
        repo.clearAll()
        assertEquals(2, fakeDao.getCount())

        // Verify the surviving entries are the pinned ones.
        val remaining = fakeDao.entries
        assertEquals(true, remaining.all { it.isPinned })
    }

    // ---------------------------------------------------------------
    // Fake DAO
    // ---------------------------------------------------------------

    /**
     * In-memory implementation of [ClipboardHistoryDao] for testing.
     * Auto-generates IDs.  Never stores or logs actual content in assertions.
     */
    private class FakeClipboardHistoryDao : ClipboardHistoryDao {

        val entries = mutableListOf<ClipboardHistoryEntity>()
        private var nextId = 1L
        private val flow = MutableStateFlow<List<ClipboardHistoryEntity>>(emptyList())

        private fun emitUpdate() {
            flow.value = entries.sortedByDescending { it.timestamp }.toList()
        }

        override fun getAllEntries(): Flow<List<ClipboardHistoryEntity>> = flow

        override fun getPinnedEntries(): Flow<List<ClipboardHistoryEntity>> =
            flow.map { list -> list.filter { it.isPinned } }

        override suspend fun getEntryById(id: Long): ClipboardHistoryEntity? =
            entries.find { it.id == id }

        override suspend fun insert(entry: ClipboardHistoryEntity): Long {
            val id = nextId++
            entries.add(entry.copy(id = id))
            emitUpdate()
            return id
        }

        override suspend fun update(entry: ClipboardHistoryEntity) {
            val idx = entries.indexOfFirst { it.id == entry.id }
            if (idx >= 0) {
                entries[idx] = entry
                emitUpdate()
            }
        }

        override suspend fun delete(entry: ClipboardHistoryEntity) {
            entries.removeAll { it.id == entry.id }
            emitUpdate()
        }

        override suspend fun deleteUnpinned() {
            entries.removeAll { !it.isPinned }
            emitUpdate()
        }

        override suspend fun deleteAll() {
            entries.clear()
            emitUpdate()
        }

        override suspend fun getCount(): Int = entries.size

        override suspend fun deleteOldest(count: Int) {
            val unpinnedAsc = entries
                .filter { !it.isPinned }
                .sortedBy { it.timestamp }
            val toRemove = unpinnedAsc.take(count).map { it.id }.toSet()
            entries.removeAll { it.id in toRemove }
            emitUpdate()
        }

        override suspend fun getPinnedEntriesList(): List<ClipboardHistoryEntity> =
            entries.filter { it.isPinned }
    }
}

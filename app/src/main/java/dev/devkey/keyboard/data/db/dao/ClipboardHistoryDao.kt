package dev.devkey.keyboard.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.devkey.keyboard.data.db.entity.ClipboardHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardHistoryDao {

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<ClipboardHistoryEntity>>

    @Query("SELECT * FROM clipboard_history WHERE is_pinned = 1 ORDER BY timestamp DESC")
    fun getPinnedEntries(): Flow<List<ClipboardHistoryEntity>>

    @Query("SELECT * FROM clipboard_history WHERE id = :id")
    suspend fun getEntryById(id: Long): ClipboardHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipboardHistoryEntity): Long

    @Update
    suspend fun update(entry: ClipboardHistoryEntity)

    @Delete
    suspend fun delete(entry: ClipboardHistoryEntity)

    @Query("DELETE FROM clipboard_history WHERE is_pinned = 0")
    suspend fun deleteUnpinned()

    @Query("DELETE FROM clipboard_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM clipboard_history")
    suspend fun getCount(): Int

    @Query("DELETE FROM clipboard_history WHERE id IN (SELECT id FROM clipboard_history WHERE is_pinned = 0 ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}

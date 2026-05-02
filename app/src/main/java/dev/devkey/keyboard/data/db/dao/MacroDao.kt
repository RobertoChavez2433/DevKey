package dev.devkey.keyboard.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.devkey.keyboard.data.db.entity.MacroEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {

    @Query("SELECT * FROM macros ORDER BY usage_count DESC")
    fun getAllMacros(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun getMacroById(id: Long): MacroEntity?

    @Query("SELECT * FROM macros WHERE name = :name ORDER BY id DESC LIMIT 1")
    suspend fun getMacroByName(name: String): MacroEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(macro: MacroEntity): Long

    @Update
    suspend fun update(macro: MacroEntity)

    @Delete
    suspend fun delete(macro: MacroEntity)

    @Query("UPDATE macros SET usage_count = usage_count + 1 WHERE id = :id")
    suspend fun incrementUsageCount(id: Long)

    @Query("DELETE FROM macros")
    suspend fun deleteAll()

    @Query("DELETE FROM macros WHERE name = :name")
    suspend fun deleteByName(name: String): Int

    @Query("UPDATE macros SET name = :newName WHERE name = :oldName")
    suspend fun updateNameByName(oldName: String, newName: String): Int

    @Query("SELECT COUNT(*) FROM macros")
    suspend fun getCount(): Int

    @Query("SELECT * FROM macros ORDER BY usage_count DESC")
    suspend fun getAllMacrosList(): List<MacroEntity>
}

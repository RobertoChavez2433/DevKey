package dev.devkey.keyboard.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.devkey.keyboard.data.db.entity.CommandAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandAppDao {

    @Query("SELECT * FROM command_apps")
    fun getAllCommandApps(): Flow<List<CommandAppEntity>>

    @Query("SELECT * FROM command_apps WHERE package_name = :packageName")
    suspend fun getByPackageName(packageName: String): CommandAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(commandApp: CommandAppEntity)

    @Update
    suspend fun update(commandApp: CommandAppEntity)

    @Delete
    suspend fun delete(commandApp: CommandAppEntity)

    @Query("DELETE FROM command_apps")
    suspend fun deleteAll()
}

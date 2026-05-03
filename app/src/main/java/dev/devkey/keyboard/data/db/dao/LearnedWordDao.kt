package dev.devkey.keyboard.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.devkey.keyboard.data.db.entity.LearnedWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LearnedWordDao {

    @Query("SELECT * FROM learned_words ORDER BY frequency DESC")
    fun getAllWords(): Flow<List<LearnedWordEntity>>

    @Query("SELECT * FROM learned_words WHERE is_command = 1 ORDER BY frequency DESC")
    fun getCommandWords(): Flow<List<LearnedWordEntity>>

    @Query("SELECT * FROM learned_words WHERE is_command = 0 ORDER BY frequency DESC")
    fun getNormalWords(): Flow<List<LearnedWordEntity>>

    @Query(
        """
        SELECT * FROM learned_words
        WHERE word = :word
          AND ((:contextApp IS NULL AND context_app IS NULL) OR context_app = :contextApp)
        ORDER BY frequency DESC
        LIMIT 1
        """
    )
    suspend fun findWord(word: String, contextApp: String?): LearnedWordEntity?

    @Query("SELECT * FROM learned_words WHERE word = :word ORDER BY frequency DESC LIMIT 1")
    suspend fun findWordAny(word: String): LearnedWordEntity?

    @Query("SELECT * FROM learned_words WHERE is_user_added = 1 ORDER BY word ASC")
    fun getUserAddedWords(): Flow<List<LearnedWordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: LearnedWordEntity): Long

    @Update
    suspend fun update(word: LearnedWordEntity)

    @Delete
    suspend fun delete(word: LearnedWordEntity)

    @Query("DELETE FROM learned_words")
    suspend fun deleteAll()

    @Query("SELECT * FROM learned_words ORDER BY frequency DESC")
    suspend fun getAllWordsList(): List<LearnedWordEntity>
}

package dev.devkey.keyboard.testutil

import dev.devkey.keyboard.data.db.dao.LearnedWordDao
import dev.devkey.keyboard.data.db.entity.LearnedWordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLearnedWordDao : LearnedWordDao {

    private val words = MutableStateFlow<List<LearnedWordEntity>>(emptyList())
    private var nextId = 1L

    override fun getAllWords(): Flow<List<LearnedWordEntity>> = words

    override fun getCommandWords(): Flow<List<LearnedWordEntity>> =
        words.map { list -> list.filter { it.isCommand } }

    override fun getNormalWords(): Flow<List<LearnedWordEntity>> =
        words.map { list -> list.filter { !it.isCommand } }

    override suspend fun findWord(word: String, contextApp: String?): LearnedWordEntity? =
        words.value.find { it.word == word && it.contextApp == contextApp }

    override suspend fun findWordAny(word: String): LearnedWordEntity? =
        words.value.filter { it.word == word }.maxByOrNull { it.frequency }

    override fun getUserAddedWords(): Flow<List<LearnedWordEntity>> =
        words.map { list -> list.filter { it.isUserAdded } }

    override suspend fun insert(word: LearnedWordEntity): Long {
        val id = nextId++
        val entity = word.copy(id = id)
        words.value = words.value + entity
        return id
    }

    override suspend fun update(word: LearnedWordEntity) {
        words.value = words.value.map { if (it.id == word.id) word else it }
    }

    override suspend fun delete(word: LearnedWordEntity) {
        words.value = words.value.filter { it.id != word.id }
    }

    override suspend fun deleteAll() {
        words.value = emptyList()
    }

    override suspend fun getAllWordsList(): List<LearnedWordEntity> = words.value
}

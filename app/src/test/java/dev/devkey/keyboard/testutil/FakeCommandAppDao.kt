package dev.devkey.keyboard.testutil

import dev.devkey.keyboard.data.db.dao.CommandAppDao
import dev.devkey.keyboard.data.db.entity.CommandAppEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake implementation of [CommandAppDao] for use in JVM unit tests.
 *
 * Stores entries in a plain mutable map so tests can verify state without
 * spinning up a Room database or an Android context.
 */
class FakeCommandAppDao : CommandAppDao {

    private val store = mutableMapOf<String, CommandAppEntity>()
    private val _flow = MutableStateFlow<List<CommandAppEntity>>(emptyList())

    override fun getAllCommandApps(): Flow<List<CommandAppEntity>> = _flow

    override suspend fun getByPackageName(packageName: String): CommandAppEntity? =
        store[packageName]

    override suspend fun insert(commandApp: CommandAppEntity) {
        store[commandApp.packageName] = commandApp
        _flow.value = store.values.toList()
    }

    override suspend fun update(commandApp: CommandAppEntity) {
        store[commandApp.packageName] = commandApp
        _flow.value = store.values.toList()
    }

    override suspend fun delete(commandApp: CommandAppEntity) {
        store.remove(commandApp.packageName)
        _flow.value = store.values.toList()
    }

    override suspend fun deleteAll() {
        store.clear()
        _flow.value = emptyList()
    }

    override suspend fun getAllCommandAppsList(): List<CommandAppEntity> =
        store.values.toList()
}

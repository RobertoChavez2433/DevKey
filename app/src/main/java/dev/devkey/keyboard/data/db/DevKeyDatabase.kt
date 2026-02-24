package dev.devkey.keyboard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.devkey.keyboard.data.db.dao.ClipboardHistoryDao
import dev.devkey.keyboard.data.db.dao.CommandAppDao
import dev.devkey.keyboard.data.db.dao.LearnedWordDao
import dev.devkey.keyboard.data.db.dao.MacroDao
import dev.devkey.keyboard.data.db.entity.ClipboardHistoryEntity
import dev.devkey.keyboard.data.db.entity.CommandAppEntity
import dev.devkey.keyboard.data.db.entity.LearnedWordEntity
import dev.devkey.keyboard.data.db.entity.MacroEntity
import dev.devkey.keyboard.feature.macro.DefaultMacros
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        MacroEntity::class,
        LearnedWordEntity::class,
        ClipboardHistoryEntity::class,
        CommandAppEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class DevKeyDatabase : RoomDatabase() {

    abstract fun macroDao(): MacroDao
    abstract fun learnedWordDao(): LearnedWordDao
    abstract fun clipboardHistoryDao(): ClipboardHistoryDao
    abstract fun commandAppDao(): CommandAppDao

    companion object {
        private const val DATABASE_NAME = "devkey.db"

        @Volatile
        private var INSTANCE: DevKeyDatabase? = null

        fun getInstance(context: Context): DevKeyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): DevKeyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DevKeyDatabase::class.java,
                DATABASE_NAME
            ).fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Insert default macros on first database creation
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        val database = getInstance(context)
                        val macroDao = database.macroDao()
                        for (macro in DefaultMacros.getDefaults()) {
                            macroDao.insert(macro)
                        }
                    }
                }
            }).build()
        }
    }
}

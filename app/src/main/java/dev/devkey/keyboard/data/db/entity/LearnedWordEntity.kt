package dev.devkey.keyboard.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learned_words")
data class LearnedWordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "word")
    val word: String,

    @ColumnInfo(name = "frequency")
    val frequency: Int = 1,

    @ColumnInfo(name = "context_app")
    val contextApp: String? = null,

    @ColumnInfo(name = "is_command")
    val isCommand: Boolean = false
)

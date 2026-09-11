package com.storynest.android.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val ageBand: String,
    val mood: String,
    val characterCard: String,
    val styleLock: String,
    val coverPath: String?,
    val pageCount: Int
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val pageNumber: Int,
    val text: String,
    val imagePath: String?,
    val imagePrompt: String,
    val isPlaceholder: Boolean
)

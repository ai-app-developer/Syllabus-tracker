package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "chapters",
  foreignKeys =
    [
      ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index(value = ["bookId"])],
)
data class ChapterEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val bookId: Long,
  val name: String,
  val number: Int,
  val isCompleted: Boolean = false,
  val completedAt: Long? = null,
)

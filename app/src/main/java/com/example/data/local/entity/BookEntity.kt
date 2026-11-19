package com.example.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "books")
data class BookEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val name: String,
  val createdAt: Long = System.currentTimeMillis(),
)

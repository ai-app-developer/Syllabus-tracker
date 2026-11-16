package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val name: String,
  val createdAt: Long = System.currentTimeMillis(),
)

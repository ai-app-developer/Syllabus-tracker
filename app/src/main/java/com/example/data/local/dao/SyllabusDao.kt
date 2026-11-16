package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyllabusDao {
  @Query("SELECT * FROM books ORDER BY createdAt DESC, id DESC")
  fun observeBooks(): Flow<List<BookEntity>>

  @Query("SELECT * FROM books WHERE id = :bookId")
  fun observeBookById(bookId: Long): Flow<BookEntity?>

  @Query("SELECT * FROM chapters ORDER BY number ASC, id ASC")
  fun observeAllChapters(): Flow<List<ChapterEntity>>

  @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY number ASC, id ASC")
  fun observeChaptersForBook(bookId: Long): Flow<List<ChapterEntity>>

  @Query("SELECT COUNT(*) FROM chapters")
  suspend fun getTotalChapterCount(): Int

  @Query(
    "SELECT COUNT(*) FROM chapters WHERE isCompleted = 1 AND completedAt IS NOT NULL AND completedAt >= :startMillis AND completedAt < :endMillis"
  )
  suspend fun getCompletedCountBetween(startMillis: Long, endMillis: Long): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBook(book: BookEntity): Long

  @Update
  suspend fun updateBook(book: BookEntity)

  @Query("DELETE FROM books WHERE id = :bookId")
  suspend fun deleteBookById(bookId: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertChapter(chapter: ChapterEntity): Long

  @Update
  suspend fun updateChapter(chapter: ChapterEntity)

  @Query("DELETE FROM chapters WHERE id = :chapterId")
  suspend fun deleteChapterById(chapterId: Long)

  @Query("UPDATE chapters SET isCompleted = :isCompleted, completedAt = :completedAt WHERE id = :chapterId")
  suspend fun updateChapterCompletion(chapterId: Long, isCompleted: Boolean, completedAt: Long?)
}

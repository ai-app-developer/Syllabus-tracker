package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

data class WidgetAggregateStats(
  val totalBooks: Int,
  val totalChapters: Int,
  val completedChapters: Int,
  val completedToday: Int,
)

data class BookChapterCounts(
  val totalChapters: Int,
  val completedChapters: Int,
)

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

  @Query("SELECT * FROM books ORDER BY createdAt DESC, id DESC")
  suspend fun getAllBooksOnce(): List<BookEntity>

  @Query("SELECT * FROM chapters ORDER BY number ASC, id ASC")
  suspend fun getAllChaptersOnce(): List<ChapterEntity>

  @Query(
    """
    SELECT
      (SELECT COUNT(*) FROM books) AS totalBooks,
      COUNT(*) AS totalChapters,
      COALESCE(SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END), 0) AS completedChapters,
      COALESCE(SUM(CASE WHEN isCompleted = 1 AND completedAt IS NOT NULL AND completedAt >= :startOfTodayMillis AND completedAt < :endOfTodayMillis THEN 1 ELSE 0 END), 0) AS completedToday
    FROM chapters
    """
  )
  suspend fun getWidgetAggregateStats(
    startOfTodayMillis: Long,
    endOfTodayMillis: Long,
  ): WidgetAggregateStats

  @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
  suspend fun getBookByIdOnce(bookId: Long): BookEntity?

  @Query(
    """
    SELECT b.* FROM books b
    WHERE EXISTS (SELECT 1 FROM chapters c WHERE c.bookId = b.id AND c.isCompleted = 0)
    ORDER BY b.createdAt DESC, b.id DESC
    LIMIT 1
    """
  )
  suspend fun getFirstIncompleteBookOnce(): BookEntity?

  @Query("SELECT * FROM books ORDER BY createdAt DESC, id DESC LIMIT 1")
  suspend fun getLatestBookOnce(): BookEntity?

  @Query(
    """
    SELECT
      COUNT(*) AS totalChapters,
      COALESCE(SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END), 0) AS completedChapters
    FROM chapters
    WHERE bookId = :bookId
    """
  )
  suspend fun getBookChapterCounts(bookId: Long): BookChapterCounts

  @Query(
    "SELECT * FROM chapters WHERE bookId = :bookId AND isCompleted = 0 ORDER BY number ASC, id ASC LIMIT 1"
  )
  suspend fun getNextIncompleteChapterOnce(bookId: Long): ChapterEntity?

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

  @Query("DELETE FROM chapters")
  suspend fun deleteAllChapters()

  @Query("DELETE FROM books")
  suspend fun deleteAllBooks()

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertChapter(chapter: ChapterEntity): Long

  @Update
  suspend fun updateChapter(chapter: ChapterEntity)

  @Query("DELETE FROM chapters WHERE id = :chapterId")
  suspend fun deleteChapterById(chapterId: Long)

  @Query("UPDATE chapters SET isCompleted = :isCompleted, completedAt = :completedAt WHERE id = :chapterId")
  suspend fun updateChapterCompletion(chapterId: Long, isCompleted: Boolean, completedAt: Long?)
}

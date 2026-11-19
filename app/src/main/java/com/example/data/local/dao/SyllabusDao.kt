package com.example.data.local.dao

import androidx.compose.runtime.Immutable
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

import kotlin.math.roundToInt

@Immutable
data class WidgetSummaryRow(
  val totalBooks: Int,
  val totalChapters: Int,
  val completedChapters: Int,
  val completedToday: Int,
  val dailyGoal: Int,
  val activeBookId: Long?,
  val activeBookName: String?,
  val activeBookTotalChapters: Int,
  val activeBookCompletedChapters: Int,
  val activeBookPercent: Int,
  val nextChapterNumber: Int?,
  val nextChapterName: String?,
) {
  val overallPercentage: Int
    get() = if (totalChapters == 0) 0 else ((completedChapters * 100f) / totalChapters).roundToInt()
}

@Immutable
data class BookProgressRow(
  @Embedded val book: BookEntity,
  val totalChapters: Int,
  val completedChapters: Int,
  val nextChapterId: Long?,
  val nextChapterName: String?,
  val nextChapterNumber: Int?,
)

@Immutable
data class OverallProgressRow(
  val totalBooks: Int,
  val totalChapters: Int,
  val completedChapters: Int,
  val completedToday: Int,
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

  @Query(
    """
    SELECT
      b.*,
      COALESCE((SELECT COUNT(*) FROM chapters c WHERE c.bookId = b.id), 0) AS totalChapters,
      COALESCE((SELECT SUM(CASE WHEN c.isCompleted = 1 THEN 1 ELSE 0 END) FROM chapters c WHERE c.bookId = b.id), 0) AS completedChapters,
      (SELECT c.id FROM chapters c WHERE c.bookId = b.id AND c.isCompleted = 0 ORDER BY c.number ASC, c.id ASC LIMIT 1) AS nextChapterId,
      (SELECT c.name FROM chapters c WHERE c.bookId = b.id AND c.isCompleted = 0 ORDER BY c.number ASC, c.id ASC LIMIT 1) AS nextChapterName,
      (SELECT c.number FROM chapters c WHERE c.bookId = b.id AND c.isCompleted = 0 ORDER BY c.number ASC, c.id ASC LIMIT 1) AS nextChapterNumber
    FROM books b
    ORDER BY b.createdAt DESC, b.id DESC
    """
  )
  fun observeBookProgressRows(): Flow<List<BookProgressRow>>

  @Query(
    """
    SELECT
      (SELECT COUNT(*) FROM books) AS totalBooks,
      (SELECT COUNT(*) FROM chapters) AS totalChapters,
      (SELECT COALESCE(SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END), 0) FROM chapters) AS completedChapters,
      (SELECT COALESCE(SUM(CASE WHEN isCompleted = 1 AND completedAt IS NOT NULL AND completedAt >= :startOfTodayMillis AND completedAt < :endOfTodayMillis THEN 1 ELSE 0 END), 0) FROM chapters) AS completedToday
    """
  )
  fun observeOverallProgressRow(
    startOfTodayMillis: Long,
    endOfTodayMillis: Long,
  ): Flow<OverallProgressRow>

  @Query(
    "SELECT completedAt FROM chapters WHERE isCompleted = 1 AND completedAt IS NOT NULL AND completedAt >= :startMillis AND completedAt < :endMillis"
  )
  fun observeCompletedTimestampsBetween(startMillis: Long, endMillis: Long): Flow<List<Long>>

  @Query(
    "SELECT COUNT(*) FROM chapters WHERE isCompleted = 1 AND completedAt IS NOT NULL AND completedAt < :beforeMillis"
  )
  fun observeCompletedCountBefore(beforeMillis: Long): Flow<Int>

  @Query("SELECT COUNT(*) FROM chapters WHERE isCompleted = 1 AND completedAt IS NULL")
  fun observeCompletedWithoutTimestampCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM chapters")
  fun observeTotalChapterCount(): Flow<Int>

  @Query("SELECT * FROM books ORDER BY createdAt DESC, id DESC")
  suspend fun getAllBooksOnce(): List<BookEntity>

  @Query("SELECT * FROM chapters ORDER BY number ASC, id ASC")
  suspend fun getAllChaptersOnce(): List<ChapterEntity>

  @Query("SELECT * FROM chapters WHERE id = :chapterId LIMIT 1")
  suspend fun getChapterByIdOnce(chapterId: Long): ChapterEntity?

  @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY number ASC, id ASC")
  suspend fun getChaptersForBookOnce(bookId: Long): List<ChapterEntity>

  @Query(
    """
    SELECT
      (SELECT COUNT(*) FROM books) AS totalBooks,
      (SELECT COUNT(*) FROM chapters) AS totalChapters,
      (SELECT COALESCE(SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END), 0) FROM chapters) AS completedChapters,
      (SELECT COALESCE(SUM(CASE WHEN isCompleted = 1 AND completedAt IS NOT NULL AND completedAt >= :startOfTodayMillis AND completedAt < :endOfTodayMillis THEN 1 ELSE 0 END), 0) FROM chapters) AS completedToday,
      :dailyGoal AS dailyGoal,
      b.id AS activeBookId,
      b.name AS activeBookName,
      COALESCE((SELECT COUNT(*) FROM chapters c WHERE c.bookId = b.id), 0) AS activeBookTotalChapters,
      COALESCE((SELECT SUM(CASE WHEN c.isCompleted = 1 THEN 1 ELSE 0 END) FROM chapters c WHERE c.bookId = b.id), 0) AS activeBookCompletedChapters,
      COALESCE(
        (SELECT CASE WHEN COUNT(*) = 0 THEN 0 ELSE CAST(ROUND(COALESCE(SUM(CASE WHEN c.isCompleted = 1 THEN 1 ELSE 0 END), 0) * 100.0 / COUNT(*)) AS INTEGER) END FROM chapters c WHERE c.bookId = b.id),
        0
      ) AS activeBookPercent,
      (SELECT c.number FROM chapters c WHERE c.bookId = b.id AND c.isCompleted = 0 ORDER BY c.number ASC, c.id ASC LIMIT 1) AS nextChapterNumber,
      (SELECT c.name FROM chapters c WHERE c.bookId = b.id AND c.isCompleted = 0 ORDER BY c.number ASC, c.id ASC LIMIT 1) AS nextChapterName
    FROM (SELECT 1 AS dummy) anchor
    LEFT JOIN (
      SELECT id, name FROM (
        SELECT id, name, 1 AS priority, createdAt FROM books
        WHERE :lastOpenedBookId > 0 AND id = :lastOpenedBookId
          AND EXISTS (SELECT 1 FROM chapters c WHERE c.bookId = books.id AND c.isCompleted = 0)
        UNION ALL
        SELECT id, name, 2 AS priority, createdAt FROM books
        WHERE EXISTS (SELECT 1 FROM chapters c WHERE c.bookId = books.id AND c.isCompleted = 0)
        UNION ALL
        SELECT id, name, 3 AS priority, createdAt FROM books
        WHERE :lastOpenedBookId > 0 AND id = :lastOpenedBookId
        UNION ALL
        SELECT id, name, 4 AS priority, createdAt FROM books
      )
      ORDER BY priority ASC, createdAt DESC, id DESC
      LIMIT 1
    ) b ON 1 = 1
    """
  )
  suspend fun getWidgetSummaryRow(
    startOfTodayMillis: Long,
    endOfTodayMillis: Long,
    lastOpenedBookId: Long,
    dailyGoal: Int,
  ): WidgetSummaryRow

  @Query(
    """
    SELECT
      (SELECT COUNT(*) FROM books) AS totalBooks,
      (SELECT COUNT(*) FROM chapters) AS totalChapters,
      (SELECT COALESCE(SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END), 0) FROM chapters) AS completedChapters,
      (SELECT COALESCE(SUM(CASE WHEN isCompleted = 1 AND completedAt IS NOT NULL AND completedAt >= :startOfTodayMillis AND completedAt < :endOfTodayMillis THEN 1 ELSE 0 END), 0) FROM chapters) AS completedToday,
      :dailyGoal AS dailyGoal,
      b.id AS activeBookId,
      b.name AS activeBookName,
      COALESCE((SELECT COUNT(*) FROM chapters c WHERE c.bookId = b.id), 0) AS activeBookTotalChapters,
      COALESCE((SELECT SUM(CASE WHEN c.isCompleted = 1 THEN 1 ELSE 0 END) FROM chapters c WHERE c.bookId = b.id), 0) AS activeBookCompletedChapters,
      COALESCE(
        (SELECT CASE WHEN COUNT(*) = 0 THEN 0 ELSE CAST(ROUND(COALESCE(SUM(CASE WHEN c.isCompleted = 1 THEN 1 ELSE 0 END), 0) * 100.0 / COUNT(*)) AS INTEGER) END FROM chapters c WHERE c.bookId = b.id),
        0
      ) AS activeBookPercent,
      (SELECT c.number FROM chapters c WHERE c.bookId = b.id AND c.isCompleted = 0 ORDER BY c.number ASC, c.id ASC LIMIT 1) AS nextChapterNumber,
      (SELECT c.name FROM chapters c WHERE c.bookId = b.id AND c.isCompleted = 0 ORDER BY c.number ASC, c.id ASC LIMIT 1) AS nextChapterName
    FROM (SELECT 1 AS dummy) anchor
    LEFT JOIN (
      SELECT id, name FROM (
        SELECT id, name, 1 AS priority, createdAt FROM books
        WHERE :lastOpenedBookId > 0 AND id = :lastOpenedBookId
          AND EXISTS (SELECT 1 FROM chapters c WHERE c.bookId = books.id AND c.isCompleted = 0)
        UNION ALL
        SELECT id, name, 2 AS priority, createdAt FROM books
        WHERE EXISTS (SELECT 1 FROM chapters c WHERE c.bookId = books.id AND c.isCompleted = 0)
        UNION ALL
        SELECT id, name, 3 AS priority, createdAt FROM books
        WHERE :lastOpenedBookId > 0 AND id = :lastOpenedBookId
        UNION ALL
        SELECT id, name, 4 AS priority, createdAt FROM books
      )
      ORDER BY priority ASC, createdAt DESC, id DESC
      LIMIT 1
    ) b ON 1 = 1
    """
  )
  fun observeWidgetSummaryRow(
    startOfTodayMillis: Long,
    endOfTodayMillis: Long,
    lastOpenedBookId: Long,
    dailyGoal: Int,
  ): Flow<WidgetSummaryRow>

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

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBooks(books: List<BookEntity>): List<Long>

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

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertChapters(chapters: List<ChapterEntity>): List<Long>

  @Update
  suspend fun updateChapter(chapter: ChapterEntity)

  @Query("DELETE FROM chapters WHERE id = :chapterId")
  suspend fun deleteChapterById(chapterId: Long)

  @Query("UPDATE chapters SET isCompleted = :isCompleted, completedAt = :completedAt WHERE id = :chapterId")
  suspend fun updateChapterCompletion(chapterId: Long, isCompleted: Boolean, completedAt: Long?)
}

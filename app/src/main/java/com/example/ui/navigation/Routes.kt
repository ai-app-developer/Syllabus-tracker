package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object HomeRoute

@Serializable object BooksRoute

@Serializable data class BookDetailRoute(val bookId: Long)

@Serializable object ProgressRoute

@Serializable object MoreRoute

@Serializable object StudyTimerSetupRoute

@Serializable object StudyingRoute

@Serializable object ReadingHistoryRoute

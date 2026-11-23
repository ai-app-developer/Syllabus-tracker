package com.example

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.notifications.NotificationHelper
import com.example.ui.components.AddChapterBottomSheet
import com.example.ui.components.BackupErrorDialog
import com.example.ui.components.BookFormDialog
import com.example.ui.components.BottomTab
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DailyGoalDialog
import com.example.ui.components.ImportBackupConfirmDialog
import com.example.ui.components.ImportBackupResultDialog
import com.example.ui.components.ReminderTimePickerDialog
import com.example.ui.components.SyllabusBottomBar
import com.example.ui.components.TimerRecoveryDialog
import com.example.ui.components.WidgetPromptBottomSheet
import com.example.ui.navigation.BookDetailRoute
import com.example.ui.navigation.BooksRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.HomeScreenWidgetsRoute
import com.example.ui.navigation.MoreRoute
import com.example.ui.navigation.ProgressRoute
import com.example.ui.navigation.ReadingHistoryRoute
import com.example.ui.navigation.StudyTimerSetupRoute
import com.example.ui.navigation.StudyingRoute
import com.example.ui.screens.BookDetailScreen
import com.example.ui.screens.BooksScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HomeScreenWidgetsScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.ReadingHistoryScreen
import com.example.ui.screens.StudyTimerSetupScreen
import com.example.ui.screens.StudyingScreen
import com.example.ui.theme.AppBackground
import com.example.ui.theme.SyllabusTrackerTheme
import com.example.ui.viewmodel.BookDetailUiState
import com.example.ui.viewmodel.SyllabusViewModel
import com.example.ui.viewmodel.TimerUiEvent
import com.example.ui.viewmodel.TimerViewModel
import com.example.ui.viewmodel.WidgetPromptSheetState
import com.example.widgets.WidgetIntents
import com.example.widgets.WidgetUpdater
import kotlinx.coroutines.delay

data class PendingWidgetNav(
  val id: Long,
  val action: String,
  val bookId: Long? = null,
)

class MainActivity : ComponentActivity() {
  private var pendingWidgetNav by mutableStateOf<PendingWidgetNav?>(null)
  private var pendingTimerAction by mutableStateOf<Pair<Long, String>?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
    )
    handleIntent(intent)
    setContent {
      SyllabusTrackerTheme {
        SyllabusApp(
          pendingWidgetNav = pendingWidgetNav,
          onConsumeWidgetNav = { pendingWidgetNav = null },
          pendingTimerAction = pendingTimerAction,
          onConsumeTimerAction = { pendingTimerAction = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    val timerAction = intent?.getStringExtra(NotificationHelper.EXTRA_TIMER_ACTION)
    if (timerAction != null) {
      pendingTimerAction = System.nanoTime() to timerAction
      intent.removeExtra(NotificationHelper.EXTRA_TIMER_ACTION)
    }

    val action = intent?.getStringExtra(WidgetIntents.EXTRA_WIDGET_ACTION) ?: return
    val bookId =
      if (intent.hasExtra(WidgetIntents.EXTRA_BOOK_ID)) {
        intent.getLongExtra(WidgetIntents.EXTRA_BOOK_ID, -1L).takeIf { it > 0L }
      } else {
        null
      }
    pendingWidgetNav =
      PendingWidgetNav(
        id = System.nanoTime(),
        action = action,
        bookId = bookId,
      )
    intent.removeExtra(WidgetIntents.EXTRA_WIDGET_ACTION)
  }
}

@Composable
fun SyllabusApp(
  pendingWidgetNav: PendingWidgetNav? = null,
  onConsumeWidgetNav: () -> Unit = {},
  pendingTimerAction: Pair<Long, String>? = null,
  onConsumeTimerAction: () -> Unit = {},
  viewModel: SyllabusViewModel = viewModel(),
  timerViewModel: TimerViewModel = viewModel(),
) {
  val dialogsState by viewModel.dialogsState.collectAsStateWithLifecycle()
  val preferencesState by viewModel.preferencesState.collectAsStateWithLifecycle()
  val homeUiState by viewModel.homeUiState.collectAsStateWithLifecycle()
  val widgetPromptSheetState by viewModel.widgetPromptSheet.collectAsStateWithLifecycle()
  val isPinRequestActive by viewModel.isPinRequestActive.collectAsStateWithLifecycle()
  val isTimerActiveGlobal by timerViewModel.isTimerActive.collectAsStateWithLifecycle()
  val recoveryPrompt by timerViewModel.recoveryPrompt.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = backStackEntry?.destination

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val snackbarHostState = remember { SnackbarHostState() }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          val app = context.applicationContext as? SyllabusApplication
          if (app != null) {
            WidgetUpdater.requestUpdate(app)
          }
          viewModel.refreshWidgetCounts()
        }
        Lifecycle.Event.ON_STOP -> {
          timerViewModel.touchLastKnownWallTime()
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(viewModel) {
    viewModel.snackbarMessages.collect { message ->
      snackbarHostState.showSnackbar(message)
    }
  }

  LaunchedEffect(timerViewModel) {
    timerViewModel.events.collect { event ->
      when (event) {
        is TimerUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
      }
    }
  }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* handled */ }

  LaunchedEffect(preferencesState.reminderEnabled) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        preferencesState.reminderEnabled &&
        !NotificationHelper.hasNotificationPermission(context)
    ) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  LaunchedEffect(homeUiState.isLoading) {
    if (homeUiState.isLoading) return@LaunchedEffect
    delay(600L)
    val isStartupPermissionPending =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        preferencesState.reminderEnabled &&
        !NotificationHelper.hasNotificationPermission(context)
    val currentIsHome =
      navController.currentBackStackEntry?.destination?.hasRoute<HomeRoute>() == true
    viewModel.checkWidgetPromptOnAppStart(
      isTimerActive = timerViewModel.isTimerActive.value,
      hasRecoveryPrompt = timerViewModel.recoveryPrompt.value != null,
      isStartupPermissionPending = isStartupPermissionPending,
      isCurrentlyOnHome = currentIsHome,
    )
  }

  val isSecondaryTimerOrDetailScreen =
    currentDestination?.hasRoute<BookDetailRoute>() == true ||
      currentDestination?.hasRoute<StudyTimerSetupRoute>() == true ||
      currentDestination?.hasRoute<StudyingRoute>() == true ||
      currentDestination?.hasRoute<ReadingHistoryRoute>() == true ||
      currentDestination?.hasRoute<HomeScreenWidgetsRoute>() == true

  val selectedTab =
    when {
      currentDestination?.hasRoute<BooksRoute>() == true -> BottomTab.BOOKS
      currentDestination?.hasRoute<ProgressRoute>() == true -> BottomTab.PROGRESS
      currentDestination?.hasRoute<MoreRoute>() == true -> BottomTab.MORE
      else -> BottomTab.HOME
    }

  fun navigateToTab(route: Any) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  LaunchedEffect(pendingWidgetNav?.id) {
    val nav = pendingWidgetNav ?: return@LaunchedEffect
    when (nav.action) {
      WidgetIntents.ACTION_OPEN_ADD_CHAPTER -> {
        navigateToTab(HomeRoute)
        viewModel.openAddChapterSheet()
      }
      WidgetIntents.ACTION_OPEN_BOOK -> {
        if (nav.bookId != null) {
          navController.navigate(BookDetailRoute(nav.bookId)) {
            launchSingleTop = true
          }
        } else {
          navigateToTab(BooksRoute)
        }
      }
      WidgetIntents.ACTION_OPEN_PROGRESS -> {
        navigateToTab(ProgressRoute)
      }
      WidgetIntents.ACTION_OPEN_TODAYS_PLAN -> {
        navigateToTab(HomeRoute)
        viewModel.openDailyGoalDialog()
      }
      WidgetIntents.ACTION_OPEN_HOME -> {
        navigateToTab(HomeRoute)
      }
    }
    onConsumeWidgetNav()
  }

  LaunchedEffect(pendingTimerAction?.first) {
    val (_, timerAction) = pendingTimerAction ?: return@LaunchedEffect
    when (timerAction) {
      NotificationHelper.TIMER_ACTION_OPEN_STUDYING -> {
        navController.navigate(StudyingRoute) { launchSingleTop = true }
      }
      NotificationHelper.TIMER_ACTION_STOP_SUMMARY -> {
        timerViewModel.requestStopSummary()
        navController.navigate(StudyingRoute) { launchSingleTop = true }
      }
    }
    onConsumeTimerAction()
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = AppBackground,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    bottomBar = {
      if (!isSecondaryTimerOrDetailScreen) {
        SyllabusBottomBar(
          selectedTab = selectedTab,
          onSelectTab = { tab ->
            when (tab) {
              BottomTab.HOME -> navigateToTab(HomeRoute)
              BottomTab.BOOKS -> navigateToTab(BooksRoute)
              BottomTab.PROGRESS -> navigateToTab(ProgressRoute)
              BottomTab.MORE -> navigateToTab(MoreRoute)
            }
          },
          onAddClick = { viewModel.openAddChapterSheet() },
        )
      }
    },
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = HomeRoute,
      enterTransition = { fadeIn(animationSpec = tween(150)) },
      exitTransition = { fadeOut(animationSpec = tween(150)) },
      popEnterTransition = { EnterTransition.None },
      popExitTransition = { ExitTransition.None },
    ) {
      composable<HomeRoute> {
        val homeUiState by viewModel.homeUiState.collectAsStateWithLifecycle()
        val isTimerActive by timerViewModel.isTimerActive.collectAsStateWithLifecycle()
        HomeScreen(
          state = homeUiState,
          liveStudyingState = timerViewModel.liveStudyingState,
          innerPadding = innerPadding,
          onBellClick = { navigateToTab(MoreRoute) },
          onStudyTimerClick = {
            if (isTimerActive) {
              navController.navigate(StudyingRoute) { launchSingleTop = true }
            } else {
              navController.navigate(StudyTimerSetupRoute) { launchSingleTop = true }
            }
          },
          onTodaysPlanClick = { viewModel.openDailyGoalDialog() },
          onContinueBookClick = { bookId ->
            navController.navigate(BookDetailRoute(bookId))
          },
          onAddBookClick = { viewModel.openAddBookDialog() },
        )
      }

      composable<BooksRoute> {
        val booksUiState by viewModel.booksUiState.collectAsStateWithLifecycle()
        val todayStudyMs by timerViewModel.todayStudyMs.collectAsStateWithLifecycle()
        val isTimerActive by timerViewModel.isTimerActive.collectAsStateWithLifecycle()
        BooksScreen(
          state = booksUiState,
          todayStudyMs = todayStudyMs,
          isTimerActive = isTimerActive,
          liveStudyingState = timerViewModel.liveStudyingState,
          innerPadding = innerPadding,
          onToggleSearch = { viewModel.toggleSearchActive() },
          onSearchQueryChange = { viewModel.setSearchQuery(it) },
          onSelectFilter = { viewModel.setBookFilter(it) },
          onSelectSortOrder = { viewModel.setBookSortOrder(it) },
          onBookClick = { bookId -> navController.navigate(BookDetailRoute(bookId)) },
          onAddBookClick = { viewModel.openAddBookDialog() },
          onStudyTimerClick = {
            if (isTimerActive) {
              navController.navigate(StudyingRoute) { launchSingleTop = true }
            } else {
              navController.navigate(StudyTimerSetupRoute) { launchSingleTop = true }
            }
          },
        )
      }

      composable<BookDetailRoute> { entry ->
        val route = entry.toRoute<BookDetailRoute>()
        val detailState by
          remember(route.bookId) { viewModel.observeBookDetailUiState(route.bookId) }
            .collectAsStateWithLifecycle(initialValue = BookDetailUiState())

        BookDetailScreen(
          state = detailState,
          onMarkOpened = { viewModel.markBookOpened(route.bookId) },
          onBackClick = { navController.popBackStack() },
          onEditBookClick = { viewModel.openEditBookDialog(it) },
          onDeleteBookClick = { viewModel.requestDeleteBook(it) },
          onAddChapterClick = { viewModel.openAddChapterSheet(preselectedBookId = route.bookId) },
          onToggleChapter = { viewModel.toggleChapterCompleted(it) },
          onEditChapterClick = { viewModel.openEditChapterSheet(it) },
          onDeleteChapterClick = { viewModel.requestDeleteChapter(it) },
        )
      }

      composable<ProgressRoute> {
        val progressUiState by viewModel.progressUiState.collectAsStateWithLifecycle()
        val analyticsUiState by timerViewModel.analyticsUiState.collectAsStateWithLifecycle()
        ProgressScreen(
          state = progressUiState,
          timerAnalytics = analyticsUiState,
          innerPadding = innerPadding,
          onSelectTab = { viewModel.setProgressTab(it) },
          onSelectTrendPeriod = { viewModel.setTrendPeriod(it) },
          onSelectAnalyticsPeriod = { timerViewModel.setAnalyticsPeriod(it) },
          onBookClick = { bookId -> navController.navigate(BookDetailRoute(bookId)) },
          onOpenReadingHistory = {
            navController.navigate(ReadingHistoryRoute) { launchSingleTop = true }
          },
        )
      }

      composable<MoreRoute> {
        val isTimerActive by timerViewModel.isTimerActive.collectAsStateWithLifecycle()
        MoreScreen(
          preferences = preferencesState,
          innerPadding = innerPadding,
          isTimerRunning = isTimerActive,
          onOpenStudyTimer = {
            if (isTimerActive) {
              navController.navigate(StudyingRoute) { launchSingleTop = true }
            } else {
              navController.navigate(StudyTimerSetupRoute) { launchSingleTop = true }
            }
          },
          onOpenReadingHistory = {
            navController.navigate(ReadingHistoryRoute) { launchSingleTop = true }
          },
          onOpenHomeWidgets = {
            navController.navigate(HomeScreenWidgetsRoute) { launchSingleTop = true }
          },
          onEditDailyGoal = { viewModel.openDailyGoalDialog() },
          onToggleReminder = { viewModel.setReminderEnabled(it) },
          onEditReminderTime = { viewModel.openTimePicker() },
          onToggleTimerAlarm = { viewModel.setTimerAlarmEnabled(it) },
          onToggleTimerVibration = { viewModel.setTimerVibrationEnabled(it) },
          onExportBackupUri = { uri -> viewModel.exportBackupToUri(uri) },
          onImportBackupUri = { uri -> viewModel.inspectBackupUri(uri) },
        )
      }

      composable<StudyTimerSetupRoute> {
        val setupUiState by timerViewModel.setupUiState.collectAsStateWithLifecycle()
        val isTimerActive by timerViewModel.isTimerActive.collectAsStateWithLifecycle()
        StudyTimerSetupScreen(
          state = setupUiState,
          isTimerActive = isTimerActive,
          liveStudyingState = timerViewModel.liveStudyingState,
          innerPadding = innerPadding,
          timerPermissionChecklistShown = preferencesState.timerPermissionChecklistShown,
          onPermissionChecklistShown = { viewModel.setTimerPermissionChecklistShown(true) },
          onBackClick = { navController.popBackStack() },
          onSelectBook = { timerViewModel.selectBookForTimer(it) },
          onSelectChapter = { timerViewModel.selectChapterForTimer(it) },
          onSelectMode = { timerViewModel.selectTimerMode(it) },
          onSelectPomodoroPreset = { focus, brk ->
            timerViewModel.setPomodoroPreset(focus, brk)
          },
          onSetGoalMinutes = { timerViewModel.setGoalMinutes(it) },
          onNotesChange = { timerViewModel.setSetupNotes(it) },
          onStartTimerClick = {
            timerViewModel.startTimerSession {
              navController.navigate(StudyingRoute) {
                popUpTo<StudyTimerSetupRoute> { inclusive = true }
                launchSingleTop = true
              }
            }
          },
          onOpenActiveTimer = {
            navController.navigate(StudyingRoute) { launchSingleTop = true }
          },
          onAddBookClick = { viewModel.openAddBookDialog() },
          onAddChapterClick = { bookId -> viewModel.openAddChapterSheet(preselectedBookId = bookId) },
        )
      }

      composable<StudyingRoute> {
        StudyingScreen(
          liveStudyingState = timerViewModel.liveStudyingState,
          innerPadding = innerPadding,
          onMinimizeBack = { navController.popBackStack() },
          onTogglePauseResume = { timerViewModel.togglePauseResume() },
          onRequestStop = { timerViewModel.requestStopSummary() },
          onRecordLap = { timerViewModel.recordLap() },
          onSkipBreak = { timerViewModel.skipBreak() },
          onSaveSession = { finalNotes ->
            timerViewModel.saveSession(finalNotes) {
              navController.popBackStack()
            }
          },
          onResumeFromSummary = { timerViewModel.resumeFromStopSummary() },
          onDiscardSession = {
            timerViewModel.discardSession {
              navController.popBackStack()
            }
          },
          onOpenHistory = {
            navController.navigate(ReadingHistoryRoute) { launchSingleTop = true }
          },
        )
      }

      composable<ReadingHistoryRoute> {
        val historyUiState by timerViewModel.historyUiState.collectAsStateWithLifecycle()
        ReadingHistoryScreen(
          state = historyUiState,
          innerPadding = innerPadding,
          observeEvents = { sid -> timerViewModel.observeSessionEvents(sid) },
          onBackClick = { navController.popBackStack() },
          onToggleSearch = { timerViewModel.toggleHistorySearch() },
          onSearchQueryChange = { timerViewModel.setHistorySearchQuery(it) },
          onSelectBookFilter = { timerViewModel.setHistoryBookFilter(it) },
          onSelectChapterFilter = { timerViewModel.setHistoryChapterFilter(it) },
          onSelectDateFilter = { timerViewModel.setHistoryDateFilter(it) },
          onSelectSortOrder = { timerViewModel.setHistorySortOrder(it) },
          onUpdateNotes = { id, notes -> timerViewModel.updateHistorySessionNotes(id, notes) },
          onDeleteSession = { id -> timerViewModel.deleteHistorySession(id) },
          onStartNewSession = {
            navController.navigate(StudyTimerSetupRoute) { launchSingleTop = true }
          },
        )
      }

      composable<HomeScreenWidgetsRoute> {
        val widgetCounts by viewModel.widgetCounts.collectAsStateWithLifecycle()
        val showFallback by viewModel.showMoreWidgetFallbackSheet.collectAsStateWithLifecycle()
        HomeScreenWidgetsScreen(
          widgetCounts = widgetCounts,
          isPinBusy = isPinRequestActive,
          showFallbackSheet = showFallback,
          innerPadding = innerPadding,
          onBackClick = { navController.popBackStack() },
          onRefreshCounts = { viewModel.refreshWidgetCounts() },
          onAddWidgetClick = { type -> viewModel.requestPinWidgetFromSettings(type) },
          onDismissFallbackSheet = { viewModel.dismissMoreWidgetFallbackSheet() },
        )
      }
    }
  }

  recoveryPrompt?.let { recovery ->
    TimerRecoveryDialog(
      data = recovery,
      onResume = {
        timerViewModel.resumeRecoveredSession {
          navController.navigate(StudyingRoute) { launchSingleTop = true }
        }
      },
      onSaveUpToLastKnown = { timerViewModel.saveRecoveredSessionUpToLastKnownTime() },
      onDiscard = { timerViewModel.discardRecoveredSession() },
    )
  }

  if (dialogsState.addChapterSheet.isOpen) {
    val bookSummaries by viewModel.bookSummariesState.collectAsStateWithLifecycle()
    AddChapterBottomSheet(
      books = bookSummaries,
      preselectedBookId = dialogsState.addChapterSheet.preselectedBookId,
      editingChapter = dialogsState.addChapterSheet.editingChapter,
      onDismiss = { viewModel.closeAddChapterSheet() },
      onCreateBookClick = { viewModel.openAddBookDialog() },
      onSaveChapter = { bookId, name, number, isCompleted ->
        viewModel.saveChapter(
          bookId = bookId,
          name = name,
          number = number,
          isCompleted = isCompleted,
          editingChapter = dialogsState.addChapterSheet.editingChapter,
        )
      },
    )
  }

  if (dialogsState.bookFormDialog.isOpen) {
    BookFormDialog(
      editingBook = dialogsState.bookFormDialog.editingBook,
      onDismiss = { viewModel.closeBookFormDialog() },
      onConfirm = { name ->
        viewModel.saveBook(name = name, editingBook = dialogsState.bookFormDialog.editingBook)
      },
    )
  }

  ConfirmDeleteDialog(
    state = dialogsState.deleteConfirm,
    onDismiss = { viewModel.dismissDeleteConfirm() },
    onConfirm = {
      viewModel.confirmDelete(
        onBookDeleted = {
          if (currentDestination?.hasRoute<BookDetailRoute>() == true) {
            navController.popBackStack()
          }
        }
      )
    },
  )

  if (dialogsState.isDailyGoalDialogOpen) {
    DailyGoalDialog(
      currentGoal = preferencesState.dailyGoal,
      onDismiss = { viewModel.closeDailyGoalDialog() },
      onSave = { goal -> viewModel.saveDailyGoal(goal) },
    )
  }

  if (dialogsState.isTimePickerOpen) {
    ReminderTimePickerDialog(
      initialHour = preferencesState.reminderHour,
      initialMinute = preferencesState.reminderMinute,
      onDismiss = { viewModel.closeTimePicker() },
      onConfirm = { hour, minute -> viewModel.saveReminderTime(hour, minute) },
    )
  }

  dialogsState.pendingBackupImport?.let { backup ->
    ImportBackupConfirmDialog(
      backup = backup,
      onDismiss = { viewModel.dismissImportConfirm() },
      onSelectMode = { mode -> viewModel.confirmImport(mode) },
    )
  }

  dialogsState.importResult?.let { result ->
    ImportBackupResultDialog(
      result = result,
      onDismiss = { viewModel.dismissImportResult() },
    )
  }

  dialogsState.backupErrorMessage?.let { errorMsg ->
    BackupErrorDialog(
      message = errorMsg,
      onDismiss = { viewModel.dismissBackupError() },
    )
  }

  val visiblePrompt = widgetPromptSheetState as? WidgetPromptSheetState.Visible
  if (visiblePrompt != null && !isTimerActiveGlobal && recoveryPrompt == null) {
    WidgetPromptBottomSheet(
      totalBooks = homeUiState.totalBooks,
      totalChapters = homeUiState.totalChapters,
      completedChapters = homeUiState.completedChapters,
      overallPercentage = homeUiState.overallPercentage,
      isFallbackManualSteps = visiblePrompt.isFallbackManualSteps,
      isPinBusy = isPinRequestActive,
      onBecameVisible = { viewModel.onWidgetPromptSheetVisible() },
      onAddWidgetClick = { viewModel.requestPinOverviewFromPrompt() },
      onDismiss = { viewModel.dismissWidgetPrompt() },
    )
  }
}

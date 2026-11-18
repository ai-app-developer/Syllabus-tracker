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
import com.example.ui.components.ReminderTimePickerDialog
import com.example.ui.components.SyllabusBottomBar
import com.example.ui.components.TimerRecoveryDialog
import com.example.ui.navigation.BookDetailRoute
import com.example.ui.navigation.BooksRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.MoreRoute
import com.example.ui.navigation.ProgressRoute
import com.example.ui.navigation.ReadingHistoryRoute
import com.example.ui.navigation.StudyTimerSetupRoute
import com.example.ui.navigation.StudyingRoute
import com.example.ui.screens.BookDetailScreen
import com.example.ui.screens.BooksScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.ReadingHistoryScreen
import com.example.ui.screens.StudyTimerSetupScreen
import com.example.ui.screens.StudyingScreen
import com.example.ui.theme.AppBackground
import com.example.ui.theme.SyllabusTrackerTheme
import com.example.ui.viewmodel.SyllabusViewModel
import com.example.ui.viewmodel.TimerUiEvent
import com.example.ui.viewmodel.TimerViewModel
import com.example.widgets.WidgetIntents
import com.example.widgets.WidgetUpdater
import kotlinx.coroutines.launch

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
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val timerUiState by timerViewModel.uiState.collectAsStateWithLifecycle()
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
          app?.applicationScope?.launch {
            WidgetUpdater.updateNow(app)
          }
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

  LaunchedEffect(Unit) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        uiState.snapshot.preferences.reminderEnabled &&
        !NotificationHelper.hasNotificationPermission(context)
    ) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  val isSecondaryTimerOrDetailScreen =
    currentDestination?.hasRoute<BookDetailRoute>() == true ||
      currentDestination?.hasRoute<StudyTimerSetupRoute>() == true ||
      currentDestination?.hasRoute<StudyingRoute>() == true ||
      currentDestination?.hasRoute<ReadingHistoryRoute>() == true

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
        HomeScreen(
          snapshot = uiState.snapshot,
          liveStudying = timerUiState.liveStudying,
          innerPadding = innerPadding,
          onBellClick = { navigateToTab(MoreRoute) },
          onStudyTimerClick = {
            if (timerUiState.liveStudying.isRunningOrPaused) {
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
        BooksScreen(
          uiState = uiState,
          todayStudyMs = timerUiState.analytics.todayTotalMs,
          liveStudying = timerUiState.liveStudying,
          innerPadding = innerPadding,
          onToggleSearch = { viewModel.toggleSearchActive() },
          onSearchQueryChange = { viewModel.setSearchQuery(it) },
          onSelectFilter = { viewModel.setBookFilter(it) },
          onSelectSortOrder = { viewModel.setBookSortOrder(it) },
          onBookClick = { bookId -> navController.navigate(BookDetailRoute(bookId)) },
          onAddBookClick = { viewModel.openAddBookDialog() },
          onStudyTimerClick = {
            if (timerUiState.liveStudying.isRunningOrPaused) {
              navController.navigate(StudyingRoute) { launchSingleTop = true }
            } else {
              navController.navigate(StudyTimerSetupRoute) { launchSingleTop = true }
            }
          },
        )
      }

      composable<BookDetailRoute> { entry ->
        val route = entry.toRoute<BookDetailRoute>()
        val book by
          viewModel.observeBookById(route.bookId).collectAsStateWithLifecycle(initialValue = null)
        val chapters by
          viewModel
            .observeChaptersForBook(route.bookId)
            .collectAsStateWithLifecycle(initialValue = emptyList())

        BookDetailScreen(
          book = book,
          chapters = chapters,
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
        ProgressScreen(
          uiState = uiState,
          timerAnalytics = timerUiState.analytics,
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
        MoreScreen(
          preferences = uiState.snapshot.preferences,
          innerPadding = innerPadding,
          isTimerRunning = timerUiState.liveStudying.isRunningOrPaused,
          onOpenStudyTimer = {
            if (timerUiState.liveStudying.isRunningOrPaused) {
              navController.navigate(StudyingRoute) { launchSingleTop = true }
            } else {
              navController.navigate(StudyTimerSetupRoute) { launchSingleTop = true }
            }
          },
          onOpenReadingHistory = {
            navController.navigate(ReadingHistoryRoute) { launchSingleTop = true }
          },
          onEditDailyGoal = { viewModel.openDailyGoalDialog() },
          onToggleReminder = { viewModel.setReminderEnabled(it) },
          onEditReminderTime = { viewModel.openTimePicker() },
          onExportBackupUri = { uri -> viewModel.exportBackupToUri(uri) },
          onImportBackupUri = { uri -> viewModel.inspectBackupUri(uri) },
        )
      }

      composable<StudyTimerSetupRoute> {
        StudyTimerSetupScreen(
          uiState = timerUiState,
          innerPadding = innerPadding,
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
          liveState = timerUiState.liveStudying,
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
        ReadingHistoryScreen(
          uiState = timerUiState,
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
    }
  }

  timerUiState.recoveryPrompt?.let { recovery ->
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

  if (uiState.addChapterSheet.isOpen) {
    AddChapterBottomSheet(
      books = uiState.snapshot.books,
      preselectedBookId = uiState.addChapterSheet.preselectedBookId,
      editingChapter = uiState.addChapterSheet.editingChapter,
      onDismiss = { viewModel.closeAddChapterSheet() },
      onCreateBookClick = { viewModel.openAddBookDialog() },
      onSaveChapter = { bookId, name, number, isCompleted ->
        viewModel.saveChapter(
          bookId = bookId,
          name = name,
          number = number,
          isCompleted = isCompleted,
          editingChapter = uiState.addChapterSheet.editingChapter,
        )
      },
    )
  }

  if (uiState.bookFormDialog.isOpen) {
    BookFormDialog(
      editingBook = uiState.bookFormDialog.editingBook,
      onDismiss = { viewModel.closeBookFormDialog() },
      onConfirm = { name ->
        viewModel.saveBook(name = name, editingBook = uiState.bookFormDialog.editingBook)
      },
    )
  }

  ConfirmDeleteDialog(
    state = uiState.deleteConfirm,
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

  if (uiState.isDailyGoalDialogOpen) {
    DailyGoalDialog(
      currentGoal = uiState.snapshot.preferences.dailyGoal,
      onDismiss = { viewModel.closeDailyGoalDialog() },
      onSave = { goal -> viewModel.saveDailyGoal(goal) },
    )
  }

  if (uiState.isTimePickerOpen) {
    ReminderTimePickerDialog(
      initialHour = uiState.snapshot.preferences.reminderHour,
      initialMinute = uiState.snapshot.preferences.reminderMinute,
      onDismiss = { viewModel.closeTimePicker() },
      onConfirm = { hour, minute -> viewModel.saveReminderTime(hour, minute) },
    )
  }

  uiState.pendingBackupImport?.let { backup ->
    ImportBackupConfirmDialog(
      backup = backup,
      onDismiss = { viewModel.dismissImportConfirm() },
      onSelectMode = { mode -> viewModel.confirmImport(mode) },
    )
  }

  uiState.backupErrorMessage?.let { errorMsg ->
    BackupErrorDialog(
      message = errorMsg,
      onDismiss = { viewModel.dismissBackupError() },
    )
  }
}

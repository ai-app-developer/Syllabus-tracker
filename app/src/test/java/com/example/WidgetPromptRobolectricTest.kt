package com.example

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupManager
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.preferences.UserPreferences
import com.example.ui.components.WidgetPromptBottomSheet
import com.example.ui.screens.HomeScreenWidgetsScreen
import com.example.ui.theme.SyllabusTrackerTheme
import com.example.ui.viewmodel.SyllabusViewModel
import com.example.ui.viewmodel.WidgetPromptSheetState
import com.example.widgets.HomeWidgetType
import com.example.widgets.OverviewWidgetReceiver
import com.example.widgets.WidgetActionReceiver
import com.example.widgets.WidgetIntents
import com.example.widgets.WidgetPromptScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetPromptRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var app: SyllabusApplication

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext<Context>().applicationContext as SyllabusApplication
  }

  @Test
  fun `cooldown schedule enforces 2d 7d 14d and max 4 appearances`() {
    val t0 = 1_763_320_000_000L
    val day = WidgetPromptScheduler.DAY_MILLIS

    // 1st show: fresh install -> eligible immediately
    assertTrue(
      WidgetPromptScheduler.shouldShowWidgetPrompt(
        prefs = UserPreferences(widgetPromptShownCount = 0, widgetLastPromptTime = 0L),
        anyWidgetPlaced = false,
        nowMs = t0,
        isBlocked = false,
      )
    )

    // 2nd show: must wait 2 days
    assertFalse(
      WidgetPromptScheduler.shouldShowWidgetPrompt(
        prefs = UserPreferences(widgetPromptShownCount = 1, widgetLastPromptTime = t0),
        anyWidgetPlaced = false,
        nowMs = t0 + (2L * day) - 1_000L,
        isBlocked = false,
      )
    )
    assertTrue(
      WidgetPromptScheduler.shouldShowWidgetPrompt(
        prefs = UserPreferences(widgetPromptShownCount = 1, widgetLastPromptTime = t0),
        anyWidgetPlaced = false,
        nowMs = t0 + (2L * day),
        isBlocked = false,
      )
    )

    // 3rd show: must wait 7 days
    val t2 = t0 + 2L * day
    assertFalse(
      WidgetPromptScheduler.shouldShowWidgetPrompt(
        prefs = UserPreferences(widgetPromptShownCount = 2, widgetLastPromptTime = t2),
        anyWidgetPlaced = false,
        nowMs = t2 + (7L * day) - 1_000L,
        isBlocked = false,
      )
    )
    assertTrue(
      WidgetPromptScheduler.shouldShowWidgetPrompt(
        prefs = UserPreferences(widgetPromptShownCount = 2, widgetLastPromptTime = t2),
        anyWidgetPlaced = false,
        nowMs = t2 + (7L * day),
        isBlocked = false,
      )
    )

    // 4th show: must wait 14 days
    val t3 = t2 + 7L * day
    assertFalse(
      WidgetPromptScheduler.shouldShowWidgetPrompt(
        prefs = UserPreferences(widgetPromptShownCount = 3, widgetLastPromptTime = t3),
        anyWidgetPlaced = false,
        nowMs = t3 + (14L * day) - 1_000L,
        isBlocked = false,
      )
    )
    assertTrue(
      WidgetPromptScheduler.shouldShowWidgetPrompt(
        prefs = UserPreferences(widgetPromptShownCount = 3, widgetLastPromptTime = t3),
        anyWidgetPlaced = false,
        nowMs = t3 + (14L * day),
        isBlocked = false,
      )
    )

    // After 4th show: never auto-shows again
    val t4 = t3 + 14L * day
    assertFalse(
      WidgetPromptScheduler.shouldShowWidgetPrompt(
        prefs = UserPreferences(widgetPromptShownCount = 4, widgetLastPromptTime = t4),
        anyWidgetPlaced = false,
        nowMs = t4 + (365L * day),
        isBlocked = false,
      )
    )
  }

  @Test
  fun `blockers and placed widget skip silently without incrementing prompt count`() = runTest {
    val t0 = 1_763_320_000_000L

    // Timer active / recovery prompt / startup permission / dialog open / widget placed all block
    assertFalse(
      WidgetPromptScheduler.shouldShowWidgetPrompt(
        prefs = UserPreferences(),
        anyWidgetPlaced = false,
        nowMs = t0,
        isBlocked = true,
      )
    )
    assertFalse(
      WidgetPromptScheduler.shouldShowWidgetPrompt(
        prefs = UserPreferences(),
        anyWidgetPlaced = true,
        nowMs = t0,
        isBlocked = false,
      )
    )
    assertFalse(
      WidgetPromptScheduler.shouldShowWidgetPrompt(
        prefs = UserPreferences(widgetEverAdded = true),
        anyWidgetPlaced = false,
        nowMs = t0,
        isBlocked = false,
      )
    )

    // ViewModel blocked check skips without touching DataStore count
    val vmBlocked = SyllabusViewModel(app)
    vmBlocked.checkWidgetPromptOnAppStart(
      isTimerActive = true,
      hasRecoveryPrompt = false,
      isStartupPermissionPending = false,
      isCurrentlyOnHome = true,
      clockNowMs = t0,
    )
    delay(150)
    assertEquals(WidgetPromptSheetState.Hidden, vmBlocked.widgetPromptSheet.value)
    assertEquals(0, app.preferencesRepository.userPreferences.first().widgetPromptShownCount)

    // ViewModel placed-widget check marks widgetEverAdded and stays hidden
    val appWidgetManager = AppWidgetManager.getInstance(app)
    Shadows.shadowOf(appWidgetManager)
      .createWidgets(ComponentName(app, OverviewWidgetReceiver::class.java), 0, 1)
    val vmPlaced = SyllabusViewModel(app)
    vmPlaced.checkWidgetPromptOnAppStart(
      isTimerActive = false,
      hasRecoveryPrompt = false,
      isStartupPermissionPending = false,
      isCurrentlyOnHome = true,
      clockNowMs = t0,
    )
    delay(150)
    assertEquals(WidgetPromptSheetState.Hidden, vmPlaced.widgetPromptSheet.value)
    val updatedPrefs = app.preferencesRepository.userPreferences.first()
    assertTrue(updatedPrefs.widgetEverAdded)
    assertEquals(0, updatedPrefs.widgetPromptShownCount)
  }

  @Test
  fun `pin success receiver marks widgetEverAdded refreshes widget and emits snackbar`() = runTest {
    val vm = SyllabusViewModel(app)
    val snackbars = mutableListOf<String>()
    val collectJob =
      launch {
        vm.snackbarMessages.collect { msg -> snackbars.add(msg) }
      }

    val receiver = WidgetActionReceiver()
    receiver.onReceive(
      app,
      Intent(WidgetIntents.ACTION_WIDGET_PIN_SUCCESS).apply {
        putExtra(WidgetIntents.EXTRA_WIDGET_KEY, HomeWidgetType.OVERVIEW.key)
      },
    )
    delay(250)
    collectJob.cancel()

    assertTrue(app.preferencesRepository.userPreferences.first().widgetEverAdded)
    assertTrue(snackbars.contains(app.getString(R.string.widget_added_snackbar)))
    assertEquals(WidgetPromptSheetState.Hidden, vm.widgetPromptSheet.value)
  }

  @Test
  fun `unsupported launcher switches prompt and settings to manual steps fallback cleanly`() = runTest {
    val appWidgetManager = AppWidgetManager.getInstance(app)
    Shadows.shadowOf(appWidgetManager).setRequestPinAppWidgetSupported(false)

    val vm = SyllabusViewModel(app)
    vm.checkWidgetPromptOnAppStart(
      isTimerActive = false,
      hasRecoveryPrompt = false,
      isStartupPermissionPending = false,
      isCurrentlyOnHome = true,
      clockNowMs = 1_763_320_000_000L,
    )
    delay(150)
    assertEquals(
      WidgetPromptSheetState.Visible(isFallbackManualSteps = false),
      vm.widgetPromptSheet.value,
    )

    vm.onWidgetPromptSheetVisible(1_763_320_000_000L)
    delay(100)
    assertEquals(1, app.preferencesRepository.userPreferences.first().widgetPromptShownCount)

    vm.requestPinOverviewFromPrompt()
    assertEquals(
      WidgetPromptSheetState.Visible(isFallbackManualSteps = true),
      vm.widgetPromptSheet.value,
    )

    vm.requestPinWidgetFromSettings(HomeWidgetType.DARK_PROGRESS)
    assertTrue(vm.showMoreWidgetFallbackSheet.value)
    vm.dismissMoreWidgetFallbackSheet()
    assertFalse(vm.showMoreWidgetFallbackSheet.value)
  }

  @Test
  fun `backup export does not include widget prompt keys and restore preserves them`() = runTest {
    app.preferencesRepository.recordWidgetPromptShown(1_763_320_555_000L)
    app.preferencesRepository.markWidgetEverAdded()

    val bookId = app.database.syllabusDao().insertBook(BookEntity(name = "Clean Code"))
    app.database.syllabusDao().insertChapter(
      ChapterEntity(bookId = bookId, name = "Meaningful Names", number = 1, isCompleted = true)
    )
    val backupJson =
      BackupManager.serializeBackup(
        app.syllabusRepository.buildBackupSnapshot("1.0")
      )
    assertFalse(backupJson.contains("widgetPromptShownCount"))
    assertFalse(backupJson.contains("widgetLastPromptTime"))
    assertFalse(backupJson.contains("widgetEverAdded"))

    app.preferencesRepository.restorePreferences(UserPreferences(dailyGoal = 7))
    val afterRestore = app.preferencesRepository.userPreferences.first()
    assertEquals(7, afterRestore.dailyGoal)
    assertEquals(1, afterRestore.widgetPromptShownCount)
    assertEquals(1_763_320_555_000L, afterRestore.widgetLastPromptTime)
    assertTrue(afterRestore.widgetEverAdded)
  }

  @Test
  fun `prompt sheet UI shows real data sample empty state and fallback steps`() {
    var addClicked = false
    var dismissed = false
    var fallbackMode = false

    composeTestRule.setContent {
      SyllabusTrackerTheme {
        WidgetPromptBottomSheet(
          totalBooks = 2,
          totalChapters = 6,
          completedChapters = 4,
          overallPercentage = 67,
          isFallbackManualSteps = fallbackMode,
          isPinBusy = false,
          onBecameVisible = {},
          onAddWidgetClick = { addClicked = true },
          onDismiss = { dismissed = true },
        )
      }
    }

    composeTestRule.onNodeWithText("Your progress, one glance away").assertIsDisplayed()
    composeTestRule
      .onNodeWithText("Add a widget to your home screen and see your syllabus without opening the app.")
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("67%").assertIsDisplayed()
    composeTestRule.onNodeWithTag("widget_prompt_add_button").performClick()
    assertTrue(addClicked)
    composeTestRule.onNodeWithTag("widget_prompt_not_now_button").performClick()
    assertTrue(dismissed)
  }

  @Test
  fun `home screen widgets catalog displays all 5 widgets and added count badges`() {
    val clicked = mutableListOf<HomeWidgetType>()

    composeTestRule.setContent {
      SyllabusTrackerTheme {
        HomeScreenWidgetsScreen(
          widgetCounts =
            mapOf(
              HomeWidgetType.OVERVIEW to 2,
              HomeWidgetType.DARK_PROGRESS to 0,
              HomeWidgetType.TODAYS_PLAN to 1,
              HomeWidgetType.DONE_RING to 0,
              HomeWidgetType.CONTINUE_STUDYING to 0,
            ),
          isPinBusy = false,
          showFallbackSheet = false,
          innerPadding = PaddingValues(),
          onBackClick = {},
          onRefreshCounts = {},
          onAddWidgetClick = { clicked.add(it) },
          onDismissFallbackSheet = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Overview").assertIsDisplayed()
    composeTestRule.onNodeWithText("Added \u00d72").assertIsDisplayed()
    composeTestRule.onNodeWithTag("widget_add_button_${HomeWidgetType.OVERVIEW.key}").performClick()
    assertEquals(listOf(HomeWidgetType.OVERVIEW), clicked)
    composeTestRule.onNodeWithText("Dark Progress").assertIsDisplayed()
  }
}

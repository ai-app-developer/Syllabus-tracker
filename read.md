# Syllabus Tracker

## 1. App Idea & Full Feature List

**Syllabus Tracker** is an offline-first personal study and syllabus tracking Android application with no login and no backend. Users organize their study material into Books and Chapters, track chapter completion, view computed overall and per-book progress, set a daily chapter goal, monitor weekly study trends, and receive daily study reminder notifications.

### Features
- **Books & Chapters Management**: Create, rename, and delete Books (with confirmation dialog and cascade chapter deletion). Add, edit, check/uncheck, and delete Chapters (with confirmation dialog).
- **Computed Progress**: Overall syllabus percentage, book count, total chapters, completed chapters, remaining chapters, and per-book completion percentages are computed reactively from Room DB.
- **Home Summary Dashboard**: Circular progress ring, Books/Chapters/Completed stats row, Today's Plan card with editable daily chapter goal, and Continue Studying card linking directly to the last opened book and next incomplete chapter.
- **Search, Filter & Sort**: Search books by title, filter by `All` / `In Progress` / `Completed`, and sort by recently added or alphabetical order.
- **Quick Add Chapter Sheet**: Accessible from the center `+` button in the bottom navigation bar across primary screens, with book dropdown, chapter name validation, numeric chapter number validation, and "Mark as Completed" switch.
- **Progress Analytics**: `Overall` and `By Book` tabs; overall percentage card with cumulative progress chart and Completed/Remaining/Total breakdown; weekly `Study Trend` bar chart (`This Week` / `Last Week`) computed from `completedAt` timestamps.
- **Daily Study Reminders**: Scheduled via `AlarmManager` at a user-chosen time (default 8:00 PM), showing remaining chapters for the day (e.g., `"2 chapters left today. Keep going!"`). Requests `POST_NOTIFICATIONS` at runtime on Android 13+ and reschedules after reboot via `BootCompletedReceiver`.
- **Local Data Backup (Export / Import)**: Zero-permission Storage Access Framework JSON export (`syllabus_tracker_backup_YYYY-MM-DD.json`) and validated import supporting **Replace** or **Merge** (deduplicating by book name + chapter number) inside an atomic Room transaction.
- **5 Android Home-Screen Widgets + Direct RemoteViews Refresh**: Classic `RemoteViews` + `AppWidgetProvider` widgets (`Overview`, `Dark Progress`, `Today's Plan`, `Done Ring`, `Continue Studying`) using native `ProgressBar` rings/bars, updated via a single aggregate Room query (`WidgetSummaryRow`), WAL-mode singleton DB, and direct `AppWidgetManager.updateAppWidget`. Includes `WidgetActionReceiver` (`goAsync()`) for direct widget chapter completion, conflated Flow observer, start/resume/boot/date/time/timezone triggers, and 30-minute WorkManager backup.
- **Empty States**: Clean empty states with call-to-action buttons when no books or no chapters exist.

---

## 2. Complete Prompt (Verbatim)

```text
Build a native Android app "Syllabus Tracker" using Kotlin + Jetpack Compose (Material 3). The attached image is the exact UI reference: recreate all 4 screens pixel-faithfully (layout, spacing, corner radius, icons, typography, bottom nav).

TARGET: Android 14 (API 34), minSdk 26. Offline-first, no login, no backend.

CORE IDEA
Personal syllabus tracker. I add Books, add Chapters inside each book, mark chapters completed, and the app shows overall % of syllabus finished. Daily push notification reminds me to study. Home screen shows everything in summary so I never need to open details.

SCREENS (match the image)
1. Home: title + subtitle, bell icon, circular progress ring (overall %), stats row (Books / Chapters / Completed), "Today's Plan" card (e.g. "Complete 2 chapters" — user-settable daily chapter goal), "Continue Studying" card (last opened book + next incomplete chapter, tap to open).
2. Books: search, filter chips (All / In Progress / Completed), book cards with icon, "x / y chapters", percent, thin progress bar, chevron. "Add Book" button at bottom. Tapping a book opens its chapter list where each chapter can be checked/unchecked, edited, deleted.
3. Add Chapter (bottom sheet): book dropdown, Chapter Name, Chapter Number, "Mark as Completed" switch, "Add Chapter" button.
4. Progress: Overall / By Book tabs, % card with simple chart, Completed / Remaining / Total, "Study Trend" weekly bar chart (chapters completed per day, This Week).
Bottom nav: Home, Books, center "+" button (quick add chapter), Progress, More (settings: notification time, daily goal, about).

FUNCTIONALITY
- Room database: Book(id, name, createdAt), Chapter(id, bookId, name, number, isCompleted, completedAt). Progress is always computed from DB.
- MVVM + StateFlow + Hilt (or manual DI), single-activity, Navigation Compose.
- Daily push notification via WorkManager/AlarmManager at user-chosen time (default 8:00 PM). Message example: "2 chapters left today. Keep going!" Request POST_NOTIFICATIONS permission at runtime (Android 13+). Re-schedule after reboot.
- Empty states for no books / no chapters (simple text + button).
- Delete with confirmation. Input validation (name required, number numeric).

DESIGN RULES (very important)
- Must look like it was made by a real human developer, NOT AI-generated. Clean, minimal, nothing extra.
- Colors: white / very light grey-blue background (#F6F7FB), cards white with subtle 1dp elevation or hairline border, single primary indigo (#4F46E5), light indigo tint for chips/icon backgrounds (#EEF0FF), success green (#22A06B) only for completed checks. Text: near-black #1A1A2E, secondary grey #6B7280.
- NO glow, NO neon, NO gradients on UI, NO glassmorphism, NO decorative blobs. Flat, solid colors only.
- Animations: minimal and fast (max 150–200ms, standard fade/ripple only). No slow or looping animations.
- Use system font (Roboto), standard Material icons. Corner radius 16dp cards, 12dp inputs.
- No extra features, banners, ads, or placeholder content beyond what is listed.
```

---

## 3. Design Tokens

### Colors (Flat, Solid Only)
- `AppBackground`: `#F6F7FB`
- `CardSurface`: `#FFFFFF`
- `HairlineBorder`: `#E8EAF0`
- `PrimaryIndigo`: `#4F46E5`
- `LightIndigoTint`: `#EEF0FF`
- `SecondaryIndigoBar`: `#C7D2FE`
- `ProgressTrack`: `#E5E7EB`
- `SuccessGreen`: `#22A06B`
- `LightGreenTint`: `#E8F7F0`
- `TextPrimary`: `#1A1A2E`
- `TextSecondary`: `#6B7280`
- `ErrorRed`: `#DC2626`
- `LightRedTint`: `#FEE2E2`

### Corner Radius & Elevation
- Cards / Dialogs: `16.dp` corner radius, `1.dp` hairline border (`#E8EAF0`), `1.dp` subtle shadow elevation
- Inputs / Dropdowns / Icon Containers: `12.dp` corner radius
- Modal Bottom Sheet Top Corners: `24.dp`
- Primary Pill Buttons / Filter Chips: `26.dp` – `50%`
- Study Trend Bar Top Corners: `6.dp`

### Typography
- Font Family: System default (`Roboto`)
- Headline Large: `28.sp` Bold
- Headline Medium: `24.sp` Bold
- Title Large: `18.sp` Bold
- Title Medium: `16.sp` SemiBold
- Body Large: `15.sp` Normal
- Body Medium: `13.sp` Normal
- Label Large/Medium/Small: `14.sp` SemiBold / `12.sp` Medium / `11.sp` Medium

### Animation Rules
- Max duration `150–180ms` (`tween(150)` / `tween(180)`)
- Standard fades, short ring arc sweep, and Material ripples only
- No glow, no neon, no gradients, no glassmorphism, no decorative blobs, no looping animations

---

## 4. Tech Stack, Package Structure & DB Schema

### Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Navigation**: Navigation Compose (`@Serializable` type-safe routes)
- **Persistence**: Room (`BookEntity`, `ChapterEntity`, `SyllabusDao`, `SyllabusDatabase`) + DataStore Preferences (`UserPreferencesRepository`)
- **Architecture**: MVVM + `StateFlow` + Repository + Manual DI (`SyllabusApplication`)
- **Notifications & Foreground Service**: `AlarmManager` + `BroadcastReceiver` (`StudyReminderReceiver`, `BootCompletedReceiver`, `TimerAlarmReceiver`) + `NotificationCompat`; `TimerForegroundService` (`foregroundServiceType="specialUse"`) runs the ongoing chronometer notification (`timer_ongoing_channel`) and the **PRIMARY** exact coroutine boundary alarm (`elapsedRealtime()`), while `TimerAlarmScheduler` simultaneously schedules a **BACKUP** `AlarmManager.setAlarmClock` at `eventTime + 2000 ms` (`TimerAlarmReceiver`), both sharing `TimerAlarmPlayer.fireEvent(...)` with Mutex + Room + `PrefsAlarmDeduplicator` deduplication via the `"timer_alarm_v2"` channel (`IMPORTANCE_HIGH`, `USAGE_ALARM`), `MediaPlayer` raw beep primary + `isPlaying`-verified `Ringtone` fallbacks, and a 20-entry `AlarmDiagnosticLog`.

### Package / Folder Structure
```text
app/src/main/java/com/example/
├── MainActivity.kt
├── SyllabusApplication.kt
├── data/
│   ├── backup/
│   │   ├── BackupManager.kt
│   │   └── BackupModels.kt
│   ├── local/
│   │   ├── SyllabusDatabase.kt
│   │   ├── dao/
│   │   │   ├── SyllabusDao.kt
│   │   │   └── TimerDao.kt
│   │   └── entity/
│   │       ├── BookEntity.kt
│   │       ├── ChapterEntity.kt
│   │       └── TimerEntities.kt
│   ├── preferences/
│   │   └── UserPreferencesRepository.kt
│   └── repository/
│       ├── SyllabusRepository.kt
│       └── TimerRepository.kt
├── notifications/
│   ├── BootCompletedReceiver.kt
│   ├── NotificationHelper.kt
│   ├── StudyReminderReceiver.kt
│   └── StudyReminderScheduler.kt
├── timer/
│   ├── TimerAlarmPlayer.kt
│   ├── TimerAlarmScheduler.kt
│   ├── TimerAnalytics.kt
│   ├── TimerForegroundService.kt
│   └── TimerMath.kt
├── widgets/
│   ├── ContinueStudyingWidget.kt
│   ├── DarkProgressWidget.kt
│   ├── DoneRingWidget.kt
│   ├── OverviewWidget.kt
│   ├── TodaysPlanWidget.kt
│   ├── WidgetActionReceiver.kt
│   ├── WidgetIntents.kt
│   ├── WidgetRefreshWorker.kt
│   └── WidgetUpdater.kt
└── ui/
    ├── components/
    │   ├── AddChapterSheet.kt
    │   ├── BottomNavBar.kt
    │   ├── Dialogs.kt
    │   └── TimerDialogs.kt
    ├── navigation/
    │   └── Routes.kt
    ├── screens/
    │   ├── BookDetailScreen.kt
    │   ├── BooksScreen.kt
    │   ├── HomeScreen.kt
    │   ├── MoreScreen.kt
    │   ├── ProgressScreen.kt
    │   ├── ReadingHistoryScreen.kt
    │   ├── StudyTimerSetupScreen.kt
    │   └── StudyingScreen.kt
    ├── theme/
    │   ├── Color.kt
    │   ├── Theme.kt
    │   └── Type.kt
    └── viewmodel/
        ├── SyllabusViewModel.kt
        └── TimerViewModel.kt
```

### Room DB Schema (`version = 4`, non-destructive `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`)
- **`books` table**: `id: Long` (PK, autoGenerate), `name: String`, `createdAt: Long`
- **`chapters` table**: `id: Long` (PK, autoGenerate), `bookId: Long` (FK -> `books.id`, `ON DELETE CASCADE`, indexed), `name: String`, `number: Int`, `isCompleted: Boolean`, `completedAt: Long?`
- **`reading_sessions` table**: `id: Long` (PK), `sessionId: String` (unique index), `bookId: Long` (FK CASCADE, indexed), `chapterId: Long` (FK CASCADE, indexed), `mode` (`STOPWATCH`/`POMODORO`), `status` (`SAVED`), `startWallTimeMs`, `endWallTimeMs`, `activeFocusMs`, `pauseMs`, `breakMs`, `goalMs` (nullable), `notes`, `sessionDate` (`yyyy-MM-dd`, indexed, split across midnight), `createdAt`
- **`session_events` table**: `id: Long` (PK), `sessionId` (indexed), `bookId` (FK CASCADE, indexed), `chapterId` (FK CASCADE, indexed), `eventType` (`START`/`PAUSE`/`RESUME`/`LAP`/`BREAK_START`/`BREAK_SKIP`/`STOP`), `elapsedAtEventMs`, `wallTimeMs`, `label`
- **`reading_goals` table**: `id: Long` (PK), `periodType` (`DAILY`/`WEEKLY`/`MONTHLY`, indexed), `targetMinutes`, `isActive`, `createdAt`
- **`active_timer_state` table**: Single-row crash/reboot state (`id = 1`), `sessionId`, `bookId` (FK CASCADE), `chapterId` (FK CASCADE), `mode`, `status`, `focusDurationMs`, `breakDurationMs`, `goalMs`, `notes`, `sessionStartWallTimeMs`, `phaseStartElapsedMs`, `phaseStartWallTimeMs`, `accumulatedFocusMs`, `accumulatedBreakMs`, `accumulatedPauseMs`, `pauseStartElapsedMs`, `pauseStartWallTimeMs`, `lastKnownWallTimeMs`, `lapCount`, `goalNotified`, `pomodoroCycle`, `phaseCarryMs` (in-phase carried ms across pauses/recovery), `pausedFromStatus` (`RUNNING`/`BREAK` when paused), `statusBeforeStopSummary`

---

## 5. Screen-by-Screen Description

1. **Home (`HomeScreen`)**: Displays `"Syllabus\nTracker"` title + subtitle, notification bell icon with pending-goal dot indicator, centered gradient circular progress ring showing overall syllabus %, 3-column stats card (`Books`, `Chapters`, `Completed`), lavender-gradient `"Study Timer"` card above `"Today's Plan"` (shows live elapsed time + book/chapter when running), clickable `"Today's Plan"` card, and `"Continue Studying"` card pointing to the last opened book and next incomplete chapter.
2. **Books (`BooksScreen`) & Book Chapters (`BookDetailScreen`)**: Header with inline expandable search field and sort/add overflow menu; pill filter chips (`All`, `In Progress`, `Completed`); book cards with subject-colored icon badges, `"x / y chapters"`, percentage, progress bar, and chevron; `"+ Add Book"` lavender pill button; and bottom `"Study Timer"` card displaying real today total (`Today: X`).
3. **Add Chapter Sheet (`AddChapterBottomSheet`)**: Modal bottom sheet opened via the center `+` button. Features book dropdown selector, `Chapter Name`, `# Chapter Number`, `"Mark as Completed"` switch, and full-width `"Add Chapter"` pill button.
4. **Progress (`ProgressScreen`)**: 3 independent pill tabs (`Overall`, `By Book`, `Timer`). `Overall` shows the percentage summary card with cumulative curve, `Completed / Remaining / Total` row, and `Study Trend` 7-day bar chart. `By Book` lists per-book progress cards. `Timer` displays real-data `"Study Time"` (`This Week` / `This Month` / `This Year`, `%` change, 7-bar Sat–Fri chart), `"Book-wise Study Time"` donut + legend, and `"Study Streak"` card linking to `ReadingHistoryScreen`.
5. **More (`MoreScreen`)**: Settings screen with **Reading Timer** links (`Study Timer` / active shortcut, `Reading History`), Daily Chapter Goal editor, Daily Study Reminder switch, Reminder Time picker, **Backup** section (`Export Data`, `Import Data`), and About section.
6. **Study Timer Setup (`StudyTimerSetupScreen`)**: Grouped container with `"Select Book"`, `"Select Chapter"`, and `"Timer Mode"` (`Stopwatch` gradient / `Pomodoro` with `25/5`, `50/10`, `Custom`), separate `"Set Goal (Optional)"` card, `"Notes (Optional)"` card, and gradient `"Start Timer"` CTA.
7. **Studying (`StudyingScreen`)**: `254.dp` circular dial with gradient arc and static layered pastel wave interior, book & chapter labels, `HH:MM:SS` tabular display, status pill (`Studying...` / `Paused` / `Break`), 3 metric tiles (`Goal`, `Elapsed`, `Focus`), `Pause/Resume` / gradient `Stop` / `Lap` controls, `Skip Break` action, `#EAF7EF` background info card, recent laps card, and `StopSessionSummaryDialog` (`Save`, `Resume`, `Discard`).
8. **Reading History (`ReadingHistoryScreen`)**: Search, book/chapter/date filters, sort options, session detail & laps viewer, inline notes editor, and delete with confirmation.
9. **Bottom Navigation (`SyllabusBottomBar`)**: Floating `28.dp`-rounded white pill bar with soft ambient shadow (`Home`, `Books`, raised `56.dp` violet-gradient `+` button, `Progress`, and `More`).

---

## 6. Current Progress Checklist

### Done
- [x] Project Gradle configuration (`minSdk 26`, `applicationId`, Room, DataStore, Navigation Compose, Kotlin Serialization, WorkManager; Glance removed)
- [x] Custom adaptive launcher icon and monochrome notification small icon
- [x] Material 3 UI Reference Design System (`Color.kt`, `Type.kt`, `Theme.kt`) with `#F3F4FC` canvas, `20.dp`–`22.dp` soft-shadow cards, and violet gradients
- [x] Room database (`SyllabusDatabase` v2 with `JournalMode.WRITE_AHEAD_LOGGING` and non-destructive `MIGRATION_1_2`)
- [x] DataStore preferences (`UserPreferencesRepository` with in-memory cache) and reactive `SyllabusRepository`
- [x] Daily reminder notifications (`NotificationHelper`, `StudyReminderScheduler`, `StudyReminderReceiver`, `BootCompletedReceiver`)
- [x] `SyllabusViewModel`, `TimerViewModel`, `TimerAnalyticsCalculator`, and `MainActivity` navigation host (including optimistic chapter toggle, `ON_RESUME` widget refresh, `ON_STOP` timer heartbeat, and notification deep-links)
- [x] All 5 UI Reference Screens + supporting screens (`HomeScreen`, `BooksScreen`, `BookDetailScreen`, `AddChapterBottomSheet`, `ProgressScreen` with `Overall`/`By Book`/`Timer`, `MoreScreen`, `StudyTimerSetupScreen`, `StudyingScreen`, `ReadingHistoryScreen`, `Dialogs`, floating `SyllabusBottomBar`)
- [x] 5 Classic `RemoteViews` Home-Screen Widgets (`Overview`, `Dark Progress`, `Today's Plan`, `Done Ring`, `Continue Studying`) with `WidgetUpdater.updateNow`
- [x] Static periwinkle wave background (`res/drawable-nodpi/bg_home_waves.xml`) behind `HomeScreen`
- [x] Local Backup Export / Import v1 (`BackupModels`, `BackupManager`, SAF pickers in `MoreScreen`)
- [x] **Reading Timer Core Engine & Analytics**:
  - [x] Room v2 schema (`ReadingSessionEntity`, `SessionEventEntity`, `ReadingGoalEntity`, `ActiveTimerStateEntity`, `TimerDao`) + `MIGRATION_1_2`
  - [x] `TimerMath` & `TimerRepository` (monotonic clock elapsed calculation, pause/resume/lap/Pomodoro transitions, midnight day-split saving, duplicate-save guard, single-active-session guard, crash/kill/reboot recovery)
  - [x] `TimerAnalyticsCalculator` computing real `This Week` (Sat–Fri) / `This Month` / `This Year` bars, previous-period `%` delta, top-4 + `"Others"` book donut slices, today's total, and streak days
  - [x] Android 14 `TimerForegroundService` (`foregroundServiceType="specialUse"`) + ongoing chronometer notification (`Pause`/`Resume` & `Stop` actions) & milestone alerts
- [x] **Primary + System Backup Study Timer Alarm System & Permission Checklist**: Restored in-service `TimerForegroundService` coroutine boundary wait as **PRIMARY** alarm at exact `eventTime`, paired with **BACKUP** `AlarmManager.setAlarmClock` scheduled at `eventTime + 2000 ms` (`FOCUS_END = 3101`, `BREAK_END = 3102`, `GOAL_REACHED = 3103`, `setAndAllowWhileIdle` fallback) via `TimerAlarmReceiver` (`exported="false"`, `goAsync()`); both paths invoke the shared `TimerAlarmPlayer.fireEvent(...)` -> `TimerRepository.fireEvent(...)` gated by `Mutex`, atomic Room transition/state flags (`goalNotified`, `status`, `pomodoroCycle`), and `PrefsAlarmDeduplicator` (`buildEventKey`) so whichever fires first wins cleanly and the second logs `"skipped duplicate ..."`; enforces 15s lateness gate, 3s `USAGE_ALARM` chime + 400ms vibration, `"timer_alarm"` channel notifications, boot/time/launch recovery, 6-row non-blocking `TimerPermissionsChecklistDialog` (`"Permissions needed for timer alarm"`) on first `Start Timer` tap + `More > "Check permissions"`, and Logcat `"TimerAlarm"` tracing
- [x] `compile_applet` build verification and `gradle :app:testDebugUnitTest` Robolectric test suite (`ExampleRobolectricTest` + `TimerRobolectricTest`)

### Pending (Phase 2 — Full Goals, Calendar Heatmap, Insights)
- [ ] Reading Goals management screen & daily/weekly/monthly minute targets with goal completed notification
- [ ] Longest streak & monthly activity calendar/heatmap + smart insights

### Pending (Phase 3 — Granular Notification Toggles)
- [ ] Granular per-type notification toggles in `MoreScreen` + scheduled daily summary & streak reminder

### Completed Backup & Widget Integration
- [x] Backup & Import Schema v2 (`reading_sessions`, `session_events`, `reading_goals`, full settings including Pomodoro focus/break durations, streaming `JsonWriter`/`JsonReader`, `schemaVersion = 1..2` compatibility, Replace/Merge ID remapping & deduplication)
- [x] Widget fast-refresh hook (`WidgetUpdater.updateNow`) after every session save/edit/delete and backup import
- [x] **Study Timer Pause/Resume Bug Fix (`phaseCarryMs` + `pausedFromStatus`, Room DB v4 `MIGRATION_3_4`)**: Atomic field-level Room `UPDATE` transitions inside `Mutex` (`pause()`/`resume()`/`togglePauseResume()`), `accumulatedFocusMs`/`accumulatedBreakMs` reserved strictly for completed phases, `phaseCarryMs` + `pausedFromStatus` carrying frozen in-phase progress across pauses/stop-summary/recovery without double-counting, `TimerAlarmScheduler` subtracting `phaseCarryMs`, shared `ACTION_TOGGLE_PAUSE_RESUME` notification + in-app button, silent `PAUSED` launch recovery, `SharingStarted.WhileSubscribed(..., Long.MAX_VALUE)` replay preservation, and Robolectric fake-clock + migration tests

### Known Bugs
- None identified.

---

## 7. Update Request (Verbatim)

```text
UPDATE REQUEST for the existing "Syllabus Tracker" Android app (Kotlin + Jetpack Compose). Do not change any existing screens, logic, database or design tokens except where stated below. First read read.md, then apply these two updates.

I have attached 2 images:
- Image 1 = Home-screen widget reference (5 widgets)
- Image 2 = Home-screen background reference

=====================================
UPDATE 1: ANDROID HOME-SCREEN WIDGETS (exactly 5)
=====================================
Use Jetpack Glance (androidx.glance:glance-appwidget) + AppWidgetProvider. These are real Android launcher widgets that the user adds from the phone's home screen, NOT in-app cards. Recreate each widget to match Image 1 as closely as possible.

Widget 1 – "Overview" (large, 4x2)
- Header: book icon + "Syllabus Tracker" + small "+" button (opens Add Chapter).
- Left: circular progress ring with "62%" and "Completed" inside.
- Right (after a thin divider): three rows – Books count, Chapters count, Completed count, each with an icon.

Widget 2 – "Dark Progress" (wide, 4x2, dark theme)
- Header: book icon + "Syllabus Tracker".
- Big "62%" + "Syllabus Completed" + thin horizontal progress bar.
- Right side: rounded "Continue Studying" tile with book icon and chevron (opens the last studied book).
- Dark solid colors (#1A1A2E background, indigo #4F46E5 progress bar). No glow.

Widget 3 – "Today's Plan" (4x2 or 3x2)
- Calendar icon + "Today's Plan" + chevron.
- Row: circle checkbox + "Complete 2 chapters" (uses user's daily goal).
- Thin progress bar with "0/2" (chapters completed today / daily goal).

Widget 4 – "Done Ring" (small, 2x2)
- Only a circular ring with "62%" and "Done" inside, on a light indigo tint background.

Widget 5 – "Continue Studying" (wide, 4x1 or 4x2)
- Book icon tile, subject name (e.g. "Mathematics"), "Chapter 9: Algebra", thin progress bar with percent (e.g. 50%), and a round play button on the right. Tapping opens that book's chapter list.

Note: Image 1 also shows a bottom bar (Books / Progress / Add / Settings). That is NOT a widget; it is only a reference for the in-app bottom navigation. Keep the in-app bottom nav as already built.

WIDGET RULES
- All data comes live from the Room database (real percentages, counts, last studied book, next incomplete chapter, today's completed count). No hardcoded numbers.
- Widgets auto-update whenever a chapter is added/completed/deleted (call GlanceAppWidget.updateAll) and also via periodic WorkManager refresh (every 30 minutes max, battery friendly).
- Circular rings in widgets: draw them as a Bitmap (Canvas drawArc, rounded stroke caps) and show via Glance Image, since Glance has no native arc.
- Widgets have rounded corners (24dp), solid colors only: light widgets use white/#F6F7FB with indigo #4F46E5 progress and #EEF0FF track; dark widget uses #1A1A2E. No glow, no neon, no gradients inside widgets, no shadows beyond system default.
- Provide a widget preview image and a short description for each widget (shown in the Android widget picker). Name them exactly: "Overview", "Dark Progress", "Today's Plan", "Done Ring", "Continue Studying".
- Support Android 14 (API 34), resizable widgets, and show a simple empty state ("Add a book to start") when there is no data.
- Tapping a widget opens the relevant screen of the app via PendingIntent / actionStartActivity.

=====================================
UPDATE 2: HOME SCREEN BACKGROUND
=====================================
Apply the design from Image 2 as the background of the in-app Home screen only.
- Very light lavender/periwinkle base (#EEF0FF to #F6F7FB) with soft, smooth, large wave-like curved shapes in light indigo/blue tints (up to #C7CCFB, small area near the right edge a bit stronger blue #8FA0FF as in the image).
- Add the image as a drawable resource (res/drawable-nodpi) and show it with ContentScale.Crop behind the content, OR recreate it with Compose Canvas using smooth Bézier paths. Prefer the drawable for exact match.
- Static background only: no animation, no parallax, no glow effects.
- All Home cards stay solid white with 1dp hairline border so the text remains clearly readable over the background.
- Status bar transparent (edge-to-edge) so the background shows behind it.
- Other screens (Books, Add Chapter, Progress, More) keep the plain #F6F7FB background.
```

---

## 8. Home-Screen Widgets & Background Details

### Home-Screen Widgets (`com.example.widgets`)
All 5 widgets use classic `AppWidgetProvider` + XML `RemoteViews` layouts (`res/layout/widget_*.xml`) and native `ProgressBar` ring/bar drawables (`widget_ring_progress_*.xml`, `widget_bar_*.xml`) with zero Bitmap rendering or Glance serialization overhead:
1. **Overview** (`4x2`, `widget_overview_info.xml`, `widget_overview.xml`, `OverviewWidgetReceiver`): Header with book icon, `"Syllabus Tracker"`, and `+` button opening Add Chapter; left `ProgressBar` circular ring (`widget_ring_progress_light.xml`: `#4F46E5` arc, `#EEF0FF` track); vertical divider; right 3-row counts (`Books`, `Chapters`, `Completed`).
2. **Dark Progress** (`4x2`, `widget_dark_progress_info.xml`, `widget_dark_progress.xml`, `DarkProgressWidgetReceiver`): Solid `#1A1A2E` rounded card with white header, overall `%`, `"Syllabus Completed"`, indigo `#4F46E5` horizontal `ProgressBar` (`widget_bar_dark.xml`), and `#262842` `"Continue Studying"` tile opening the active book.
3. **Today's Plan** (`3x2`, `widget_todays_plan_info.xml`, `widget_todays_plan.xml`, `TodaysPlanWidgetReceiver`): Calendar icon, `"Today's Plan"`, chevron, clickable goal checkbox row (sends `ACTION_COMPLETE_NEXT_CHAPTER` broadcast to `WidgetActionReceiver` which writes to Room and calls `WidgetUpdater.updateNow` inside `goAsync()`), `"Complete X chapters"` daily goal, and horizontal `ProgressBar` (`widget_bar_light.xml` / `widget_bar_green.xml`) with `completedToday/dailyGoal`.
4. **Done Ring** (`2x2`, `widget_done_ring_info.xml`, `widget_done_ring.xml`, `DoneRingWidgetReceiver`): Solid `#EEF0FF` background with centered `ProgressBar` circular ring (`widget_ring_progress_indigo.xml`) displaying overall `%` and `"Done"`.
5. **Continue Studying** (`4x1`, `widget_continue_studying_info.xml`, `widget_continue_studying.xml`, `ContinueStudyingWidgetReceiver`): Book tile, active book name, next incomplete chapter, horizontal `ProgressBar` + `%`, and circular play button opening that book's chapter list.

- **Direct Synchronous-Fast Refresh (`WidgetUpdater.updateNow`)**: Runs one single aggregate Room query (`SyllabusDao.getWidgetSummaryRow` on WAL-mode singleton `SyllabusDatabase`, paired with in-memory cached `UserPreferences`), skips widgets with zero placed IDs via `AppWidgetManager.getAppWidgetIds(ComponentName)`, skips `updateAppWidget` when `summary == lastSummary` (unless `force = true`), builds `RemoteViews` directly, and logs `Log.d("WidgetUpdater", "updateNow took X ms")`.
- **Concurrency & Triggers**: Guarded by a `Mutex` so only one update runs at a time (`WidgetRefreshWorker` calls `updateNowIfIdle()` and aborts immediately if busy). Invoked directly on `applicationScope` (`SupervisorJob() + Dispatchers.Default`) after every DB write, collected from `observeWidgetSummaryRow().conflate()` (no debounce) in `SyllabusApplication.onCreate()`, and triggered on app start, `ON_RESUME`, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `ACTION_DATE_CHANGED`, `ACTION_TIME_CHANGED`, and `ACTION_TIMEZONE_CHANGED`.

### Home Screen Background
- `res/drawable-nodpi/bg_home_waves.xml` rendered with `Image(..., contentScale = ContentScale.Crop)` behind `HomeScreen` content and extending behind the transparent edge-to-edge status bar (`SystemBarStyle.light(TRANSPARENT, TRANSPARENT)`). Other screens keep `#F6F7FB`.

---

## 9. Backup JSON Schema & RemoteViews Performance Fix Specification

### Performance Fix Request (Verbatim)
```text
PERFORMANCE FIX for the existing "Syllabus Tracker" Android app (Kotlin + Compose + Room). Read read.md first. Do NOT change any widget visual design, app UI, database schema or features. Only make widget refresh much faster (target: visible update within ~100-300 ms after a change in the app).

PROBLEM
Widgets update, but too slowly. Glance updateAll() recomposes and serializes the whole UI on every update, ring Bitmaps are redrawn, and debounce/WorkManager add delay.

REQUIRED CHANGES

1. REPLACE Glance with classic RemoteViews for all 5 widgets (Overview, Dark Progress, Today's Plan, Done Ring, Continue Studying).
   - Each widget = AppWidgetProvider + XML layout + appwidget-provider XML. Keep exactly the same look (24dp rounded solid backgrounds as shape drawables, same colors, icons, text).
   - Remove the Glance dependency after migration.

2. NO BITMAP RINGS. Draw circular rings using a ProgressBar in the RemoteViews layout:
   - Use a ring drawable (layer-list with two ring shapes: track + progress, using rotate/level or style="?android:attr/progressBarStyleHorizontal" with a circular progressDrawable), max=100, updated via remoteViews.setProgressBar(id, 100, percent, false).
   - Percent text via setTextViewText. Horizontal bars also via setProgressBar. This removes all Bitmap drawing and LruCache work.

3. DIRECT, SYNCHRONOUS-FAST UPDATE PATH
   - Create object WidgetUpdater with fun updateNow(context). It must:
     a) Run one single aggregate Room query (books count, chapters count, completed count, today's completed count, daily goal, last studied book + next chapter + its percent) returning one data class.
     b) Build RemoteViews for each widget and call AppWidgetManager.updateAppWidget(ids, views) directly. Use getAppWidgetIds(ComponentName) and skip widgets that have no placed instances.
   - Call it from an application-level CoroutineScope(SupervisorJob() + Dispatchers.Default) immediately after every DB write (add/edit/delete book or chapter, toggle complete, goal change, import, last-studied update). No delay(), no debounce on this path.
   - Keep the in-memory last summary; if the new summary equals the previous one, skip the update.

4. DATABASE OBSERVER (safety net): in Application.onCreate collect the summary Flow with NO debounce (use conflate()), and call updateNow on every emission. Make sure only one update runs at a time (conflate / Mutex) so updates never queue up.

5. Use ONE singleton Room instance shared by app and widgets. Enable WAL mode and keep widget queries as COUNT/SUM only.

6. Triggers still required: app start, ON_RESUME, BOOT_COMPLETED, ACTION_DATE_CHANGED, ACTION_TIMEZONE_CHANGED, ACTION_TIME_CHANGED. Keep WorkManager (30 min) only as a backup, and make sure it never runs while another update is in progress.

7. Widget click handling: use PendingIntent.FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT with setOnClickPendingIntent. Any toggle inside a widget goes through a BroadcastReceiver that writes to Room, then calls updateNow before finishing (use goAsync()).

8. Manifest: all 5 receivers exported="true" with APPWIDGET_UPDATE intent-filter and correct meta-data. appwidget-provider XML: updatePeriodMillis="1800000", resizeMode="horizontal|vertical", widgetCategory="home_screen".

9. Keep a Log.d("WidgetUpdater", "updateNow took X ms") that prints how long each update took, so I can check it in Logcat.
```

### Backup JSON Schema (`schemaVersion = 1`)
```json
{
  "appVersion": "1.0",
  "schemaVersion": 1,
  "exportedAt": 1763320000000,
  "settings": {
    "dailyGoal": 2,
    "reminderEnabled": true,
    "reminderHour": 20,
    "reminderMinute": 0
  },
  "books": [
    {
      "id": 1,
      "name": "Mathematics",
      "createdAt": 1763310000000
    }
  ],
  "chapters": [
    {
      "id": 10,
      "bookId": 1,
      "name": "Algebra",
      "number": 1,
      "isCompleted": true,
      "completedAt": 1763315000000,
      "createdAt": 1763310000000
    }
  ]
}
```

---

## 10. Reading Timer & Analytics System Feature Request (Verbatim)

```text
FEATURE ADDITION for the existing "Syllabus Tracker" Android app (Kotlin + Jetpack Compose + Room + RemoteViews widgets). Read read.md first.

Attached:
1. Full specification document "Complete Reading Timer & Analytics System". Follow it for all logic, database, accuracy rules and acceptance criteria.
2. New UI design image (Home, Books, Study Timer setup, Studying screen, Progress with Timer tab). Match it closely.

Add the complete Reading Timer & Analytics System. Do not remove or break existing features, data, widgets, backup/import or the fast widget refresh. Existing user data must migrate safely (Room migration, no data loss).

NAVIGATION
Keep the current bottom nav (Home, Books, center "+", Progress, More). Where the spec's navigation differs, follow the image. Put Study Timer setup under Home/Books cards and the Progress "Timer" tab. Put Reading History and Reading Goals as rows in More and links inside the Timer tab.

REUSE EXISTING DATA
Reuse the existing Book and Chapter tables. Do not create duplicate book/chapter tables. Add new tables only: ReadingSession, SessionEvent, ReadingGoal (+ settings via the existing settings store). Add indexes on bookId, chapterId, session date. Handle session deletion/cascade when a book or chapter is deleted (with confirmation).

TIMER ENGINE (highest priority)
- Foreground Service for Android 14: FOREGROUND_SERVICE + FOREGROUND_SERVICE_SPECIAL_USE permissions, foregroundServiceType="specialUse" with the required subtype property, runtime POST_NOTIFICATIONS request.
- Time must come from SystemClock.elapsedRealtime(). Never count by incrementing a variable. Elapsed = now - start - totalPause.
- Persist timer state in Room only on state changes (start/pause/resume/stop), never every second. Store both monotonic and wall-clock timestamps.
- Single source of truth: TimerRepository exposing StateFlow<TimerState>. UI only observes it and ticks every second for display only.
- Only one active session at a time.
- Persistent notification with Pause/Resume and Stop actions, using setUsesChronometer (no per-second manual updates).
- Recovery after process death, crash, force stop or reboot: on next launch show a dialog "Recover session / Save up to last known time / Discard". After reboot use stored wall-clock time.
- Midnight crossing: split session time across days for analytics.
- Duplicate protection: unique sessionId, check status before saving.
- Pause time is never added to active reading time.

TIMER UI (match the image)
- Setup screen: Select Book, Select Chapter (chapters of chosen book), Timer Mode (Stopwatch / Pomodoro), optional Goal, optional Notes, Start Timer (disabled until book + chapter chosen).
- Studying screen: ring with book, chapter, big HH:MM:SS, status chip (Studying / Paused / Break), tiles Goal / Elapsed / Focus % (real value = active / total), buttons Pause/Resume, Stop, Lap, and the "running in background" info box.
- Stop opens a Session Summary dialog: Save / Discard / Resume.

POMODORO / FOCUS
Presets 25/5, 50/10, custom. Focus and Break tracked separately; only Focus counts as study time. Skip Break, End Session, focus-complete and break notifications.

ANALYTICS (real DAO queries only, SUM/GROUP BY)
- Daily, weekly, monthly, yearly totals; previous-period comparison; averages; most-read book/month.
- Book-wise analytics page and chapter-wise stats.
- Progress → Timer tab: period dropdown (Week/Month/Year), total time, % change, bar chart, book-wise donut + legend, streak card, simple monthly activity calendar/heatmap.
- Count only saved, valid sessions.

GOALS, STREAK, INSIGHTS
- Daily/weekly/monthly goals with progress ring, history, achievement %, and a "Goal completed" notification sent once.
- Streak: a day is active if it has at least one saved session. Current streak, longest streak, total active days.
- Smart Insights: short text lines from real saved data only. No fake or estimated numbers.

HISTORY
Reading History screen: all sessions, filter by book/chapter/date, sort by duration/recent, search, view details, edit, delete.

NOTIFICATIONS (each toggleable in More)
Running timer, daily goal reminder, focus complete, break reminder, daily summary, streak reminder.

INTEGRATION
- Add sessions and goals to Export/Import (bump schemaVersion, keep old backups importable).
- Call WidgetUpdater.updateNow after every session save/edit/delete. Do not redesign the 5 widgets.
- Home: add "Study Timer" card (shows live time + book/chapter when running). Books: add "Study Timer – Today: X" card with the real total.

DESIGN RULES (unchanged)
Flat solid colors: indigo #4F46E5, tint #EEF0FF, background #F6F7FB, text #1A1A2E, secondary #6B7280. No glow, no neon, no glassmorphism, no gradients, no slow animations (max 150-200 ms). It must look like a real human-made app. Light mode required; dark mode only if it does not risk breaking existing screens.

WORK ORDER (important, to avoid errors and limit issues)
Phase 1: DB migration, timer engine, foreground service, setup + studying screens, session save, recovery, history. Compile, then STOP and summarize.
Phase 2: Analytics, Progress → Timer tab, goals, streak, calendar, insights.
Phase 3: Pomodoro polish, all notifications, backup integration, widget refresh, home/books cards.
Start with Phase 1 only. I will say "continue Phase 2" and "continue Phase 3".

AFTER EACH PHASE
1. Update read.md: add this prompt verbatim, timer engine architecture, new DB schema, checklist (done / pending / known bugs).
2. Make sure the project compiles with no errors.
3. Every button, graph, timer control and notification must really work. No UI-only demos, no extra features.
```

### Timer Engine Architecture Notes
- **Monotonic Clock + Wall-Clock Persistence**: `TimerRepository` uses `ClockProvider` (`SystemClock.elapsedRealtime()` for zero-drift active/pause elapsed calculation and `System.currentTimeMillis()` for wall-clock session windows, midnight boundary splitting, and reboot recovery rebasing).
- **Single Active Session**: `ActiveTimerStateEntity` (`id = 1`) stores the in-flight state in Room on transitions (`START`, `PAUSE`, `RESUME`, `LAP`, `BREAK_START`, `BREAK_SKIP`, `STOP`) and `ON_STOP` / 30s service heartbeat (`lastKnownWallTimeMs`).
- **Midnight Split & Duplicate Protection**: `TimerMath.splitSessionAcrossDays` divides cross-midnight spans across local `yyyy-MM-dd` dates with deterministic `${sessionId}#${yyyy-MM-dd}` unique keys checked via `countSessionsBySessionId` prior to insert.
- **Foreground Service**: `TimerForegroundService` (`specialUse`) runs an ongoing `setUsesChronometer` notification with `Pause/Resume` and `Stop` pending intents, plus milestone alerts for focus/break transitions and session goal completion.

---

### Pending (Phase 2 — Full Goals, Calendar Heatmap, Insights)
- [ ] Reading Goals management screen & daily/weekly/monthly minute targets with goal completed notification
- [ ] Longest streak & monthly activity calendar/heatmap + smart insights

### Pending (Phase 3 — Granular Notification Toggles & Backup Schema v2)
- [ ] Granular per-type notification toggles in `MoreScreen` + scheduled daily summary & streak reminder
- [ ] Backup & Import Schema v2 (include `readingSessions` & `readingGoals` while keeping v1 importable)
- [ ] Widget fast-refresh hook (`WidgetUpdater.updateNow`) after every session save/edit/delete

---

## 11. UI Reference (5-Screen Visual Specification)

The visual design follows the 5-screen UI reference image across every core screen:

1. **Global Visual System**:
   - Canvas background `#F3F4FC` (`bg_home_waves.xml` static periwinkle waves on Home).
   - Cards: pure white `#FFFFFF` with `20.dp`–`22.dp` corner radius, subtle `1.dp` `#ECEEF8` border, and soft diffuse ambient shadows (`AmbientShadow = #181A1A2E`), `16.dp`–`18.dp` padding.
   - Primary Violet Gradient (`#7C5CF0` -> `#5B3FE0`) on primary CTAs (`Start Timer`, `Stop`, active `Timer Mode` tile, Home timer play circle, highest weekly bar, and raised center `+` nav button); `#6D4DEB` for active pills, tabs, and progress bars.
   - Floating Bottom Navigation (`SyllabusBottomBar`): `28.dp` rounded white pill bar (`64.dp` height) floating above navigation insets with a `56.dp` circular violet-gradient `+` button raised `-14.dp` above the bar.

2. **Screen 1 — Home (`HomeScreen`)**:
   - `"Syllabus\nTracker"` header (`31.sp` Bold) + subtitle + notification bell with unread goal dot.
   - `184.dp` circular syllabus ring with violet-to-blue linear gradient sweep (`#9E6EFF -> #6D4DEB -> #4F6BFF`), `42.sp` bold percentage + `"Completed"`.
   - 3-column stats card (`Books`, `Chapters`, `Completed`).
   - Lavender gradient `"Study Timer"` card above `"Today's Plan"`, displaying `"Track your reading time"` when idle or live elapsed clock + active book/chapter when running/paused, plus a circular gradient play button.
   - `"Today's Plan"` card + `"Continue Studying"` card.

3. **Screen 2 — Books (`BooksScreen`)**:
   - Header with expandable Search and Sort/Add menu; pill filter chips (`All`, `In Progress`, `Completed`).
   - `20.dp` book cards with subject-colored rounded icon tiles (`Σ`, `Aa`, flask, book), completion counts, `%` + chevron, and progress bar.
   - Wide `#EEEAFE` `"+ Add Book"` pill button and bottom lavender `"Study Timer"` card showing live `"Today: <real total>"` and chart icon.

4. **Screen 3 — Study Timer Setup (`StudyTimerSetupScreen`)**:
   - Grouped `22.dp` white card holding `"Select Book"`, `"Select Chapter"`, divider, and side-by-side `"Timer Mode"` tiles (`Stopwatch` gradient when active, `Pomodoro` with `25/5`, `50/10`, `Custom` presets).
   - Separate `"Set Goal (Optional)"` card, `"Notes (Optional)"` card with `#F7F8FD` input field, and `54.dp` pill violet-gradient `"Start Timer"` button.

5. **Screen 4 — Studying (`StudyingScreen`)**:
   - `254.dp` circular dial with gradient progress arc and static layered pastel lavender Bézier waves inside the white inner dial, `38.sp` tabular clock, and `"Studying..."` green-dot pill.
   - 3 rounded metric tiles (`Goal`, `Elapsed`, `Focus` with orange flame), `Pause` / large gradient `Stop` / `Lap` circular buttons, and `#EAF7EF` background status banner.

6. **Screen 5 — Progress (`ProgressScreen`)**:
   - 3 independent pill tabs (`Overall`, `By Book`, `Timer`).
   - `Timer` tab powered strictly by Room `reading_sessions`: `"Study Time"` card (`This Week` / `This Month` / `This Year` dropdown, formatted total, `%` change vs previous period, Sat–Fri bar chart with highest bar highlighted in violet gradient), `"Book-wise Study Time"` donut chart + legend (top 4 books + `"Others"`, or `"No study time yet"` empty ring), and `"Study Streak"` card with orange flame tapping through to `ReadingHistoryScreen`.

---

## 12. System-Driven Study Timer Alarm Architecture (Bug Fix)

### Bug Fix Request (Summary / Verbatim Scope)
```text
BUG FIX for the existing "Syllabus Tracker" Android app (Kotlin + Compose + Room). Read read.md first. Do NOT change UI, database schema, widgets or timer calculation logic. Only rebuild the Timer Alarm delivery so it fires at the exact time even when the app is in the background, screen off, or the process is frozen.

PROBLEM
The mini alarm (Pomodoro focus end, break end, goal reached) does not play on time when the app is in the background. It plays late only after I open the app. Cause: the alarm is triggered from an in-process Handler/coroutine/UI, which Meizu Flyme freezes or kills.

REQUIRED ARCHITECTURE (system-driven, not process-driven)
1. Remove every Handler/coroutine/UI-based alarm trigger. The only trigger is the system AlarmManager.
2. Schedule each upcoming event (focus end, break end, goal reached) with AlarmManager.setAlarmClock(AlarmClockInfo, PendingIntent). setAlarmClock is exempt from Doze and app standby and fires exactly on time. Calculate the trigger wall-clock time = System.currentTimeMillis() + remainingMillis.
3. Schedule Focus end and Goal reached separately with unique request codes (3101, 3102, 3103).
4. TimerAlarmReceiver (exported="false", goAsync()): validate active/unpaused/matching session & cycle, skip sound/vibration if delay > 15s (post notification only), play 3s USAGE_ALARM Ringtone (fallback bundled raw beep) + 400ms vibration, post on channel "timer_alarm" (IMPORTANCE_HIGH, CATEGORY_ALARM, channel alarm sound fallback), advance Room phase and chain next alarm directly in receiver.
5. Reschedule / cancel cleanly on Start, Pause, Resume, Stop/Discard/Save, Skip Break, Goal change, Reboot/App Update, and Time/Timezone change.
6. Exact alarm permission prompt before Start Timer ("Allow Alarms & reminders so the timer alarm rings on time"), fallback to setAndAllowWhileIdle + warning note in More if declined, and Battery optimization row in More for Flyme.
```

### Removed vs Added
- **Removed**:
  - In-process `TimerForegroundService` boundary waits and phase/goal milestone triggers (`advancePomodoroPhaseIfDue` / `checkAndMarkGoalAlertIfReached`).
  - `TimerViewModel` UI collector calls to `advancePomodoroPhaseIfDue` / `checkAndMarkGoalAlertIfReached`.
  - `TimerRepository.onTimerAlert` / `TimerAlertType` in-process trigger pipeline and `MediaPlayer`/`Handler` alarm stopping.
- **Added**:
  - `TimerAlarmScheduler` using `AlarmManager.setAlarmClock(AlarmClockInfo, PendingIntent)` (`PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE`) with unique request codes (`FOCUS_END = 3101`, `BREAK_END = 3102`, `GOAL_REACHED = 3103`) and `setAndAllowWhileIdle` fallback when exact alarm permission is denied.
  - `TimerAlarmReceiver` (`exported="false"`, `goAsync()`) handling validation, 15-second lateness suppression, 3s `RingtoneManager` `USAGE_ALARM` playback (`res/raw/timer_alarm_beep.wav` fallback) + 400ms vibration via `TimerAlarmPlayer`, `"timer_alarm"` channel notifications (`NotificationHelper`), and atomic Room state advancement + chained scheduling via `TimerRepository.handleAlarmEvent`.
  - `BootCompletedReceiver` + `SyllabusApplication` clock/reboot handling (`handleSystemTimeOrBootReschedule`), `ExactAlarmPermissionDialog`, exact-alarm pre-start check in `StudyTimerSetupScreen`, and conditional exact-alarm warning + `"Battery optimization"` row in `MoreScreen`.

---

## 13. Primary + Backup Alarm System & Permission Checklist Dialog (Bug Fix)

### Bug Fix Request (Verbatim Scope)
```text
BUG FIX for the existing "Syllabus Tracker" Android app (Kotlin + Compose + Room). Read read.md first. Do NOT change UI design (except the new permission dialog below), database schema, widgets, or timer calculation logic. Do NOT remove the current in-service alarm. It worked correctly when battery was set to Unrestricted. Only ADD a system-level backup and a permission checklist dialog.

PROBLEM
Sometimes the mini alarm (Pomodoro focus end, break end, goal reached) plays late, only after the app is opened, because the OS freezes the in-service Handler/coroutine.

SOLUTION: PRIMARY + BACKUP (both always scheduled together)
1. PRIMARY (keep as is): the in-service Handler/coroutine alarm that fires at the exact event time.
2. BACKUP (new): for every upcoming event (focus end, break end, goal reached) ALSO schedule AlarmManager.setAlarmClock(AlarmClockInfo, PendingIntent) at eventTime + 2000 ms. Use PendingIntent.FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT with a unique requestCode per event type. Trigger wall-clock time = System.currentTimeMillis() + remainingMillis + 2000.
3. Shared fire path & duplicate prevention: shared AlarmPlayer.fireEvent(...), guarded by Mutex + Room state + PrefsAlarmDeduplicator so whichever fires first executes sound/vibration/notification/phase advance once and the second skips cleanly. Skip sound/vibration if delay > 15s.
4. Cancel/reschedule both primary & backup together; restore backup alarms on BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_CHANGED, TIMEZONE_CHANGED, and app start.
5. Permission Checklist Dialog ("Permissions needed for timer alarm"): shown on first Start Timer tap (non-blocking, "Continue anyway") and accessible in More > Timer Alarm > "Check permissions", with 6 rows (Notifications, Alarms & reminders, Battery: Unrestricted, Alarm notification channel sound, Do Not Disturb note, Flyme / Meizu note) and live re-check on ON_RESUME.
```

### Architecture Summary
- **PRIMARY (`TimerForegroundService`)**: Observes `TimerRepository.observeActiveTimerState()`, computes exact remaining duration from `SystemClock.elapsedRealtime()` (`GOAL_REACHED` prioritized before `FOCUS_END` on exact tie), delays until exact event time, and invokes `TimerAlarmPlayer.fireEvent(..., AlarmFireSource.PRIMARY, ...)`.
- **BACKUP (`TimerAlarmScheduler` + `TimerAlarmReceiver`)**: For every `RUNNING`/`BREAK` target (`FOCUS_END = 3101`, `BREAK_END = 3102`, `GOAL_REACHED = 3103`), schedules `AlarmManager.setAlarmClock` at `triggerWallMs + 2000 ms` (`setAndAllowWhileIdle` fallback if `canScheduleExactAlarms()` is false) while carrying exact `triggerWallMs` in `EXTRA_TRIGGER_WALL_MS` for accurate lateness calculation (`delayMs = nowWall - triggerWallMs`). `TimerAlarmReceiver` (`exported="false"`, `goAsync()`) calls `TimerAlarmPlayer.fireEvent(..., AlarmFireSource.BACKUP, ...)`.
- **Shared Fire Gate (`TimerAlarmPlayer.fireEvent` & `TimerRepository.fireEvent`)**: Serialized by `Mutex` + Room state checks + `PrefsAlarmDeduplicator` (`buildEventKey`). First path advances phase/goal and chains next alarms; second logs `TimerAlarm: skipped duplicate <key>` and exits cleanly.
- **Permission Checklist Dialog (`TimerPermissionsChecklistDialog`)**: Tracked via DataStore `timerPermissionChecklistShown` so it appears automatically once on first `Start Timer` tap (non-blocking `"Continue anyway"` starts the timer immediately) and anytime from **More > Check permissions**, refreshing permission badges on `Lifecycle.Event.ON_RESUME`.

---

## 14. App Smoothness, SQL Analytics & Release R8 Performance Fix

### Scope Summary
- **CI + Release Build (`/.github/workflows/blank.yml`, `app/build.gradle.kts`, `app/proguard-rules.pro`)**: Workflow invokes `./gradlew assembleRelease` and uploads `app/build/outputs/apk/release/*.apk`; release `buildType` enables `isMinifyEnabled = true` and `isShrinkResources = true` with `proguard-android-optimize.txt`, `proguard-rules.pro`, and `debugConfig` signing.
- **Room Schema v3 & SQL Projections (`SyllabusDatabase`, `SyllabusDao`, `TimerDao`, `SyllabusRepository`)**:
  - Non-destructive `MIGRATION_2_3` adds 4 performance indexes: `index_chapters_bookId_isCompleted` (`chapters(bookId, isCompleted)`), `index_chapters_completedAt` (`chapters(completedAt)`), `index_reading_sessions_sessionDate_startWallTimeMs` (`reading_sessions(sessionDate, startWallTimeMs)`), and `index_reading_sessions_startWallTimeMs` (`reading_sessions(startWallTimeMs)`).
  - SQL projection/aggregate queries (`observeBookProgressRows`, `observeOverallProgressRow`, `observeCompletedTimestampsBetween`, `observeCompletedCountBefore`, `observeCompletedWithoutTimestampCount`, `observeTotalChapterCount`, `observeTodayFocusMs`, `observeTotalFocusMsBetween`, `observeBookFocusTotalsBetween`, `observeDailyFocusTotalsBetween`, `observeDistinctActiveDatesDescending`, `TimerAnalyticsCalculator.computeFromAggregates`) eliminate full-table reads from UI flows.
  - Batched `insertBooks` / `insertChapters` in `SyllabusRepository.importBackup` and optimistic chapter add/edit/toggle/delete state updates in `SyllabusViewModel`.
- **Scoped Compose State & Leaf Ticker Isolation (`MainActivity`, `SyllabusViewModel`, `TimerViewModel`, screens, `StableList<T>`)**:
  - Removed root-level `uiState` / `timerUiState` collection from `MainActivity`; each route collects only its own scoped `@Immutable` `StateFlow` (`WhileSubscribed(5000)`, `.distinctUntilChanged()`, `.flowOn(Dispatchers.Default)`).
  - Live `500ms` ticker `StateFlow` (`liveStudyingState`) is collected solely inside leaf composables (`HomeStudyTimerCard`, `BooksStudyTimerCard`, `ActiveTimerSetupBanner`, `StudyingScreen`).
  - Added `contentType` + stable `key`s to `LazyColumn`s, hoisted `Brush` gradients and `DateTimeFormatter`s, replaced deprecated `Icons.Outlined.MenuBook` with `Icons.AutoMirrored.Outlined.MenuBook`, and lightened scrolling card shadows.
- **Conflated Widgets, Startup & Logging (`WidgetUpdater`, `TimerForegroundService`, `SyllabusApplication`)**:
  - Unified `WidgetUpdater.requestUpdate(...)` using `Channel.CONFLATED` + 100ms coalescing window and `distinctUntilChanged()` before `AppWidgetManager.updateAppWidget`.
  - `.distinctUntilChanged()` on `TimerForegroundService` active metadata state, deferred WorkManager + startup work off the main thread in `SyllabusApplication.onCreate()`, and gated `Log.d` calls with `if (BuildConfig.DEBUG)`.

---

## 15. Backup Export / Import Full Study History Fix (`schemaVersion = 2`)

### Audit Findings (Previous v1 Gaps Fixed)
1. **Missing Tables in Export & Import**: v1 only exported/imported `books` and `chapters`, completely omitting `reading_sessions`, `session_events`, `reading_goals`, and `counts`.
2. **Missing Settings**: v1 `BackupSettings` saved only 6 fields (`dailyGoal`, `reminderEnabled`, `reminderHour`, `reminderMinute`, `timerAlarmEnabled`, `timerVibrationEnabled`), omitting `timerPermissionChecklistShown`, `lastOpenedBookId`, and Pomodoro durations (`pomodoroFocusMinutes`, `pomodoroBreakMinutes`).
3. **In-Memory JSON vs Streaming & Compatibility**: Replaced whole-string JSON parsing (`schemaVersion != 1` rejection) with `android.util.JsonWriter` / `android.util.JsonReader` streaming (`ByteLimitInputStream` 20 MB cap), supporting `schemaVersion in 1..2`.
4. **Replace/Merge Integrity & Post-Import Side Effects**: Replace preserves original IDs after clearing tables (`active_timer_state`, `session_events`, `reading_sessions`, `reading_goals`, `chapters`, `books`); Merge matches books by trimmed normalized name and chapters by `(book name + chapter number)`, remaps `bookId`/`chapterId` on imported sessions & events, prevents duplicates via `sessionId` and `(startWallTimeMs, targetChapterId, activeFocusMs)`, resets stale alarms/recovery prompts (`TimerRepository.resetAfterBackupImport`), updates widgets (`WidgetUpdater.updateNow(app, force = true)`), and displays export snackbar / import result summary dialog.

### Final `schemaVersion = 2` JSON Format
```json
{
  "appVersion": "1.0",
  "schemaVersion": 2,
  "exportedAt": 1763400000000,
  "counts": {
    "books": 1,
    "chapters": 1,
    "sessions": 1,
    "sessionEvents": 2,
    "goals": 1
  },
  "settings": {
    "dailyGoal": 2,
    "reminderEnabled": true,
    "reminderHour": 20,
    "reminderMinute": 0,
    "timerAlarmEnabled": true,
    "timerVibrationEnabled": true,
    "timerPermissionChecklistShown": true,
    "lastOpenedBookId": 1,
    "pomodoroFocusMinutes": 25,
    "pomodoroBreakMinutes": 5
  },
  "books": [
    { "id": 1, "name": "Mathematics", "createdAt": 1763310000000 }
  ],
  "chapters": [
    {
      "id": 10,
      "bookId": 1,
      "name": "Algebra",
      "number": 1,
      "isCompleted": true,
      "completedAt": 1763315000000,
      "createdAt": 1763310000000
    }
  ],
  "sessions": [
    {
      "id": 1,
      "sessionId": "uuid-1#2026-11-18",
      "bookId": 1,
      "bookName": "Mathematics",
      "chapterId": 10,
      "chapterName": "Algebra",
      "mode": "STOPWATCH",
      "status": "SAVED",
      "startWallTimeMs": 1763350000000,
      "endWallTimeMs": 1763353600000,
      "activeFocusMs": 3600000,
      "pauseMs": 0,
      "breakMs": 0,
      "activeDurationSeconds": 3600,
      "pauseDurationSeconds": 0,
      "goalMs": 5400000,
      "notes": "Practice problems",
      "sessionDate": "2026-11-18",
      "createdAt": 1763353600000
    }
  ],
  "sessionEvents": [
    {
      "id": 1,
      "sessionId": "uuid-1",
      "bookId": 1,
      "chapterId": 10,
      "eventType": "START",
      "elapsedAtEventMs": 0,
      "wallTimeMs": 1763350000000,
      "label": null
    }
  ],
  "goals": [
    {
      "id": 1,
      "periodType": "DAILY",
      "targetMinutes": 60,
      "isActive": true,
      "createdAt": 1763310000000
    }
  ]
}
```

---

## 16. Study Timer Pause / Resume Bug Fix

### Bug Fix Request (Verbatim)
```text
BUG FIX for the existing "Syllabus Tracker" Android app (Kotlin + Jetpack Compose + Room). Read read.md first. Do NOT change UI design, database schema (unless strictly required), widgets, alarm system, backup, or performance setup. Only fix the Pause bug.

PROBLEM
When I press Pause in the app (Studying screen), the timer resets (goes back to 00:00:00 or the session is lost) instead of freezing at the current elapsed time. Expected: Pause freezes the displayed time and active duration; Resume continues from the same value.

STEP 1: FIND THE ROOT CAUSE (report it before fixing)
Trace the full Pause path: UI button -> ViewModel -> TimerRepository -> Service -> Room -> StateFlow -> UI. Check every one of these likely causes and report which are real:
1. Pause handler overwrites or resets startTime/elapsedStart/totalPause (e.g. sets startTime = now, or clears state, or creates a new TimerState with default values).
2. Pause sends a new Intent to the Service (startForegroundService / onStartCommand) and onStartCommand re-initializes the session instead of only handling ACTION_PAUSE.
3. Elapsed formula keeps using "now" while paused. It must use pauseStart as the end point while status = PAUSED: elapsed = (if paused: pauseStartElapsed else now) - start - totalPause.
4. Room write on Pause uses @Insert(onConflict = REPLACE) on the timer-state row, which deletes and recreates it (and cascades to SessionEvent), or the new row has default/zero values. Use @Update or an UPSERT that keeps all existing fields.
5. The state Flow (stateIn / WhileSubscribed / distinctUntilChanged / optimistic update from the performance fix) emits an initial/idle/default value after Pause, so the UI shows 0 or "no active session".
6. The recovery/restore logic runs on Pause or on recomposition/ON_RESUME and treats the paused session as unfinished and resets or discards it.
7. The ticker coroutine or ViewModel is cancelled and recreated on Pause, resetting the locally held elapsed value. The UI must never hold its own counter; it only derives from stored timestamps.
8. Cancelling alarms on Pause (primary + backup) also clears or resets the timer state by mistake.
9. Pomodoro phase logic restarts the phase when status changes to PAUSED.
10. Notification Pause action works but the in-app Pause uses a different code path. Both must call the SAME single repository function.

STEP 2: REQUIRED FIX
- One single source of truth: TimerRepository.pause() and resume(), used by the app UI, notification actions and anything else.
- pause(): in a single guarded operation (Mutex) only if status == RUNNING: record pauseStartElapsed = SystemClock.elapsedRealtime() and pauseStartWall = System.currentTimeMillis(), set status = PAUSED, keep startTime, totalPause, bookId, chapterId, mode, goal, notes, session id, phase info UNCHANGED. Persist with a field-level UPDATE (one write). Cancel alarms. Do not touch anything else.
- resume(): only if status == PAUSED: totalPause += (now - pauseStartElapsed), clear pauseStart, status = RUNNING, persist, reschedule alarms for the remaining time.
- Elapsed/active time derivation (shared by UI, service, notification, summary, save):
  activeMillis = (if PAUSED then pauseStartElapsed else now) - startElapsed - totalPauseMillis
  While paused this value must be constant, and the displayed HH:MM:SS, ring, Elapsed tile and Focus % must freeze.
- Service: onStartCommand must handle ACTION_START, ACTION_PAUSE, ACTION_RESUME, ACTION_STOP separately. Only ACTION_START may create a session. A null/redelivered intent must restore from Room, never create a new session or reset. Keep the foreground notification alive while paused (show "Paused", stop the chronometer using setUsesChronometer(false) or show the frozen time), with a Resume action.
- UI: Studying screen shows status chip "Paused", Pause button becomes Resume. The screen state is derived only from the repository StateFlow (no local counters). The StateFlow must never emit an idle/default state while a session exists in Room.
- Recovery: a session with status PAUSED is restored as PAUSED with the same frozen elapsed time (no reset, no discard, no dialog needed unless the process died while RUNNING).
- Rotation, minimizing, screen off, process death while paused: time stays frozen at the same value.
- Pause/Resume repeated many times: active time = sum of running periods only. Pause time never counts as reading time. Stop/Save while paused saves the frozen active time and the correct pause duration (including the current open pause period).
- Pomodoro: pausing during Focus or Break freezes that phase's remaining time; resume continues the same phase (no restart, no alarm fired while paused).
- Add Log.d (inside BuildConfig.DEBUG) lines on pause/resume printing startElapsed, pauseStartElapsed, totalPause, activeMillis.

STEP 3: TESTS (verify in code and report)
- Run 30 s, Pause for 20 s, Resume: active time continues from 30 s, total pause 20 s.
- Pause, minimize app, open again: same frozen time, not reset.
- Pause, kill app from Recent apps, reopen: restored as Paused with same time.
- Pause from notification and from app both behave identically.
- Pause, Stop, Save: saved active time equals the frozen time.
- Pomodoro Pause/Resume: no phase restart, no duplicate alarm.
- Release build (R8) still works.

AFTER FINISHING
1. Report the root cause(s) you found and exactly what you changed.
2. Update read.md: add this prompt verbatim, the final pause/resume state machine (RUNNING <-> PAUSED, formulas), and the checklist (done / pending / known bugs).
3. Make sure the project compiles with no errors. No extra features, animations or decorations.
```

### Root Causes Identified & Fixed (5 Confirmed Causes)
1. **`TimerMath.computeMetrics()` Zeroed `phaseElapsed` When `PAUSED`**:
   - Previously returned `phaseElapsed = 0L` on `PAUSED`, resetting Pomodoro `currentPhaseRemainingMs` to the full phase duration (e.g., `25:00`) and `progressFraction` to `0f`.
2. **`TimerRepository.resumeSession()` Reset `phaseStart` Without `phaseCarryMs`**:
   - Setting `phaseStartElapsedMs = now` and `phaseStartWallTimeMs = now` without preserving prior in-phase duration lost the time already studied in that phase.
3. **Partial Phase Folded Into `accumulatedFocusMs` Double-Counted Focus (`pauseSession` / `requestStopSummary` + `fireEvent`)**:
   - Folding partial phase time into `accumulatedFocusMs` caused `fireEvent(FOCUS_END)` (which adds `focusDurationMs`) to double-count completed focus time.
4. **`TimerAlarmScheduler.computeScheduledTargets()` Ignored `phaseCarryMs`**:
   - `FOCUS_END`, `BREAK_END`, and `GOAL_REACHED` targets were computed from `phaseStart` only, ignoring carried time already elapsed before pause/recovery.
5. **`TimerRepository.resumeRecoveredSession()` Overwrote `PAUSED` to `RUNNING` (Plus Unlocked Toggle / `REPLACE` / Replay Cache)**:
   - Restoring a `PAUSED` session turned it into `RUNNING`; fixed alongside `MIGRATION_3_4` (`phaseCarryMs` + `pausedFromStatus`), `Mutex`-guarded `togglePauseResume()`, field-level `@Query UPDATE` mutations, shared `ACTION_TOGGLE_PAUSE_RESUME` notification action with `setUsesChronometer(false)`, and `WhileSubscribed(..., Long.MAX_VALUE)`.

### Final `phaseCarryMs` + `pausedFromStatus` State Machine & Formulas (Room v4 `MIGRATION_3_4`)
- **Schema (`ActiveTimerStateEntity`, DB v4 `MIGRATION_3_4`)**:
  - Added `phaseCarryMs: Long NOT NULL DEFAULT 0` and `pausedFromStatus: String?` (`RUNNING` / `BREAK`).
  - `accumulatedFocusMs` and `accumulatedBreakMs` track **completed phases only** (updated only on `FOCUS_END`, `BREAK_END`, or `skipBreak()`).
- **Pause (`RUNNING`/`BREAK -> PAUSED`)**:
  - Computes `carryMs = TimerMath.computeMetrics(...).currentPhaseElapsedMs` and `pausedPhase = current.status`.
  - Executes `TimerDao.updatePauseState(...)` setting `status = PAUSED`, `pausedFromStatus = pausedPhase`, `phaseCarryMs = carryMs`, `pauseStartElapsedMs = nowElapsed`, `pauseStartWallTimeMs = nowWall`, `lastKnownWallTimeMs = nowWall` without modifying `accumulatedFocusMs` / `accumulatedBreakMs` / `phaseStart*`. Cancels alarms (`onSyncAlarm(null)`).
- **Elapsed Math (`TimerMath.computeMetrics` & `computeLastKnownRecoveryMetrics`)**:
  - `liveDelta = if (RUNNING || BREAK) max(0, nowElapsed - phaseStartElapsedMs) else 0` (wall-clock fallback if monotonic clock reset across reboot).
  - `phaseElapsed = state.phaseCarryMs + liveDelta` (while `PAUSED` or `STOP_SUMMARY`, `liveDelta = 0` -> `phaseElapsed == state.phaseCarryMs`, frozen!).
  - `effectivePhaseStatus`: `state.status` (`RUNNING`/`BREAK`), `state.pausedFromStatus ?: RUNNING` (when `PAUSED`), or `state.statusBeforeStopSummary` / `pausedFromStatus` (when `STOP_SUMMARY`).
  - `activeFocusMs = accumulatedFocusMs + (if effectivePhaseStatus == RUNNING -> phaseElapsed else 0)`
  - `breakMs = accumulatedBreakMs + (if effectivePhaseStatus == BREAK -> phaseElapsed else 0)`
  - `pauseMs = accumulatedPauseMs + (if PAUSED -> max(0, nowElapsed - pauseStartElapsedMs) else 0)`
- **Resume (`PAUSED`/`STOP_SUMMARY -> RUNNING`/`BREAK`)**:
  - Resolves `targetStatus` from `pausedFromStatus` (`RUNNING` vs `BREAK`).
  - Adds open pause `extraPause` to `accumulatedPauseMs`, resets `phaseStartElapsedMs = nowElapsed`, `phaseStartWallTimeMs = nowWall`, keeps `phaseCarryMs` unchanged, clears `pausedFromStatus`, `statusBeforeStopSummary`, `pauseStart*` via `TimerDao.updateResumeState(...)`.
- **Alarm Scheduling (`TimerAlarmScheduler.computeScheduledTargets`)**:
  - Uses `phaseCarryMs` and `metrics.currentPhaseElapsedMs` / `metrics.activeFocusMs` so `FOCUS_END`, `BREAK_END`, and `GOAL_REACHED` fire after the exact remaining duration.
- **Phase Completion & Recovery**:
  - `fireEvent(FOCUS_END)` / `fireEvent(BREAK_END)` / `skipBreak()` add the completed phase duration to `accumulatedFocusMs` / `accumulatedBreakMs` and reset `phaseCarryMs = 0L`, `pausedFromStatus = null`.
  - `checkUnfinishedSessionOnLaunch()` and `resumeRecoveredSession()` restore `PAUSED` sessions as `PAUSED` with `phaseCarryMs` preserved, and rebase interrupted `RUNNING`/`BREAK` sessions into `phaseCarryMs` without double-counting `accumulatedFocusMs`.

---

## 17. Timer Alarm Delivery & Reliability Fix

### Bug Fix Request Summary (Prompt)
```text
BUG FIX for the existing "Syllabus Tracker" Android app (Kotlin + Compose + Room). Read read.md first. Do NOT change UI design (except one test button below), the pause/resume model (phaseCarryMs), database schema, widgets, backup or performance setup. Only fix the Timer Alarm so it reliably rings. Currently NO alarm rings at all (focus end, break end, goal reached). I analyzed the code; follow this root cause.
```

### Root Causes Fixed
1. **Self-Cancellation on `fireEvent()` (`TimerRepository` / `TimerForegroundService` / `TimerAlarmPlayer`)**:
   - Previously, `TimerRepository.fireEvent()` acquired `alarmDeduplicator` and updated Room BEFORE `TimerAlarmPlayer` suspended on `preferencesRepository.getCurrentPreferences()`. Room's Flow emitted the new phase to `TimerForegroundService.stateObserverJob`, which called `schedulePrimaryBoundaryAlarm()` -> `boundaryJob.cancel()`, aborting sound & notification at the suspension point while the backup `TimerAlarmReceiver` later skipped the event as `"skipped duplicate"`.
   - **Fix**: `TimerAlarmPlayer.fireEvent()` now reads preferences synchronously via `preferencesRepository.getCachedPreferencesSnapshot()` (backed by a synchronous `SharedPreferences` mirror in `UserPreferencesRepository`) BEFORE entering `repo.fireEvent()`, runs fire/delivery inside `withContext(NonCancellable)` on `applicationScope` in `TimerForegroundService` with an `activeFiringJob` guard so `schedulePrimaryBoundaryAlarm()` never cancels an active fire, and executes audio/vibration/notification delivery via `onBeforeCommitAttempt` BEFORE `alarmDeduplicator.tryAcquire()` and Room phase commit (so a crash before play leaves no fired mark and lets the backup still ring).
2. **Unverified `Ringtone.play()` & Missing Primary `MediaPlayer` Raw Fallback (`TimerAlarmPlayer`)**:
   - Previously, `Ringtone.play()` was treated as success even when `isPlaying` was false (common on Flyme/empty default URIs), leaving `res/raw/timer_alarm_beep.wav` untried and posting silent notifications.
   - **Fix**: `DefaultAlarmAudioBackend` now plays `res/raw/timer_alarm_beep.wav` FIRST using `MediaPlayer` (`USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`, `OnPreparedListener`, `OnErrorListener`, 1.5s prepare timeout, `OnCompletionListener`/3s release + audio focus abandon), then falls back to `RingtoneManager.TYPE_ALARM` and `RingtoneManager.TYPE_NOTIFICATION` verifying `ringtone.isPlaying` after a short 60ms settle before accepting success. When all audio candidates fail (and sound is enabled, not >15s late, and not DND-blocked), `NotificationHelper.showTimerAlert(..., silent = false)` lets the `"timer_alarm_v2"` channel play sound.
3. **Debug-Only Logging & Legacy Channel Stale State**:
   - **Fix**: Upgraded notification channel ID to `"timer_alarm_v2"` (deleting legacy `"timer_alarm"` on channel setup), kept `"TimerAlarm"` `Log.i` diagnostics active in release builds, added `AlarmDiagnosticLog` (20-entry `SharedPreferences` ring buffer, Room-free), and added a `"Test alarm"` row in `MoreScreen` opening `TimerAlarmTestDialog` (real report + `"Test in 10 seconds"` primary+`setAlarmClock` synthetic test + `"Alarm log"` dialog).

### Alarm Flow Architecture
```text
Timer Boundary / Backup Receiver / Test Alarm
  │
  ├─ Synchronous Prefs Read: preferencesRepository.getCachedPreferencesSnapshot()
  │    (in-memory cache OR SharedPreferences mirror — zero suspension)
  │
  ├─ Uncancellable Execution:
  │    • TimerForegroundService tracks activeFiringJob + launches on app.applicationScope
  │    • TimerAlarmPlayer.fireEvent & TimerRepository.fireEvent wrapped in withContext(NonCancellable)
  │
  ├─ Mutex Ordering (Check -> Deliver -> Commit):
  │    1. Validate sessionId, active status (RUNNING/BREAK), cycle, early tolerance, !dedup.isMarked
  │    2. Invoke onBeforeCommitAttempt -> TimerAlarmPlayer.deliverAlarmAlert():
  │         • Check DND, 15s lateness threshold, STREAM_ALARM volume %, notif permission, channel
  │         • Vibrate (400ms USAGE_ALARM) if enabled
  │         • Sound chain:
  │             [1] MediaPlayer (res/raw/timer_alarm_beep.wav)
  │             [2] Ringtone TYPE_ALARM (verified ringtone.isPlaying)
  │             [3] Ringtone TYPE_NOTIFICATION (verified ringtone.isPlaying)
  │             [4] Non-silent notification on "timer_alarm_v2" (fallback when 1-3 fail)
  │         • Record outcome in AlarmDiagnosticLog (last 20 entries in SharedPreferences) + Log.i
  │    3. Only after step 2 completes without throwing:
  │         • alarmDeduplicator.tryAcquire(eventKey, sessionId)
  │         • Commit Room phase transition / goalNotified + sync next AlarmManager targets
```

### Checklist
- [x] Room schema `version = 4` (`MIGRATION_1_2` + `MIGRATION_2_3` + `MIGRATION_3_4` with `phaseCarryMs` / `pausedFromStatus`) preserved unchanged
- [x] Uncancellable `fireEvent` + `activeFiringJob` guard in `TimerForegroundService` + synchronous `UserPreferencesRepository` mirror
- [x] `MediaPlayer` raw primary (`res/raw/timer_alarm_beep.wav`) + `isPlaying`-verified `Ringtone` fallbacks + `"timer_alarm_v2"` channel upgrade & legacy `"timer_alarm"` deletion
- [x] Delivery-before-commit ordering so pre-play crashes never poison backup firing
- [x] Release-safe `AlarmDiagnosticLog` (20 entries) + `More > Test alarm` diagnostics dialog + `"Test in 10 seconds"` + `"Alarm log"` dialog
- [x] Robolectric unit tests covering cancellation survival, cold pref cache, audio fallbacks, pre-play failure backup rescue, `timer_alarm_v2` upgrade, and 10s test dedup
- Known bugs: None reported

---

## 18. Resume Prompt

Read this file fully before editing to keep Room schema (`version = 4`, `MIGRATION_1_2` + `MIGRATION_2_3` + `MIGRATION_3_4`), Backup Schema v2 (`schemaVersion = 1..2` streaming JSON + Replace/Merge ID remapping), Release CI/R8 config, SQL aggregate/projection DAOs, scoped ViewModel/Compose `StateFlow`s, conflated `WidgetUpdater`, RemoteViews widgets, `TimerMath` (`phaseCarryMs` + `pausedFromStatus` state machine), and the Primary + Backup `"timer_alarm_v2"` alarm system (`NonCancellable` + synchronous pref snapshot + `MediaPlayer` raw / verified `Ringtone` fallbacks + `AlarmDiagnosticLog`) intact.


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
- **5 Android Home-Screen Widgets + Instant Refresh**: Jetpack Glance widgets (`Overview`, `Dark Progress`, `Today's Plan`, `Done Ring`, `Continue Studying`) updated immediately after DB writes, on app start/resume, and on date/time/timezone/boot events, backed by a 30-minute WorkManager fallback.
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
- **Notifications**: `AlarmManager` + `BroadcastReceiver` (`StudyReminderReceiver`, `BootCompletedReceiver`) + `NotificationCompat`

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
│   │   ├── dao/SyllabusDao.kt
│   │   └── entity/
│   │       ├── BookEntity.kt
│   │       └── ChapterEntity.kt
│   ├── preferences/
│   │   └── UserPreferencesRepository.kt
│   └── repository/
│       └── SyllabusRepository.kt
├── notifications/
│   ├── BootCompletedReceiver.kt
│   ├── NotificationHelper.kt
│   ├── StudyReminderReceiver.kt
│   └── StudyReminderScheduler.kt
├── widgets/
│   ├── ContinueStudyingWidget.kt
│   ├── DarkProgressWidget.kt
│   ├── DoneRingWidget.kt
│   ├── OverviewWidget.kt
│   ├── TodaysPlanWidget.kt
│   ├── WidgetBitmapHelper.kt
│   ├── WidgetRefreshWorker.kt
│   └── WidgetUpdater.kt
└── ui/
    ├── components/
    │   ├── AddChapterSheet.kt
    │   ├── BottomNavBar.kt
    │   └── Dialogs.kt
    ├── navigation/
    │   └── Routes.kt
    ├── screens/
    │   ├── BookDetailScreen.kt
    │   ├── BooksScreen.kt
    │   ├── HomeScreen.kt
    │   ├── MoreScreen.kt
    │   └── ProgressScreen.kt
    ├── theme/
    │   ├── Color.kt
    │   ├── Theme.kt
    │   └── Type.kt
    └── viewmodel/
        └── SyllabusViewModel.kt
```

### Room DB Schema
- **`books` table**: `id: Long` (PK, autoGenerate), `name: String`, `createdAt: Long`
- **`chapters` table**: `id: Long` (PK, autoGenerate), `bookId: Long` (FK -> `books.id`, `ON DELETE CASCADE`, indexed), `name: String`, `number: Int`, `isCompleted: Boolean`, `completedAt: Long?`

---

## 5. Screen-by-Screen Description

1. **Home (`HomeScreen`)**: Displays `"Syllabus Tracker"` title + subtitle, notification bell icon with pending-goal dot indicator, centered circular progress ring showing overall syllabus %, 3-column stats card (`Books`, `Chapters`, `Completed`), clickable `"Today's Plan"` card showing daily chapter goal progress, and `"Continue Studying"` card pointing to the last opened book and next incomplete chapter (or empty state CTA when no books exist).
2. **Books (`BooksScreen`) & Book Chapters (`BookDetailScreen`)**: Header with inline expandable search field and sort/add overflow menu; pill filter chips (`All`, `In Progress`, `Completed`); book cards with icon box, title, `x / y chapters`, percentage, progress bar, and chevron; bottom `"+ Add Book"` pill button. Tapping a book opens `BookDetailScreen`, showing book progress summary and ordered chapters with green completion check toggles, edit/delete actions (with confirmation), and empty state.
3. **Add Chapter Sheet (`AddChapterBottomSheet`)**: Modal bottom sheet opened via the center `+` bottom nav button or from `BookDetailScreen`. Features close button, book dropdown selector (plus quick `"New Book"` action if no books exist), validated `Chapter Name` input, validated numeric `# Chapter Number` input, `"Mark as Completed"` switch, and full-width `"Add Chapter"` pill button (also supports editing existing chapters).
4. **Progress (`ProgressScreen`)**: Segmented `Overall` / `By Book` pill switcher. `Overall` shows the percentage summary card with flat solid cumulative curve, `Completed / Remaining / Total` row, and `Study Trend` 7-day bar chart (`This Week` / `Last Week` dropdown). `By Book` lists per-book progress cards.
5. **More (`MoreScreen`)**: Settings screen with Daily Chapter Goal editor, Daily Study Reminder switch (handles Android 13+ `POST_NOTIFICATIONS` permission), Reminder Time picker (default 8:00 PM), **Backup** section (`Export Data` via SAF `CreateDocument`, `Import Data` via SAF `OpenDocument` with validation + `Replace` / `Merge` confirmation dialog), and About section.
6. **Bottom Navigation (`SyllabusBottomBar`)**: 5-slot white bar with hairline top border: `Home`, `Books`, circular indigo `+` button, `Progress`, and `More`.

---

## 6. Current Progress Checklist

### Done
- [x] Project Gradle configuration (`minSdk 26`, `applicationId`, Room, DataStore, Navigation Compose, Kotlin Serialization, Glance, WorkManager)
- [x] Custom adaptive launcher icon and monochrome notification small icon
- [x] Flat Material 3 design system (`Color.kt`, `Type.kt`, `Theme.kt`)
- [x] Room database (`BookEntity`, `ChapterEntity`, `SyllabusDao`, `SyllabusDatabase`)
- [x] DataStore preferences (`UserPreferencesRepository`) and reactive `SyllabusRepository`
- [x] Daily reminder notifications (`NotificationHelper`, `StudyReminderScheduler`, `StudyReminderReceiver`, `BootCompletedReceiver`)
- [x] `SyllabusViewModel` and `MainActivity` navigation host (including optimistic chapter toggle state and `ON_RESUME` widget refresh)
- [x] All screens & modals (`HomeScreen`, `BooksScreen`, `BookDetailScreen`, `AddChapterBottomSheet`, `ProgressScreen`, `MoreScreen`, `Dialogs`, `SyllabusBottomBar`)
- [x] 5 Jetpack Glance Home-Screen Widgets (`Overview`, `Dark Progress`, `Today's Plan`, `Done Ring`, `Continue Studying`) with lightweight aggregate queries, `LruCache` 300x300 ring bitmaps, pure-XML preview drawables, and deep-link navigation
- [x] Static periwinkle wave background (`res/drawable-nodpi/bg_home_waves.xml`) applied behind `HomeScreen` with transparent edge-to-edge status bar
- [x] Local Backup Export / Import (`BackupModels`, `BackupManager`, SAF pickers in `MoreScreen`, confirmation & error dialogs, atomic Room transaction import, widget & reminder resync)
- [x] Instant Widget Refresh architecture (`WidgetUpdater.refreshAll` concurrent execution, `applicationScope` Flow observer with `debounce(50).distinctUntilChanged()`, start/resume/boot/date/time/timezone triggers, 30-min WorkManager fallback)
- [x] `compile_applet` build verification and `gradle :app:testDebugUnitTest` Robolectric test suite (progress calculations, backup validation/replace/merge, widget summary queries, `LruCache` bitmap reuse, reminder scheduler)

### Pending
- None (all core features, widgets, backup export/import, instant widget updates, and verifications complete)

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
1. **Overview** (`4x2`, `widget_overview_info.xml`, `OverviewWidget` / `OverviewWidgetReceiver`): Header with book icon, `"Syllabus Tracker"`, and `+` button opening Add Chapter; left circular bitmap progress ring (`#4F46E5` arc, `#EEF0FF` track); vertical divider; right 3-row counts (`Books`, `Chapters`, `Completed`).
2. **Dark Progress** (`4x2`, `widget_dark_progress_info.xml`, `DarkProgressWidget` / `DarkProgressWidgetReceiver`): Solid `#1A1A2E` rounded card with white header, overall `%`, `"Syllabus Completed"`, indigo `#4F46E5` horizontal bar, and `#262842` `"Continue Studying"` tile opening the active book.
3. **Today's Plan** (`3x2`, `widget_todays_plan_info.xml`, `TodaysPlanWidget` / `TodaysPlanWidgetReceiver`): Calendar icon, `"Today's Plan"`, chevron, circle checkbox with `"Complete X chapters"` daily goal, and progress bar with `completedToday/dailyGoal`.
4. **Done Ring** (`2x2`, `widget_done_ring_info.xml`, `DoneRingWidget` / `DoneRingWidgetReceiver`): Solid `#EEF0FF` background with centered circular bitmap progress ring displaying overall `%` and `"Done"`.
5. **Continue Studying** (`4x1`, `widget_continue_studying_info.xml`, `ContinueStudyingWidget` / `ContinueStudyingWidgetReceiver`): Book tile, active book name, next incomplete chapter, progress bar + `%`, and circular play button opening that book's chapter list.

- **Auto-Refresh**: `WidgetUpdater.refreshAll()` runs `updateAll()` across all 5 Glance widgets concurrently on `Dispatchers.Default` immediately after DB writes and settings updates; `SyllabusApplication` also observes `combine(books, chapters, prefs).debounce(50).distinctUntilChanged()` to guarantee rapid widget sync. Refresh triggers cover app startup, `Lifecycle.Event.ON_RESUME`, `BOOT_COMPLETED`, `ACTION_DATE_CHANGED`, `ACTION_TIME_CHANGED`, and `ACTION_TIMEZONE_CHANGED`, with `WidgetRefreshWorker` (30-min `ExistingPeriodicWorkPolicy.KEEP`) as periodic backup.
- **Fast Rendering & Optimistic UI**: `SyllabusDao` provides aggregate `COUNT`/`SUM` queries (`getWidgetAggregateStats`, `getBookChapterCounts`) and `WidgetBitmapHelper` caches `300x300` ring bitmaps in an `LruCache(128)` keyed by percentage, dimensions, and colors. Chapter toggles update `SyllabusViewModel` optimistically right before the Room write and widget refresh.

### Home Screen Background
- `res/drawable-nodpi/bg_home_waves.xml` rendered with `Image(..., contentScale = ContentScale.Crop)` behind `HomeScreen` content and extending behind the transparent edge-to-edge status bar (`SystemBarStyle.light(TRANSPARENT, TRANSPARENT)`). Other screens keep `#F6F7FB`.

---

## 9. Backup JSON Schema & Instant Widget Update Specification

### Update Summary
1. **Data Export / Import (Local Backup)**: Added a `"Backup"` section inside `MoreScreen` with `"Export Data"` (`ActivityResultContracts.CreateDocument`, `application/json`, suggested filename `syllabus_tracker_backup_YYYY-MM-DD.json`, `Dispatchers.IO`, snackbar `"Backup saved"`) and `"Import Data"` (`ActivityResultContracts.OpenDocument`). Imported files are size-checked (`<= 20 MB`) and strictly validated (`schemaVersion == 1`, required keys, positive/valid IDs and ranges, no broken `bookId` references) before prompting a plain confirmation dialog (`Replace existing data`, `Merge with existing data`, `Cancel`), running in a single Room `withTransaction` block, restoring preferences/reminder schedule, refreshing all widgets, and showing snackbar `"Data imported"`.
2. **Instant Widget Updates (Milliseconds)**: Replaced full-table widget reads with lightweight aggregate DAO queries, added `LruCache` for 300x300 progress ring bitmaps, ran all 5 `GlanceAppWidget.updateAll` calls concurrently inside `applicationScope` (`Dispatchers.Default`) immediately on DB mutations and combined debounced Flow changes, added start/`ON_RESUME`/boot/`DATE_CHANGED`/`TIME_CHANGED`/`TIMEZONE_CHANGED` triggers, and applied optimistic in-app chapter toggling.

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

## 10. Resume Prompt

Read this file fully and continue building this exact app from the pending items without changing the design.

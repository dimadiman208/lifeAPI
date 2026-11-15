# Grow v3 — developer notes

Grow = **Projects** (what am I working on?) · **Growth** (what am I getting better at?) · **Learn** (library).
Plan is gone from the UI; its data (`task`, `task_done`, `check_item`, `wish`) stays in the DB untouched.
The global **Log** FAB is hidden: `SHOW_LOG_FAB = false` in `ui/Nav.kt` (flip to bring it back; Quick Log code is intact).
Screen-specific FABs (`FabOverride`, e.g. Train "Start") still work; list padding and snackbar offset follow `Fab.visible`.

DB version **8** (`AutoMigration(7, 8, spec = Migration7to8)` in `data/LifeDatabase.kt`).

## Core rules

| Concept | Where | Meaning |
|---|---|---|
| **Goal** | `DayPlan.goal` (one per project per day) | what I planned to achieve that day |
| **Expected result** | `DayPlan.expectedResult` | what "done" looks like |
| **Task** | `PlanTask` | something to do (day = null → backlog) |
| **Activity Log** | `ActivityEntry` | what I *actually* did (free text, optional minutes) |
| **Result** | `DayPlan.result` (non-blank = goal achieved), `TimeBlock.result`, `Achievement` | what came out of it |

* **Work hours are a plan, never logged time.** `Project.workStart/workEnd/workDays` only shape the week/timeline.
* **Spent time** = Σ `ActivityEntry.minutes` where `blockId == null` + Σ `TimeBlock.actualMinutes`.
  Planned block length is never counted. An activity linked to a block (`blockId`) only describes it — its minutes
  are ignored. Implemented in `data/grow3/GrowStats.loggedMinutes`. UI: when logging time for a block, write
  `TimeBlock.actualMinutes`; don't also put minutes on the linked activity.
* **Carry-over is explicit.** Unfinished past tasks are shown (`ProjectsDao.overdue`, `CarryOver.candidates`) with
  actions "Move to today / next workday / backlog / next week". `CarryOver.move` updates the same rows (no copies),
  sets `movedFrom`. Never move automatically.
* **No fake scores.** No productivity %, no skill levels. Stats are counts (`WeekStats`, `GrowthStats`) and
  suggestions come with a reason (`GrowthCalc.nextUp`).
* **Links, not copies.** Activity/learning/achievement/material reference `projectId`, `skillId`, `materialId`
  (nullable). Deleting a project/skill/material keeps those rows and nulls the link (cascades in the DAOs).
* **Weeks are Mon–Sun** (`Weeks.start/end/days`); Work-style projects emphasise Mon–Fri (`Weeks.workdays`).

## Entities (data/EntitiesGrow.kt, + Project columns in Entities2.kt)

* `Project` (+v8): `description`, `categoryId` (null = Personal), `status` Active|Paused|Completed|Archived,
  `icon` (emoji/letters), `workStart` "09:00", `workEnd` "18:00", `workDays` bitmask (31 = Mon–Fri).
  `archived` (legacy) is kept in sync — always save through `ProjectsDao.saveProject`.
  Old fields `nextAction/nextSince/targetDay/startDay/hue` still exist (hue = colour, use `C.hue(p.hue)`).
* `ProjectCategory(id, name, weekly, builtIn, sort)` — built-ins `WORK = 1` (weekly = true), `PERSONAL = 2`.
  A project is "work-style" when its category has `weekly = true`. Custom categories can be renamed/deleted;
  `ProjectsDao.deleteCategory` moves its projects to Personal (null) and refuses built-ins.
* `Milestone` (existing v2 table) = project stages; `PlanTask.milestoneId` optional.
* `DayPlan(projectId, day)` unique: `goal`, `expectedResult`, `result`, `note`.
* `PlanTask`: `projectId`, `day?`, `title`, `done`, `doneTs`, `sort`, `movedFrom?`, `createdTs`, `milestoneId?`.
* `ActivityEntry`: `projectId?`, `day`, `text`, `minutes?`, `ts`, `skillId?`, `materialId?`, `blockId?`.
* `TimeBlock`: `day`, `start`/`end` "HH:MM" (same day, end > start), `projectId?`, `title`, `description`,
  `result`, `actualMinutes?` (null = not logged). Overlaps are allowed but should be shown (`TimeMath.conflicts`).
* `Skill` (a growth direction): `name`, `goal`, `note`, `status` Active|Paused|Done.
* `SkillTopic`: `skillId`, `title`, `done`, `doneTs`, `note`, `kind` Topic|Practice.
* `LearningEntry` (journal): `skillId?`, `day`, `ts`, `learned`, `practiced`, `understood`, `unclear`,
  `minutes?`, `projectId?`, `materialId?`, `topicId?`. A quick thought = only `learned`.
* `Achievement`: `day`, `title`, `detail`, `projectId?`, `skillId?`, `materialId?`, `ts` — concrete results for
  weekly/monthly reports and 1-on-1s.
* `Material`: `title`, `kind` PDF|Article|Docs|GitHub|YouTube|Link, `url?`, `fileUri?` (content:// with a
  **persisted** SAF permission: `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`
  — never a raw file path), `fileName`, `description`, `tags` (comma, lowercase), `status`
  To Learn|In Progress|Completed|Archived, `favorite`, `skillId?`, `projectId?`, `addedTs`, `lastOpenedTs`,
  `progressPage`, `pageCount`, `completedTs?`. Future AI fields (summary etc.) can be added later as columns.
* `MaterialNote`: `materialId`, `ts`, `text`, `kind` Idea|Example|Learned|Try|Conclusion, `page?`.

### What happened to old Grow data (Migration7to8, one-time, no duplicates)

* `learning` → `learning_entry` (`legacyLogId`, `learned` = topic). From v8 **all learning time is read from
  `learning_entry`**: `LifeDao.learningByDay/learningDays` (Today's Day Score) and `LifeRepository.addLearning`
  (Quick Log "topic 15m") now use it. The old table is frozen.
* `journal` (Done journal) → `activity` (project matched by tag = project name).
* `focus` sessions → `activity` with minutes ("Focus: <task>").
* one-off old `task` rows with a project → `plan_task` (keeps day/done); project `nextAction` → backlog task.
* Old `goal` (30/90-day goals) and `habit`/`habit_check` stay as they are. **Habits are still used by Today
  (Day Score, habit tile) and widgets** — the Growth tab should keep a compact habits card
  (`ui/screens/grow/LegacyGrowthCards.kt` → `LegacyHabitsCard`, `LegacyHeatmap`, driven by `vm/GrowVM.kt`).
  Old goals: show only if any exist (read/edit), don't build new features on them — Skill.goal replaces them.
  `LegacyLearningCard` reads the frozen table — don't reuse it.

## DAOs (repo.db.projects() / growth() / learn())

`ProjectsDao` — categories (`categories`, `upsertCategory`, `deleteCategory`), projects (`allProjects`,
`project`, `projectNow`, `saveProject`, `deleteProjectCascade`), milestones (`milestonesOf`, `upsertMilestone`,
`deleteMilestone`), day plans (`dayPlans(projectId, from, to)`, `dayPlansRange`, `dayPlanNow`, `upsertDayPlan`),
tasks (`tasksForWeek` incl. backlog, `tasksOf`, `tasksRange`, `overdue`, `upsertTask`, `updateTasks`,
`insertTasks`, `deleteTask`), activity (`activityOf`, `activityRange`, `activityAllOf`, `upsertActivity`,
`deleteActivity`), time blocks (`blocksOn(day)` = Day Timeline across projects, `blocksRange`, `blocksOf`,
`upsertBlock`, `deleteBlock`), `achievementsOf(projectId)`.

`GrowthDao` — skills (`skills`, `skill`, `upsertSkill`, `deleteSkillCascade`), topics (`topicsOf`, `allTopics`,
`upsertTopic`, `deleteTopic`), journal (`recentLearning`, `learningRange`, `learningOf`, `learningForMaterial`,
`insertLearning`, `upsertLearning`, `deleteLearning`, `deleteLearningById`), achievements (`achievementsRange`,
`achievementsOf`, `upsertAchievement`, `deleteAchievement`).

`LearnDao` — `materials` (filter/search/sort in memory), `material`, `materialNow`, `materialsOfSkill`,
`materialsOfProject`, `findDuplicate(url, fileUri)` (dedupe on add), `upsertMaterial`, `saveProgress`,
`markOpened` (To Learn → In Progress), `deleteMaterialCascade`, notes (`notesOf`, `upsertNote`, `deleteNote`).

In a `BaseVM`: `repo.db.projects()` etc. (add `protected val projectsDao = repo.db.projects()` locally in your VM).
Use `act("toast") { …; undo }` for deletes so the snackbar offers Undo.

## Pure logic (data/grow3/, unit-tested in app/src/test/.../grow3/GrowV3LogicTest.kt)

* `Weeks` — `start/end/days/workdays/inMask/nextWorkday`, masks `WORKDAYS`, `ALL_DAYS`.
* `TimeMath` — `parse/format/duration/overlaps/conflicts/nextFreeSlot(blocks, workStart, workEnd, length)`.
* `CarryOver` — `candidates(tasks, today)`, `move(tasks, to)`.
* `GrowStats` — `week(from, to, plans, tasks, activity, blocks, achievements, dayOfTs)` → `WeekStats`,
  `delta(cur, prev)`, `loggedMinutes`, `minutesByProject`, `fmtMinutes`. Pass `Days::dayOf` as `dayOfTs`.
* `GrowthCalc` — `period(...)` → `GrowthStats`, `nextUp(...)` → review/learn-next suggestions with reasons.
* `MaterialKinds` — `detect(url, mime, fileName)`, `normalizeUrl`, `parseTags`, `tagList`, `titleFromUrl`,
  `STATUSES`, `NOTE_KINDS`, `ALL`.

Run tests: `./gradlew testDebugUnitTest`.

## Phase-2 file ownership

| Agent | Owns (create/modify freely) | Entry points already wired |
|---|---|---|
| **Projects** | `ui/screens/grow/projects/**`, `vm/grow3/projects/**`, may add queries to `data/ProjectsDao.kt` | `ProjectsTab(header)`, `projectsRoutes(nav)` (routes `grow/projects/…`) |
| **Growth** | `ui/screens/grow/growth/**`, `vm/grow3/growth/**`, may add queries to `data/GrowthDao.kt` | `GrowthTab(header)`, `growthRoutes(nav)` (routes `grow/growth/…`) |
| **Learn** | `ui/screens/grow/learn/**`, `vm/grow3/learn/**`, may add queries to `data/LearnDao.kt` | `LearnTab(header)`, `learnRoutes(nav)` (routes `grow/learn/…`) |

Shared, read-only for phase 2: `data/EntitiesGrow.kt` (schema is frozen at v8 — no new columns/tables without
the integrator), `data/LifeDatabase.kt`, `data/grow3/**`, `ui/screens/GrowScreen.kt`, `ui/screens/grow/GrowGraph.kt`,
`ui/Nav.kt`, theme/components, and the other agents' DAO files. Cross-tab links go by route — each agent must
register these detail routes so the others can link: `grow/projects/project/{id}`, `grow/growth/skill/{id}`,
`grow/learn/material/{id}`.

Tab contract: `@Composable fun XTab(header: LazyListScope.(subtitle: String) -> Unit)` — render your own
`ScreenList(glow = C.Grow) { header("…"); … }`; keep sheets/dialogs inside the tab composable.
`FabOverride(FabSpec(label, icon) { … })` is available if a tab wants a contextual add button (the Log FAB is off).

## Shared UI kit (reuse, don't fork)

* `ui/components/Components.kt`: `LifeCard`, `SectionHeader`, `MonoLabel`, `PillButton`, `GhostButton`,
  `RoundIconButton`, `Segmented`, `SmallChip`, `TagBadge`, `CheckCircle`, `EmptyCard`, `LifeField`, `Stepper`,
  `LinearBar`, `BarChart`, `Modifier.press` (tap/long-press + haptic), `Modifier.dashedBorder`.
* Sheets: `ui/components/Sheets.kt` `FormSheet` (simple), and the fixed-height scrolling sheet
  `ui/screens/body/BodyKit.kt` `EntrySheet` (pinned header + Save, max 90% height) — prefer `EntrySheet` for
  anything that can grow. `ChipPicker` for single-choice chips.
* `ui/screens/body/BodyCalendar.kt` `CalendarBar` (week Mon–Sun / month grid, themes) — use a Grow theme for
  the project week strip.
* `ui/screens/body/BodyKit.kt` `EntryRow` (tap = edit, swipe-left = delete).
* Tokens: `C.Grow` / `C.GrowTint` (Grow hue), `C.hue(i)`/`C.tint(i)` for project colours, text styles `T.*`.
* Icons: `ui/components/Icons.kt` `Ic.*` (Book, Check, Plus, Trash, Edit, ChevronRight, Sparkle, Play …).

## Tasks tab (added later)
Grow segments: 0 Projects · 1 Tasks · 2 Growth · 3 Learn (`Segments.grow`).
`ui/screens/grow/tasks/TasksTab.kt` + `vm/grow3/tasks/TasksVM.kt` show every `PlanTask` across projects
(`ProjectsDao.allTasks()`), filtered/grouped in memory by the pure `data/grow3/tasks/TaskViews.kt` (tested).
Views: Today · Upcoming (14 days) · Overdue (open, past days — never auto-moved) · Backlog · Done (30 days) · All.
Edit uses the shared `TaskMenuSheet` (optional project picker); bulk actions: project, date, done/reopen, delete — all with Undo.

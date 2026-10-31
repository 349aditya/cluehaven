# Cluehaven Android app

Early development build with Sudoku, Tile Merge (2048-style), Nonogram, Pipe Connect, and Block Garden.

## Current build

- Daily Haven steps 1–3 support Easy/Medium/Hard selection for each game. Start atomically locks the chosen difficulty, date seed, target, and initial board; Resume reopens progress and Retry restores that same starting board. Completing any one difficulty awards that game's growth once per date, permanently through undo/retry. The all-five bonus is awarded in the fifth completion's transaction. Only Daily Five play counts; regular saves and the existing Sudoku streak stay separate. `DailyFiveConfig.kt` holds Tile Merge score targets 500/1,000/2,000, Block Garden targets 1,000/3,000/5,000, and `requiredGamesForStreak = 5` (snapshotted per date for the later streak feature). Sudoku uses 6×6 Easy / 9×9 Medium and Hard; Nonogram uses the existing authored difficulty pools; Pipe Connect uses 4×4/5×5/6×6. See [migration review](docs/daily-five-migration.md) for the additive Room v4→v5 upgrade and preservation of legacy daily goals.
- Daily Haven step 4 adds a Daily Five Home card above Continue playing, five game icons with saved done/pending status, a segmented progress bar, and a Continue Daily Five action. Bottom navigation is Home / Haven / Progress / Settings. The Haven tab rebuilds a garden from today's saved completions: Sudoku tree, Nonogram flowers, Pipe Connect water channel, Tile Merge lanterns, and Block Garden bed. Pending items have dashed outlines. The scene is read-only; reopening never awards growth or bonuses. Reduced Motion switches growth changes immediately; other changes crossfade gently. See [step-4 review](docs/daily-haven-step4.md).
- Step 5 replaces the Haven streak placeholder with a count, current-week rest availability, and Monday–Sunday completed/forgiven/pending/missed dots; Home shows a compact count. The streak is a pure recomputation from saved daily counts and each date's saved requirement. One missed date per week preserves but does not increment the count; later misses reset it without restoring that week's rest day. Today stays pending until midnight. First use is the earliest saved Daily Five day; earlier dates are neutral. The existing date observer handles midnight, resume, and timezone/clock changes, and multi-day gaps are evaluated date by date. No Room migration is needed. Sudoku's streak is unchanged. See [step-5 review](docs/haven-streak.md).
- The debug shortcut `adb shell am start -W -n com.cluehaven.puzzlegames/.MainActivity --ez daily_five_review true` still opens the Daily Five flow from a fresh activity. This extra is ignored in release builds; normal entry points are the Home card and Haven's daily game row.
- The Nonogram catalogue is unchanged: Easy 20, Medium 16, Hard 4. Repeats are accepted during development; the [separate expansion task](docs/follow-ups.md) covers 50 new pictures and solver-based unique-solution tests.
- For isolated QA, build with `-PisolatedDailyReview=true`. Debug app/test IDs become `com.cluehaven.puzzlegames.dailyreview` and `com.cluehaven.puzzlegames.dailyreview.test`; its launcher name is Cluehaven Daily Review. Open the review flow with `adb shell am start -W -n com.cluehaven.puzzlegames.dailyreview/com.cluehaven.puzzlegames.MainActivity --ez daily_five_review true` from a fresh activity. Omit the property for the normal application ID. The v4→v5 migration was approved and applied to the main app during step 4 after the steps 1–3 commits were created; saved boards/history/daily progress were compared before and after.
- Progress distinguishes active play from earned completions. A 9,140-point Block Garden run with legal placements is still in progress, and an unfinished Sudoku does not increment puzzles solved; both now show an explicit active-board/run status alongside their totals.

- Block Garden: an endless 8×8 block-placement puzzle with 31 fixed-orientation shapes, three-piece trays, drag/drop landing previews, tap-to-place controls, simultaneous row/column clears, consecutive-clear combo bonuses, and one undo per run (including game over and tray refills). Fresh trays have at least one legal placement sequence; larger shapes enter after the first nine placements. New runs retain the best score. The board, remaining tray, random state, and undo snapshot save automatically. A dedicated Home card, Continue support, active-play timer, score statistics, and dated finished-run history are included. Fastest-time records remain for finite puzzles.

- Pipe Connect now follows the approved board design: broad curved pipes that meet across softly rounded tiles, teal connected paths, muted disconnected paths, a gold source, coral endpoints, a continuous progress bar, and actions grouped below the board. Its timer sits beside the connection count. Both board sizes support light/dark appearance and reduced motion; generation, rotation rules, hints, undo, and saves are unchanged.

- All five games have persistent active-play timers. Time pauses when leaving the board, backgrounding the app, or opening a new-run/ad flow; normal exits save immediately and active play checkpoints every five seconds. Tile Merge and Block Garden time the full run until game over, and undo reopens that same run. Progress shows fastest unassisted puzzle times by difficulty/size, alongside the score games' best scores. Settings → Completion history opens dated, filterable results. Assisted and older partially timed saves stay in history but do not qualify for fastest times. Database v3 preserves existing boards and imports surviving older completions without inventing elapsed times; replaced old boards cannot be recovered.

- Tile Merge samples scrambled random output for tile positions and the 90% two / 10% four distribution, including saved runs. Undo keeps deterministic replay. Sudoku refreshes the daily selection at local midnight, on resume, and after clock/timezone changes; an already open board stays intact and shows its original daily date.

- Reference-matched visual pass: centered game titles, compact typography and home grid, house-and-leaf brand mark, warm card surfaces without default purple tints, rectangular action controls, bottom toolbars, and a three-column Nonogram chooser. Reviewed screenshots from the connected phone are in `output/ui-review/`.
- Settings includes working sound effects (Android system tap feedback), haptics, and reduced motion. Saved appearance and puzzle state are preserved when installing an update.

- Refreshed UI: warm ivory and teal themes, shared game headers, compact home cards, and a Continue card that opens the latest unfinished saved board. Appearance supports System, Light, and Dark with legacy preferences preserved; reduced motion disables connection and tile color transitions.
- Sudoku highlights the selected box and matching digits, with a larger two-row number pad for 9×9 boards. Nonogram has size filters, saved-board previews, active row/column highlighting, and clue-match colors. Pipe Connect shows a gold source, coral endpoints, and connection progress. Progress displays real statistics for all five games, including a persistent count of completed Pipe Connect networks.

- Pipe Connect: generated 4×4 and 6×6 networks, clockwise tile rotation, source-connected highlighting, undo, one free hint, and independent save/resume for each size. Connect every tile with no leaking ends; new puzzles replace the current board only after confirmation.

- Home screen with game navigation, a saved theme setting, and Progress statistics for Sudoku and Tile Merge.
- Playable offline Sudoku: 6×6 Easy, 9×9 Medium and Hard, a daily 9×9 puzzle, notes, erase, undo, one free hint, and local save/resume.
- Playable Tile Merge: swipe to move tiles, score and best score, automatic local save/resume, and a game-over dialog with a new game or undo. The first undo is free; the next two can use a rewarded test ad in debug builds.
- Progress records each solved Sudoku once and counts runs that reach 2048. The Sudoku-only streak display is hidden for now; its saved results and calculation remain intact. Existing saved puzzles and runs are preserved when the database upgrades.
- Previously saved daily Sudoku completions are backfilled. Older free-play boards and Tile Merge runs that were replaced before this build were never stored.
- Mini Crosswords and Word Guess are deferred and are hidden from the app for now.
- Nonogram: 40 puzzles (20 each in 5×5 and 10×10 sizes), with Easy/Medium/Hard labels, fill/cross controls, undo, one free hint per puzzle, and automatic local save/resume for each board. The daily puzzle rotates through the expanded pack from 31 October 2026; earlier dates retain the original rotation. Progress tracks pack and daily completions. The original eight board IDs and layouts are unchanged, and saved daily sessions retain their source puzzle.
- Release builds do not request ads yet. Production AdMob IDs, consent handling, hint credits, between-puzzle ads, content packs, and final icon are not yet integrated.

## Run on the connected phone

Open this folder in Android Studio, choose the connected phone in the device selector, then press **Run** for the `app` configuration. On the home screen, tap **Sudoku**, **Tile Merge**, **Nonogram**, **Pipe Connect**, or **Block Garden**.

## Build on Windows

Open the `Cluehaven` folder in Android Studio and let Gradle sync. The ignored
`local.properties` file must point to this machine's Android SDK; on this machine:

```properties
sdk.dir=C\:/Users/aditya/AppData/Local/Android/Sdk
```

The project requests Java 25 for the Gradle daemon. Android Studio includes Java 25,
and the Gradle wrapper can also download the required toolchain. Keep the existing
Android SDK 37 platform installed through Android Studio's SDK Manager.

From PowerShell in this folder, run:

```powershell
.\build.ps1
.\build.ps1 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The PowerShell script uses the ignored `.gradle-user` folder in this checkout for
Gradle caches and downloaded toolchains. The first build needs internet access for
any missing dependencies. The standard Windows wrapper is `gradlew.bat`.

## Verification so far

- Daily Haven steps 1–5: 90 unit tests and 24 device tests passed, including all difficulties, concurrent difficulty locking, persistent resume/retry, legacy migrations, atomic completion/bonus rollback, read-only garden/streak reconstruction, and Home/Haven controls in both themes with Reduced Motion. Streak tests cover weekly boundaries, gaps, first use, midnight, timezone changes, and saved requirement changes. Lint has zero errors and 18 existing warnings. See the step-5 review for main-app saved-data recovery and verification details.
- `:app:assembleDebug` passed.
- `:app:testDebugUnitTest` passed, including Sudoku uniqueness, difficulty, hint and undo tests, Tile Merge movement and undo tests, and Progress streak tests.

The current debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

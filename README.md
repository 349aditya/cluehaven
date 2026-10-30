# Cluehaven Android app

Early development build with Sudoku, Tile Merge (2048-style), Nonogram, and Pipe Connect.

## Current build

- Pipe Connect now follows the approved board design: broad curved pipes that meet across softly rounded tiles, teal connected paths, muted disconnected paths, a gold source, coral endpoints, a continuous progress bar, and actions grouped below the board. Its timer sits beside the connection count. Both board sizes support light/dark appearance and reduced motion; generation, rotation rules, hints, undo, and saves are unchanged.

- All four games have persistent active-play timers. Time pauses when leaving the board, backgrounding the app, or opening a new-run/ad flow; normal exits save immediately and active play checkpoints every five seconds. Tile Merge times the full run until game over, and undo reopens that same run. Progress includes dated, filterable completion history and fastest unassisted puzzle times by difficulty/size, alongside Tile Merge's best score. Assisted and older partially timed saves stay in history but do not qualify for fastest times. Database v3 preserves existing boards and imports surviving older completions without inventing elapsed times; replaced old boards cannot be recovered.

- Tile Merge samples scrambled random output for tile positions and the 90% two / 10% four distribution, including saved runs. Undo keeps deterministic replay. Sudoku refreshes the daily selection at local midnight, on resume, and after clock/timezone changes; an already open board stays intact and shows its original daily date.

- Reference-matched visual pass: centered game titles, compact typography and home grid, house-and-leaf brand mark, warm card surfaces without default purple tints, rectangular action controls, bottom toolbars, and a three-column Nonogram chooser. Reviewed screenshots from the connected phone are in `output/ui-review/`.
- Settings includes working sound effects (Android system tap feedback), haptics, and reduced motion. Saved appearance and puzzle state are preserved when installing an update.

- Refreshed UI: warm ivory and teal themes, shared game headers, compact home cards, and a Continue card that opens the latest unfinished saved board. Appearance supports System, Light, and Dark with legacy preferences preserved; reduced motion disables connection and tile color transitions.
- Sudoku highlights the selected box and matching digits, with a larger two-row number pad for 9×9 boards. Nonogram has size filters, saved-board previews, active row/column highlighting, and clue-match colors. Pipe Connect shows a gold source, coral endpoints, and connection progress. Progress displays real statistics for all four games, including a persistent count of completed Pipe Connect networks.

- Pipe Connect: generated 4×4 and 6×6 networks, clockwise tile rotation, source-connected highlighting, undo, one free hint, and independent save/resume for each size. Connect every tile with no leaking ends; new puzzles replace the current board only after confirmation.

- Home screen with game navigation, a saved theme setting, and Progress statistics for Sudoku and Tile Merge.
- Playable offline Sudoku: 6×6 Easy, 9×9 Medium and Hard, a daily 9×9 puzzle, notes, erase, undo, one free hint, and local save/resume.
- Playable Tile Merge: swipe to move tiles, score and best score, automatic local save/resume, and a game-over dialog with a new game or undo. The first undo is free; the next two can use a rewarded test ad in debug builds.
- Progress records each solved Sudoku once, tracks daily Sudoku streaks, and counts runs that reach 2048. Existing saved puzzles and runs are preserved when the database upgrades.
- Previously saved daily Sudoku completions are backfilled. Older free-play boards and Tile Merge runs that were replaced before this build were never stored.
- Mini Crosswords and Word Guess are deferred and are hidden from the app for now.
- Nonogram: 40 puzzles (20 each in 5×5 and 10×10 sizes), with Easy/Medium/Hard labels, fill/cross controls, undo, one free hint per puzzle, and automatic local save/resume for each board. The daily puzzle rotates through the expanded pack from 31 October 2026; earlier dates retain the original rotation. Progress tracks pack and daily completions. The original eight board IDs and layouts are unchanged, and saved daily sessions retain their source puzzle.
- Release builds do not request ads yet. Production AdMob IDs, consent handling, hint credits, between-puzzle ads, content packs, and final icon are not yet integrated.

## Run on the connected phone

Open this folder in Android Studio, choose the connected phone in the device selector, then press **Run** for the `app` configuration. On the home screen, tap **Sudoku**, **Tile Merge**, **Nonogram**, or **Pipe Connect**.

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

- `:app:assembleDebug` passed.
- `:app:testDebugUnitTest` passed, including Sudoku uniqueness, difficulty, hint and undo tests, Tile Merge movement and undo tests, and Progress streak tests.

The current debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

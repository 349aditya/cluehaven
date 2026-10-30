# Cluehaven Android app

Early development build with Sudoku, Tile Merge (2048-style), and Nonogram.

## Current build

- Nonogram: eight uniquely solvable 5×5 and 10×10 starter boards, a daily rotation, fill/cross controls, undo, one hint, saved boards, and completion statistics.

- Home screen with game navigation, a saved theme setting, and Progress statistics for Sudoku and Tile Merge.
- Playable offline Sudoku: 6×6 Easy, 9×9 Medium and Hard, a daily 9×9 puzzle, notes, erase, undo, one free hint, and local save/resume.
- Playable Tile Merge: swipe to move tiles, score and best score, automatic local save/resume, and a game-over dialog with a new game or undo. The first undo is free; the next two can use a rewarded test ad in debug builds.
- Progress records each solved Sudoku once, tracks daily Sudoku streaks, and counts runs that reach 2048. Existing saved puzzles and runs are preserved when the database upgrades.
- Previously saved daily Sudoku completions are backfilled. Older free-play boards and Tile Merge runs that were replaced before this build were never stored.
- Mini Crosswords and Word Guess are deferred and are hidden from the app for now.
- Release builds do not request ads yet. Production AdMob IDs, consent handling, hint credits, between-puzzle ads, content packs, and final icon are not yet integrated.

## Run on the connected phone

Open this folder in Android Studio, choose the connected phone in the device selector, then press **Run** for the `app` configuration. On the home screen, tap **Sudoku** or **Tile Merge**.

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

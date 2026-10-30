# Cluehaven Android app

Early development build focused on Sudoku and Tile Merge (2048-style).

## Current build

- Home screen with game navigation, a saved theme setting, and Progress statistics for Sudoku and Tile Merge.
- Playable offline Sudoku: 6×6 Easy, 9×9 Medium and Hard, a daily 9×9 puzzle, notes, erase, undo, one free hint, and local save/resume.
- Playable Tile Merge: swipe to move tiles, score and best score, one free undo, new-run confirmation, and automatic local save/resume.
- Progress records each solved Sudoku once, tracks daily Sudoku streaks, and counts runs that reach 2048. Existing saved puzzles and runs are preserved when the database upgrades.
- Previously saved daily Sudoku completions are backfilled. Older free-play boards and Tile Merge runs that were replaced before this build were never stored.
- Mini Crosswords and Word Guess are deferred and are hidden from the app for now.
- Ads, hint credits, content packs, and final icon are not yet integrated.

## Run on the connected phone

Open this folder in Android Studio, choose the connected phone in the device selector, then press **Run** for the `app` configuration. On the home screen, tap **Sudoku** or **Tile Merge**.

## Verification so far

- `:app:assembleDebug` passed.
- `:app:testDebugUnitTest` passed, including Sudoku uniqueness, difficulty, hint and undo tests, Tile Merge movement and undo tests, and Progress streak tests.

The current debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

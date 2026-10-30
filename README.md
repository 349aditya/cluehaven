# Cluehaven Android app

Early development build focused on Sudoku and Tile Merge (2048-style).

## Current build

- Home screen with game navigation, Progress shell, and a saved theme setting.
- Playable offline Sudoku: 6×6 Easy, 9×9 Medium and Hard, a daily 9×9 puzzle, notes, erase, undo, one free hint, and local save/resume.
- Playable Tile Merge: swipe to move tiles, score and best score, one free undo, new-run confirmation, and automatic local save/resume.
- Mini Crosswords and Word Guess are deferred and are hidden from the app for now.
- Ads, hint credits, content packs, and final icon are not yet integrated.

## Run on the connected phone

Open this folder in Android Studio, choose the connected phone in the device selector, then press **Run** for the `app` configuration. On the home screen, tap **Sudoku** or **Tile Merge**.

## Verification so far

- `:app:assembleDebug` passed.
- `:app:testDebugUnitTest` passed, including Sudoku uniqueness, difficulty, hint and undo tests, plus Tile Merge movement and undo tests.

The current debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

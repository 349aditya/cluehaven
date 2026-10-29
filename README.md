# Cluehaven Android app

Early development build for the four-game puzzle collection.

## Current build

- Home screen with game navigation, Progress shell, and a saved theme setting.
- Playable offline Sudoku: daily and free-play boards, three difficulty levels, notes, erase, undo, one free hint, and local save/resume.
- Playable Tile Merge: swipe to move tiles, score and best score, one free undo, new-run confirmation, and automatic local save/resume.
- Mini Crosswords and Word Guess are listed but their gameplay is still being developed.
- Ads, hint credits, content packs, and final icon are not yet integrated.

## Run on the connected phone

Open this folder in Android Studio, choose the connected phone in the device selector, then press **Run** for the `app` configuration. On the home screen, tap **Sudoku** or **Tile Merge**.

## Verification so far

- `:app:assembleDebug` passed.
- `:app:testDebugUnitTest` passed, including Sudoku uniqueness, difficulty, hint and undo tests, plus Tile Merge movement and undo tests.

The current debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

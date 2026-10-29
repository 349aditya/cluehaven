# Cluehaven Android app

Early development build for the four-game puzzle collection.

## Current build

- Home screen with game navigation, Progress, and Settings shells.
- Playable Tile Merge: swipe to move tiles, score and best score, one free undo, new-run confirmation, and automatic local save/resume.
- Sudoku, Mini Crosswords, and Word Guess are listed but their gameplay is still being developed.
- Ads, hint credits, content packs, and final icon are not yet integrated.

## Run on the connected phone

Open this folder in Android Studio, choose the connected phone in the device selector, then press **Run** for the `app` configuration. On the home screen, tap **Tile Merge** and swipe the board.

## Verification so far

- `:app:assembleDebug` passed.
- `:app:testDebugUnitTest` passed, including Tile Merge movement and undo tests.

The current debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

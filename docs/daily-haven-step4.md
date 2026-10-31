# Daily Haven step 4 — approved

Home now puts Daily Five above the compact Continue playing card. The five game
icons show saved done/pending states, the segmented bar and count show today's
progress, and Continue Daily Five opens the daily-only hub. Each icon can also open
its own saved daily challenge directly.

The four bottom tabs are Home, Haven, Progress, Settings. The Haven tab shows the
garden, today's five daily game buttons, and a clearly labelled Coming soon streak
card. Game back navigation returns through the daily hub to the tab that opened it.

| Daily completion | Earned scene item |
| --- | --- |
| Sudoku | Tree |
| Nonogram | Flowers |
| Pipe Connect | Water channel |
| Tile Merge | Lanterns |
| Block Garden | Garden bed |

`HavenProgress` projects only saved `completedAt` markers for the current local date.
Unknown games, unfinished entries, duplicate entries, and other dates do not add
growth. Home/Haven only observe Room: they never open a day, start a challenge, or
call the completion/bonus writer. Reopening rebuilds the same scene. Local date
rollover uses the existing midnight/resume/clock-change handling.

Pending items use dashed outlines. The artwork is native Compose Canvas, using the
existing ivory/teal/mint/gold/coral theme. Growth changes crossfade; Reduced Motion
renders them immediately. The initial saved scene appears directly after loading.
Screen-reader descriptions name the earned and pending items, and every daily game
button includes its completion state.

## Known progress reports

The earlier 9,140-point Block Garden save was a still-playable run, not a finished
run. The earlier Sudoku save had 17 empty cells and no recorded solve. Neither zero
total demonstrated a counting failure. The display fix adds explicit active-run/
board status and keeps best score separate from finished runs. The current main
app shows one finished Block Garden run and another in progress; Sudoku still shows
17 empty cells. No completions have been invented or backfilled from a high score.

## Ordered commits made before the approved main-app upgrade

1. `1295efd` — clarify unfinished Sudoku and Block Garden progress.
2. `bfdeb48` — shared save codecs and 5×5 Pipe Connect support.
3. `8e2a569` — seeded Daily Five contracts, difficulties, and config.
4. `bc2026f` — isolated daily Room storage and additive migrations.
5. `2874e6b` — all five game hookups, history isolation, and review tooling.

Step 4 was approved by the user and committed before starting step 5.

## Validation

- 72 unit tests pass; lint has no errors (18 existing warnings, 3 hints).
- 20 device tests pass, including both themes, reduced motion, zero/three/five grown
  items, Home actions, and repeated projection without any growth/bonus writes.
- The normal APK builds and the approved main database migration has been applied.
  All existing values in its 8 board saves, 17 history rows, 1 daily day, and 5 daily
  entries were preserved. Completion and milestone counts were unchanged.
- Review captures and isolated UI fixture screenshots are in `output/haven-step4/`.
  Fixture screenshots do not insert fake completions into the main app.
- Main-app navigation checks passed: a Haven game button opens its daily board,
  Back returns through the hub to Haven, and the Home CTA returns to Home after
  leaving the hub. The main app is left on Haven for review, with zero daily
  completions awarded by these checks.

Step 5 was deferred during the approved step-4 work. Its subsequent implementation
is documented in [the streak review](haven-streak.md). The Nonogram expansion remains
a separate follow-up task.

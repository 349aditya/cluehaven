# Daily Five steps 1–3: migration review

Status: prepared for review. Do not install this build over the development app until
the migration has been reviewed. Isolated database tests may exercise the migration;
they do not modify the installed app's database.

## Version 4 → 5

```sql
ALTER TABLE daily_haven_days
  ADD COLUMN requiredGamesForStreak INTEGER NOT NULL DEFAULT 5;
ALTER TABLE daily_haven_entries
  ADD COLUMN difficulty TEXT NOT NULL DEFAULT 'MEDIUM';
ALTER TABLE daily_haven_entries
  ADD COLUMN objectiveKind TEXT NOT NULL DEFAULT 'SOLVE';
UPDATE daily_haven_entries
SET difficulty = CASE WHEN gameId = 'nonogram' THEN 'EASY' ELSE 'MEDIUM' END,
    objectiveKind = CASE WHEN gameId = 'tile_merge' THEN 'TILE_VALUE'
                         WHEN gameId = 'blocks' THEN 'LINES' ELSE 'SOLVE' END;
```

- All existing rows, board payloads, seeds, targets, timestamps, completion markers,
  bonuses, regular saves, and history are retained. No table is dropped or rebuilt.
- Version 4 created all five entries when opening a date, without recording when a
  game was started. Treat these existing entries as locked: this avoids guessing
  which puzzles have already been seen. Their old 256-tile / 10-line goals remain
  playable; they are not silently reinterpreted as score thresholds.
- New dates snapshot `requiredGamesForStreak` from config. There is no weekly streak
  calculation in this phase. The future calculation must use the saved requirement,
  forgive one missed date per Monday–Sunday week, preserve rather than increment the
  streak on a forgiven date, and break it on the second miss without replenishing
  the week's forgiveness. Today remains pending until local midnight.
- New game entries are inserted atomically only when Start is pressed. Their unique
  `(date, gameId)` key locks difficulty, seed, target, and initial board. Concurrent
  Start requests return the first saved challenge. Resume/retry never regenerate it.
- The all-five bonus is separate from streak qualification and still requires all
  five completions, recorded in the same transaction as the fifth completion.
- Version 3 installations upgrade through the existing additive 3 → 4 migration,
  then this 4 → 5 migration. Destructive migration is not enabled.

## Verification required

- Exercise 2 → 3 → 4 → 5 and 3 → 4 → 5 upgrade chains and Room schema validation.
- Exercise 4 → 5 with populated daily rows, legacy goals, completion/bonus markers,
  regular boards, and history; compare values before and after.
- Test concurrent difficulty selection, restart/resume from a file-backed database,
  immutable saved targets, score thresholds, duplicate callbacks, permanent growth,
  and transactional rollback of the fifth completion if awarding the bonus fails.

The Home card, Haven scene, and weekly streak calculation remain deferred for review.

## Verification results — 31 October 2026

- Debug APK, unit tests, Android test APK, and lint build passed with
  `-PisolatedDailyReview=true --offline --no-configuration-cache`.
- 68 unit tests passed. Lint: zero errors; 18 existing warnings and 3 hints.
- 16 device tests passed in the separate `com.cluehaven.puzzlegames.dailyreview`
  application. This includes the populated 4 → 5 migration, 3 → 4 → 5 and
  2 → 3 → 4 → 5 chains, file-backed close/reopen, concurrent starts and duplicate
  completions, threshold changes, legacy goal preservation, and forced bonus-write
  failure rolling back the fifth completion.
- The installed `com.cluehaven.puzzlegames` app has not been updated by this pass.
- Phone screen checks covered Sudoku Easy, Nonogram Hard, Pipe Connect Medium,
  Tile Merge Hard (2,000 points), and Block Garden Medium (3,000 points). After a
  pipe rotation, force-stop/resume restored the exact moved board and the locked
  Medium difficulty; Retry restored the exact initial board. Captures are in the
  ignored `output/daily-five-v5-review/` directory. The separate review app is left
  on its Daily Five hub with no daily completions awarded during these UI checks.

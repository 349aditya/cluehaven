# Haven streak — step 5 review

Step 4 was committed first as `e1c268d` (`feat: add Daily Five Home card and
saved-growth Haven tab`). Step 5 was reviewed before the user requested ordered
commits of the streak implementation and subsequent Progress/Settings cleanup.

## Rules and calculation

`calculateHavenStreak(savedResults, today)` is a pure function. It reads no clock,
config, database, or preferences and has no writes or reward callbacks. Each result
contains its date, distinct completed-game count, and the requirement saved for that
date. The current config is never substituted for an older date's requirement.

The earliest saved Daily Five day is first use, including an opened day with zero
completions. Dates before it stay neutral and do not consume forgiveness. If the
player has never opened Daily Five, there is no active streak or missed history.

The calculation walks every date from first use through local today, including
dates on which the app was not opened:

- A qualifying date increments the streak once, including today as soon as it qualifies.
- An unfinished today remains pending until local midnight.
- The first missed past date in a Monday–Sunday week is forgiven and preserves the
  count without incrementing it.
- Every later missed date in that week resets the count to zero. A reset does not
  replenish the week's forgiveness. A later qualifying date can start a new streak.
- Monday renews that week's single rest day. It does not reset a surviving streak.
- Future results are ignored until their date is reached. Changing timezone/clock
  changes the supplied current date; saved date labels and requirements stay fixed.

For example, with a saved requirement of five:

| Date | Completed games | Week status | Streak after date |
| --- | ---: | --- | ---: |
| Monday | 5 | Completed | 1 |
| Tuesday | 0 | Forgiven | 1 |
| Wednesday | 5 | Completed | 2 |
| Thursday | 0 | Missed | 0 |
| Friday | 5 | Completed | 1 |
| Saturday (today) | 2 | Pending | 1 |
| Sunday (future) | — | Pending | — |

In this example the rest day remains used after Thursday's break. The same daily
results and current date always reproduce the same count and week statuses; there
is no persisted processing cursor, rest-day charge, or streak counter to duplicate.

## Storage and display

No Room schema/version change is needed (database version remains 5). The existing
daily-day rows already preserve each date's streak requirement. A lightweight SQL
summary counts known completed game IDs once per date. Home/Haven read that summary
and today's garden entries together in one Room read transaction. Observing them
does not create dates, complete games, award bonuses, or change stored streak rules.

The existing `rememberCurrentDate` observer refreshes the projection at midnight,
on resume, and after clock/timezone changes. Reopening after a multi-day gap evaluates
every intervening date in chronological order without adding missed-day rows.

Haven shows the count, current-week rest availability, and a Monday–Sunday dot row:
check mark = completed, dash = forgiven rest, dot = pending, cross = missed. Today
has a stronger outline. Dates before first use and future dates are pending/neutral;
screen-reader descriptions distinguish them from an unfinished today. Home's Daily
Five card shows a compact Haven streak count. The Sudoku-only streak is unchanged.

## Scope

The all-five bonus still requires all five games, independently of a lowered streak
requirement. Garden growth is still driven only by saved daily completions.
Share cards, notifications, and the Nonogram catalogue expansion remain outside this
step.

## Verification

- 90 unit tests passed, including 15 pure streak tests and 3 integrations with the
  existing date flow for midnight, a multi-day resume, and timezone changes.
- 24 device tests passed. These include reading an empty first day, preserving
  saved 5/3 requirements, excluding unknown games, consistent live garden/streak
  snapshots during completions, and the four week-dot states on screen. The
  existing Sudoku-only streak isolation and completion/bonus tests still pass.
- Both the isolated review APK and normal main APK build. Lint has zero errors,
  18 existing warnings, and 3 hints. Light/dark and Reduced Motion fixtures were
  inspected. Images are in the ignored `output/haven-step5/haven-review/` folder.
- The main app is installed with the real streak display and left on Haven.
  Viewing Home/Haven after the saved-data recovery below left all six game/progress
  tables unchanged; database version stayed 5.

## Saved-data recovery during device QA

The initial main-app backup command returned an unknown-package error in its output
while `adb exec-out` reported a successful process exit. The installation therefore
continued before that output was recognized as an invalid backup. The resulting
phone database had 8 boards, 11 history records, and no daily entries. The cause of
the missing package could not be established from that backup attempt.

The user asked to keep whichever copy worked better. A read-only comparison showed
the verified step-4 backup had the same eight board IDs: seven identical boards and
one newer board. Its history contained every current record, a later finished
Block Garden run (11,120 points rather than the earlier ongoing 9,140-point state),
six additional history records, and the five Daily Five entries. No newer current
board or history progress was found.

The more complete step-4 database was restored after preserving the current copy,
checking SQLite integrity, and validating the transferred/restored file hashes.
The resulting database contains 8 saved boards, 17 history records, 1 daily day,
and 5 daily entries. A subsequent Home/Haven read preserved every original column
value. Both database copies are retained locally in the ignored review output.
No fake completions or streak days were inserted.

The one-off recovery helper now checks package presence and the SQLite file header
before any replacement, compares fresh current progress against the selected backup,
and verifies the temporary transfer hash before activating it. These QA helpers
are not part of the shipped app or a new backup feature.

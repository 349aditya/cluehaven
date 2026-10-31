# Endless Nonogram

## Player flow

Nonogram → Endless → 5×5 or 10×10 → Easy, Medium or Hard → Play.
An unfinished picture changes Play to Resume for that size/difficulty. The usual
fill/cross, hint, undo, timer and completion rules are reused. Completion offers
Next puzzle. One different picture is prepared in the background while the current
board is open; prefetch does not save anything or create history. The new initial
snapshot is durably saved before it is displayed.

The existing Picture Pack and daily/Daily Five selection continue to use their
authored puzzles. Endless has its own Progress card, chooser totals, history
filter and personal-best categories. Home Resume identifies its size/difficulty.

## Version 1 generation contract

The request identity is seed + board size + requested difficulty + generator
version. Version 1 specifies its own SplitMix64 PRNG, motif grammar, quality
filters, line solver and rating thresholds. Golden tests pin accepted pictures
for all six size/difficulty combinations. A change to this contract requires a
new generator version and retention of the old version when regeneration is needed.

Candidates use canopy/trunk/pot, flower/stem/leaves, crystal/ribbon, butterfly,
and geometric-crest templates. Seeded bands, paired stroke edits, flips and
transposition vary the motifs. No candidate starts as a pure random cell grid.
Pictures keep at least one reflection axis; some variations are abstract symbols.

The quality gate requires 24–76% filled cells, at least three distinct rows and
columns, limited empty rows/columns and isolated pixels, bounded perimeter, and
at least 75% of filled cells in one connected component. This rejects empty,
full, flat stripe/rectangle, checkerboard and disconnected/noisy candidates.

## Acceptance and rating

`NonogramLineSolver` receives row/column clues only, never the hidden solution.
It enumerates legal arrangements within each individual line and repeatedly
intersects those arrangements with known filled/empty cells. Only cells shared
by every compatible arrangement are deduced. It never branches on a board cell,
guesses, or uses a picture's symmetry to solve it.

Only a completely deduced grid is accepted, and the generator additionally checks
that it matches the candidate. A stalled, contradictory or unfinished grid is
rejected. Since every deduction is forced and the candidate satisfies the clues,
full deduction also establishes a unique solution.

The saved report records passes, work and cells deduced by:

- Overlap in an otherwise unconstrained line.
- Line completion when one legal arrangement remains.
- Constrained overlap after other row/column deductions.

Version 1 rates Easy as at most two passes with no constrained-overlap deductions;
Medium as at most three passes with at most `size` constrained-overlap cells;
everything else that is fully line-solvable rates Hard. Candidates must actually
earn the requested rating. These are solver-effort ratings for playtesting, not
labels assigned solely from board dimensions.

Generation is bounded to 4,096 candidates, 50,000 solver work units per candidate,
four million total solver units and 100 passes per solve. A request also has a
cooperative four-second deadline off the UI thread. Cancellation, a deadline, or
an undecided solver budget aborts the request rather than skipping that candidate
and choosing a different result for the same seed. The chooser remains usable
and can start a new request. Prefetch tries at most four separate seeds to avoid
an immediate repeated picture; it never returns an unverified fallback.

## Local storage and compatibility

No Room migration is required: database version remains 5. Existing JSON session
payloads are extended for generated pictures. They store the full bitmap, unique
puzzle ID, size/difficulty/title, seed, generator version, motif/attempt and solver
report, together with the normal marks, history and hint count. The save also
contains a top-level generator version checked against the picture metadata.

There is one active slot per size/difficulty (`nonogram_endless-5-EASY`, etc.),
so six generated board slots suffice. Queued writes preserve ordering; starting
the next board awaits its initial snapshot. Completed results retain unique IDs
containing the seed/version in the existing `play_records` history. They survive
replacement of an active board and supply total solved counts and personal bests.
The same completion ID cannot count twice. Assisted completions count toward
totals but do not qualify for fastest unassisted times.

Resume reads the stored full picture without invoking the generator. Tests also
restore a valid snapshot labelled with an unavailable generator version to verify
that resume does not regenerate it. Picture Pack/daily codecs and old completions
remain supported. Timers/history have a small injectable store interface so UI
fixtures can run entirely against their own Room database.

## Verification

- 105 unit tests passed, including determinism, signed seed extremes, frozen v1
  pictures, all six size/difficulty choices, clue-only solvability, technique
  reports, variation, acceptance filters, work limits, timeouts, cancellation and
  separate Endless statistics/personal bests.
- Seven Endless device tests passed: full-snapshot file reopen, unavailable
  generator version, malformed data, bounded independent slots, hint/undo/resume,
  timed completion history, Next retaining the finished result, themes/Reduced
  Motion, timeout recovery and generation cancellation.
- The 13 existing Daily Five device tests and play-record migration test passed
  as compatibility checks (21 distinct device tests in total).
- Light/dark screenshots are kept in the ignored
  `output/endless-nonogram/screens-final/` directory. Build and lint checks are
  recorded alongside the local review output. Lint has zero errors, 15 existing
  warnings and three hints.
- The main APK is installed. A validated fresh backup and before/after comparison
  confirmed every existing value in all six game/progress tables was preserved
  during the upgrade (Room remains version 5). The app is left on the Endless
  size/difficulty chooser; no real puzzle was completed by the review automation.

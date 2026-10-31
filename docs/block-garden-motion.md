# Block Garden drag and placement feedback

The old drag updated screen-level state for every pointer event. Recomposition
repeated tray placement searches and game-over checks, including a game-over
search for each of the 64 board accessibility targets. Entering the board also
replaced the floating piece with a snapped preview, creating a visible jump.

Pointer position now lives in a stable drag object and is read in Canvas drawing.
The floating piece follows the finger continuously, including over the board;
the snapped guide is separate. Pickup has a 90 ms lift/scale transition without
interpolating or delaying finger movement. Availability checks stop at the first
fit without allocating all positions, and tray/game-over results are remembered
for each board and tray.

Invalid landing crosses use rounded strokes and a short reveal. Rejected-drop
markers linger briefly then fade. Cleared tile colours are captured before the
engine removes them and shrink/fade over 300 ms; crossing rows/columns still use
the engine's distinct cleared cells. Reduced Motion skips pickup and clear
movement. Enabling it finishes an active clear immediately; disabling it does
not replay an old effect. Off-board releases and cancelled gestures never place
a piece. Scoring, undo, daily objectives, and saved-board formats are unchanged.

Validation includes 94 unit tests and 19 distinct device tests: placement-search
equivalence, lifted-coordinate mapping for every shape, all board edges, continuous
drag without surrounding-screen recomposition, commit only on release, invalid
and cancelled drops, tap placement, Reduced Motion, effect completion, storage,
and Daily Five regression checks. Isolated animation screenshots are kept under
the ignored `output/block-motion/` directory. This verifies drawing behaviour and
avoided recomposition; it is not a measured frame-rate benchmark.

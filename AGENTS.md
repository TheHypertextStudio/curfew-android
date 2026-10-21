# Repository instructions

Commit messages use one of these scopes: `account`, `alarm`, `callback`, `ci`, `dx`, `sync`, or `ui`.

## Worktree preparation

Run `./bootstrap worktree prepare` for a fresh worktree. Keep native shared
caches intact and keep `node_modules`, `.build`, DerivedData, and application
state local to the checkout. Use existing build and test commands after setup.
Do not replace the shared engine with another dependency installer or setup
framework.


## Visual review

Every screenshot handoff must include a matrix across phone, tablet, and
foldable cover/inner windows in portrait and landscape. Include light and dark
mode and a narrow 200% text profile. Review the full rendered matrix before
presenting it. Keep controls reachable and avoid unnecessary scrolling at normal
text size. Label emulated windows separately from physical-device and hinge
posture evidence. Use `scripts/capture-welcome-matrix.py` for the welcome screen;
it resets only an owned disposable Sunrise emulator and restores its display
settings. Do not substitute one phone capture for this matrix.

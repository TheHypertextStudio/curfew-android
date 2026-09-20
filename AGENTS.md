# Repository instructions

Commit messages use one of these scopes: `account`, `alarm`, `callback`, `ci`, `dx`, `sync`, or `ui`.

## Worktree preparation

Run `./bootstrap worktree prepare` for a fresh worktree. Keep native shared
caches intact and keep `node_modules`, `.build`, DerivedData, and application
state local to the checkout. Use existing build and test commands after setup.
Do not replace the shared engine with another dependency installer or setup
framework.

---
type: project
created: 2026-05-25
updated: 2026-07-12
---

# Project Conventions

## Git Workflow
- Always create a new dedicated branch for major code changes.
- Branch name format should follow: `feature/[task-slug]` or `fix/[bug-slug]`.

## Supported AI platforms (AG Kit)
- AG Kit **only supports Gemini CLI and Google Antigravity**.
- Do not claim compatibility with Claude Code, Cursor, Copilot, Windsurf, or other assistants unless the user explicitly expands scope.
- Copy on the website, docs, FAQ, README, and marketing should describe AG Kit as a toolkit for Gemini CLI / Antigravity-style agent setups.

## Sestia Client Versioning
- Sestia Client versions use base-10 positional `X.Y.Z` mapped directly from `VersionCode`:
  - `major = VersionCode / 100`, `minor = (VersionCode / 10) % 10`, `patch = VersionCode % 10`.
  - When the rightmost digit reaches 10, the middle digit increments by 1 (`2.1.9 -> 2.2.0`).
  - When the middle digit reaches 10, the leading digit increments by 1 (`2.9.9 -> 3.0.0`).
  - Example sequence starting from `212`: `v2.1.2 -> v2.1.9 -> v2.2.0 -> ... -> v2.9.9 -> v3.0.0`.

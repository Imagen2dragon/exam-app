# Tibeb (exam-app) – working rules

## Git workflow (owner's rule)
- Never commit directly to `main`.
- Every new feature or fix: create a branch (`feat/<name>`, `fix/<name>`, `chore/<name>`), commit, push,
  open a pull request into `main`, then merge it once the checks below pass.
- One feature per PR. PR description: what changed, why, how it was tested, screenshots for UI changes.

## Checks before opening a PR
- Server: `cd server && python -m pytest -q`
- Android: `cd android && ./gradlew testDebugUnitTest assembleRelease`
  (unit, migration, language and screenshot tests; release build runs lint)
- UI changes: `./gradlew recordRoborazziDebug` and look at `docs/screenshots/` (English) and `docs/screenshots/am/` (Amharic).

## Project rules
- The Anthropic API key lives only on the server (`server/.env`, git-ignored). Never put keys in the app or the repo; this repository is public.
- Every user-visible string goes through `android/tools/strings_table.py` (English + Amharic), then `python3 android/tools/gen_strings.py`.
- Schema changes need a Room migration and a test in `MigrationTest.kt`.
- Keep the release APK small (currently ~1.5 MB); avoid heavy libraries.

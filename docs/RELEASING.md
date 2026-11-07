# Releasing a new version

Official APKs are built and signed by GitHub Actions (`.github/workflows/release.yml`), never on a laptop.

## One-time setup (owner)
Add 4 repository secrets at **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `TIBEB_KEYSTORE_BASE64` | the release keystore, base64-encoded (one line) |
| `TIBEB_KEYSTORE_PASSWORD` | keystore password |
| `TIBEB_KEY_ALIAS` | `tibeb` |
| `TIBEB_KEY_PASSWORD` | key password |

Keep a private backup of the keystore (`tibeb-release.jks`). **If it is lost, no future version can be installed as an update.** Never commit it; this repository is public.

## Each release
1. In the PR for the release, change `version.txt` (e.g. `0.4.0` → `0.5.0`). Always increase it.
2. Merge the PR once CI is green.
3. The **Release** workflow runs automatically: it tests, builds, signs, refuses debug-signed APKs, creates the tag `v0.5.0`, and publishes the GitHub Release with `tibeb.apk`.
4. The README link `releases/latest/download/tibeb.apk` and the in-app update notice pick it up automatically.

To re-run a release by hand: **Actions → Release → Run workflow** (it skips versions that are already released).

`version.txt` is the single source of the version: `0.5.0` → versionName `0.5.0`, versionCode `500`.

Expected signing certificate SHA-256: `a69d3924d96174e922a6fae63922f82352922db5e8b982c99bce06a83bea5e3f`

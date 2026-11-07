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
1. Merge the feature PRs into `main` (CI must be green).
2. Tag and push: `git tag v0.4.0 && git push origin v0.4.0`, or on GitHub: **Releases → Draft a new release → Choose a tag → type `v0.4.0` → Create new tag → Publish**.
3. The workflow runs the tests, builds the APK, signs it, refuses to continue if it's debug-signed, and publishes the release with `tibeb.apk` attached.
4. The README link `releases/latest/download/tibeb.apk` and the in-app update notice pick it up automatically.

Version numbers come from the tag: `v0.4.0` → versionName `0.4.0`, versionCode `400`. Always increase the version.

Expected signing certificate SHA-256: `a69d3924d96174e922a6fae63922f82352922db5e8b982c99bce06a83bea5e3f`

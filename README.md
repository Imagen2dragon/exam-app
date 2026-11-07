# Tibeb (ጥበብ) – AI exam prep for the Ethiopian Grade 12 national exam

## ⬇️ Download the Android app

<table><tr><td>

**[Download the latest Tibeb (APK, ~1.5 MB)](https://github.com/Imagen2dragon/exam-app/releases/latest/download/tibeb.apk)**

Android 7.0 or newer · English and አማርኛ · [all versions](https://github.com/Imagen2dragon/exam-app/releases)

Or scan the QR code with your phone's camera →

</td><td>

<img src="docs/download-qr.png" alt="QR code linking to the latest Tibeb APK" width="160">

</td></tr></table>

The link and QR code always give the **newest** version. The app also tells you inside Home when an update is available, and installing an update keeps all your books, notes and results.

**How to install**
1. Open the link on the phone and download `tibeb.apk`.
2. Tap the downloaded file. If Android asks, allow **"Install unknown apps"** for your browser or file manager (this is normal for apps from outside the Play Store).
3. If Play Protect shows a warning, tap **More details → Install anyway**. The app asks for only one permission: internet.
4. Open Tibeb → ⚙️ Settings → enter the server address and device token to turn on the AI features. Flashcards and saved past papers work without them.

**Is my download genuine?** Every official release is signed with the Tibeb key. Certificate SHA-256:
`a69d3924d96174e922a6fae63922f82352922db5e8b982c99bce06a83bea5e3f`

---

Android app plus a small backend. Upload textbooks, teacher guides and past national exams. Tibeb then:
- **Creates exams** (multiple choice, calculation, short answer, *draw-a-graph* with photo grading) at national, hard or very hard level, weighted toward his weak topics
- **Makes notes** (summary, detailed, formula sheet, cheat sheet), in English or Amharic
- **Makes flashcards** with spaced repetition (SM-2)
- **Full national mock**: all 6 papers of the stream, scored out of 600 against the 300 pass line
- **Amharic or English interface**, with Ethiopian-calendar dates in Amharic
- **Works offline** for review, notes and exams built from saved past papers

Release APK: **1.5 MB**, Android 7.0+ (minSdk 24), one permission (INTERNET).
See [`docs/RESEARCH_AND_ROADMAP.md`](docs/RESEARCH_AND_ROADMAP.md) for the research, the security design and the next steps. Screenshots are in `docs/screenshots/`.

## Admin app for Windows (owner only)

**[Download TibebAdmin.msi](https://github.com/Imagen2dragon/exam-app/releases/latest/download/TibebAdmin.msi)**: follow your student's preparation and AI use from your PC.

- **Overview:** days to the exam, average score, best national mock (out of 600, against the 300 pass line), streak, mistakes to fix; charts over time; per-subject averages
- **Topics** (weak / developing / strong), **Exams** (recent results and mocks), **Study days** (calendar), **AI use** (cost per day vs. the daily budget, cost by feature, cache savings), **Books**
- Sign in with your server address and the server's `ADMIN_TOKEN`. Phones never have that key, and the key is stored encrypted for your Windows account (DPAPI). 10 wrong keys lock the admin side for 15 minutes.
- **The student sees that sharing is on** (a notice in the app and a Settings card). Only results and activity are shared, never notes or tutor chats.
- The installer is not code-signed yet, so Windows SmartScreen may say "Windows protected your PC": click **More info → Run anyway**. It installs for your user only (no admin rights needed).

## 1. Run the server
**On a server, with HTTPS:** follow [`docs/DEPLOY.md`](docs/DEPLOY.md): Docker + Caddy (automatic HTTPS certificate), the keys to generate, a smoke test that calls every AI feature once and prints the cost, backups and updates. About 30 minutes on a ~US$5/month server.

**Encrypted cloud backup (optional):** [`docs/SUPABASE.md`](docs/SUPABASE.md). A free Supabase project in Frankfurt. The phone encrypts its backup with the student's recovery code before uploading, so nobody else can read it.

**On your computer, for development:**
```bash
cd server
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env        # put your ANTHROPIC_API_KEY and a device token in it
set -a; . ./.env; set +a
uvicorn app:app --host 127.0.0.1 --port 8080
```
Tests: `pip install pytest httpx && python -m pytest -q` (Claude is mocked; no key needed).

**Costs:** every AI call is recorded with its tokens and estimated cost (only a hash of each device token is stored). Each device has a daily budget (`DAILY_BUDGET_USD`, default $5, resets at midnight Addis Ababa time); the app shows today's use in Settings. See the spend per day and device with:
```bash
python usage_report.py 7                                   # or, with Docker: docker compose exec api python usage_report.py 7
```

**Models:** exams, book reading, photo grading and the tutor chat use Claude Opus 5.5. Flashcards and revision notes use the much cheaper Claude Haiku 5.5 (about 40x cheaper per input token); if Haiku declines a request it is retried once on Opus. Change per task in `server/.env` (`MODEL_FLASHCARDS`, `MODEL_NOTES`, `MODEL_DEFAULT`).

## 2. Build the app
Requires JDK 17+ and the Android SDK (platform 36).
```bash
cd android
./gradlew assembleRelease            # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest          # unit + screenshot tests
./gradlew recordRoborazziDebug       # regenerate docs/screenshots
```
A local release build without the signing secrets is debug-signed: fine for testing, but only publish the APKs that `.github/workflows/release.yml` builds (see `docs/RELEASING.md`).

## 3. First launch
Settings (gear on Home) → enter the server address (`https://…`) and the device token → **Save & test**.

## Layout
```
server/   FastAPI proxy: auth, rate limits, validation, Claude calls (ai.py), JSON schemas
android/  Kotlin + Jetpack Compose + Room (FTS4) – no OkHttp/Retrofit/DI libs, to stay small
supabase/ setup.sql for the encrypted backup bucket (+ policy tests)
deploy/   docker-compose + Caddyfile (HTTPS) + backup script; guide in docs/DEPLOY.md
admin/    Windows admin app (Compose Desktop, MSI)
docs/     research + roadmap, deploy and release guides, screenshots
```

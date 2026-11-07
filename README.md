# Tibeb (ጥበብ) – AI exam prep for the Ethiopian Grade 12 national exam

## ⬇️ Download the Android app

<table><tr><td>

**[Download Tibeb 0.3.0 (APK, 1.5 MB)](https://github.com/Imagen2dragon/exam-app/raw/main/docs/tibeb-0.3.0-preview.apk)**

Android 7.0 or newer · English and አማርኛ

Or scan the QR code with your phone's camera →

</td><td>

<img src="docs/download-qr.png" alt="QR code linking to the Tibeb APK download" width="160">

</td></tr></table>

**How to install**
1. Open the link on the phone and download the APK.
2. Tap the downloaded file. If Android asks, allow **"Install unknown apps"** for your browser or file manager (this is normal for apps from outside the Play Store).
3. If Play Protect shows a warning, tap **More details → Install anyway**. The app asks for only one permission: internet.
4. Open Tibeb → ⚙️ Settings → enter the server address and device token to turn on the AI features. Flashcards and saved past papers work without them.

This is a **preview build**. SHA-256: `a2d1e228f2cd9eb051ec5d4fbb7eae383a2aa0fff7b1812931fb7b318e89b7ce`

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

## 1. Run the server
```bash
cd server
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env        # put your ANTHROPIC_API_KEY and a device token in it
set -a; . ./.env; set +a
uvicorn app:app --host 127.0.0.1 --port 8080
```
For the phone to reach it, put it behind HTTPS (e.g. Caddy: `your.domain { reverse_proxy 127.0.0.1:8080 }`).
Tests: `pip install pytest && python -m pytest -q` (Claude is mocked; no key needed).

## 2. Build the app
Requires JDK 17+ and the Android SDK (platform 36).
```bash
cd android
./gradlew assembleRelease            # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest          # unit + screenshot tests
./gradlew recordRoborazziDebug       # regenerate docs/screenshots
```
The release build is signed with the debug key so it installs right away. Create your own keystore before sharing it widely.

## 3. First launch
Settings (gear on Home) → enter the server address (`https://…`) and the device token → **Save & test**.

## Layout
```
server/   FastAPI proxy: auth, rate limits, validation, Claude calls (ai.py), JSON schemas
android/  Kotlin + Jetpack Compose + Room (FTS4) – no OkHttp/Retrofit/DI libs, to stay small
docs/     research + roadmap, screenshots, preview APK
```

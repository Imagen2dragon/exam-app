# Tibeb – research, design decisions and roadmap

## 1. How the Ethiopian university entrance exam works (what the app must train for)

| Fact | Detail | Source |
|---|---|---|
| Exam | Ethiopian Secondary School Leaving Certificate Exam (ESSLCE) / Grade 12 national exam, run by the Educational Assessment and Examinations Service (EAES) | [Wikipedia](https://en.wikipedia.org/wiki/National_Educational_Assessment_and_Examination_Agency) |
| Papers | 6 papers × 100 marks = **600**. Everyone: English, Mathematics, Scholastic Aptitude. Natural stream adds Physics, Chemistry, Biology. Social stream adds Geography, History, Economics. | [Temari](https://temari.et/blog/grade-12-exam-format-and-marking), [Ethiopian Monitor](https://ethiopianmonitor.com/2025/09/14/grade-12-exam-results-8-4-of-the-students-hit-the-passing-mark/) |
| Pass mark | **50% (300/600)** for direct university admission | same |
| Format | Multiple choice (≈100 questions per paper is reported by practice sites; not confirmed by an official EAES source) | [open-exam-prep](https://open-exam-prep.com/practice/et-ethiopian-esslce) |
| Curriculum | From 2018 E.C. (2025/26), grades 10–12 are examined on the **new national curriculum**; grade 9 only on topics common to old and new | [Kehulum](https://kehulum.com/blog-detail/Ethiopia-12th-Grade-National-Exam-2025-Subjects-Syllabus-and-Exam-Structure-Guide-191) |
| Going digital | Online pilot in Addis Ababa (2024). In 2025, 134,609 of 585,882 sat online (23%); for 2025/26 most candidates were planned online. Government goal: fully digital (Digital Ethiopia 2030). | [Shega](https://shega.co/news/addis-ababa-to-administer-grade-12-national-exams-online), [ENA](https://www.ena.et/web/eng/w/eng_8515594), [Ethiopian Monitor](https://ethiopianmonitor.com/2025/09/14/grade-12-exam-results-8-4-of-the-students-hit-the-passing-mark/) |
| Pass rates | 2023/24: 5.4% · 2024/25: 8.4% · 2025/26: **12.8%** (70,544 of 550,210; natural 16.5%, social 8.3%) | [Ethiopian Monitor 2026](https://ethiopianmonitor.com/2026/08/22/grade-12-university-entrance-exam-over-70500-students-obtain-passing-mark/), [Ethiopia Today](https://ethiopiatoday.net/ethiopias-2025-2026-grade-12-exam-pass-rate-rises-to-12-8-up-from-last-years-8-9/) |

**What this means for the design**
- The exam is mostly **on-screen, timed multiple choice**, so the app's exam mode copies that: a timer, a question palette, and A–D options.
- The real exam is MCQ only, but **calculation, written and graph questions** build the understanding that MCQs test. That's why they are optional types.
- Low pass rates mean the main risk is weak fundamentals. The app tracks **weak topics** and feeds them back into the next exam.

## 2. What other countries' apps do, and what Tibeb borrows

| Country / app | What it does well | Borrowed in Tibeb |
|---|---|---|
| 🇪🇹 **Ethio Matric** (past questions 2008–2017 E.C. with explanations), **Exam Galaxy** (~97k downloads), **Ethio Exam Prep**, **Zede Academy** (grades 7–12, offline, small) | Past national exams with explanations, offline | An offline past-exam question bank, built from papers *your brother uploads* |
| 🇨🇳 **Zuoyebang / Question.AI**, **Yuanfudao**, **Doubao AiXue** | Photograph a problem or homework page and get it checked step by step | **Photo grading** of handwritten working and sketched graphs, with method marks |
| 🇨🇳 Gaokao practice | AI apps switch off photo-solving during exam days to protect integrity | (Idea) a "focus lock" exam mode, below |
| 🇮🇳 **NTA National Test Abhyas** (free government CBT mocks) | Mocks that look like the real computer-based test, instant analytics | Timed on-screen exam UI and per-topic accuracy |
| 🇮🇳 **Physics Wallah** | AI answer grader for written answers; study plan from mock results | AI grading of short answers; weak-topic weighting |
| 🇮🇳 **Embibe** | Deep analytics, personalised practice | Topic-mastery stats on Home ("Needs work") |
| 🇰🇷 **QANDA (Mathpresso)** | Photo → solution in seconds; CSAT past papers; LLM tutor | Step-by-step explanations on every question |
| 🇰🇷 **EBS** (background knowledge, not verified in this research) | Korea's CSAT is officially linked to EBS study books | Exams are generated **from the books he uploads**, the same idea of tying practice to the official textbooks |

**Gap Tibeb fills:** every Ethiopian app found is a *fixed* question bank. None of them generates new hard questions from the student's own textbooks, or grades handwritten work and graphs.

Sources: [Ethio Matric (Play)](https://play.google.com/store/apps/details?id=com.addismatric.addismatric&hl=en), [Exam Galaxy (AppBrain)](https://www.appbrain.com/app/ethio-grade-12-entrance-exam/com.appysinia.exam_galaxy), [Ethio Exam Prep](https://www.appbrain.com/app/ethio-exam-prep-app/com.ethioexamprep.app), [Zede Academy](https://zedeacademy.et/download/), [Shega: apps for grade 12](https://shega.co/news/apps-to-prepare-for-grade-12-examination-in-ethiopia), [SCMP on Yuanfudao](https://www.scmp.com/tech/start-ups/article/2185452/tencent-backed-ai-firm-aims-free-parents-and-teachers-checking), [Gaokao AI freeze](https://www.gurufocus.com/news/2915661/alibaba-tencent-freeze-ai-photo-tools-for-gaokao), [NTA Abhyas](https://www.similarweb.com/app/apple/1521249409), [PW](https://www.pw.live/neet/class-11/test-series/66d69a93319eb62805d86d88-online), [QANDA](https://en.wikipedia.org/wiki/QANDA), [MathGPT](https://www.koreaherald.com/article/3299066).

## 3. Architecture

```
Android phone (1.4 MB APK, Android 7+)              Your server (small VPS)            Anthropic
┌────────────────────────────────────┐   HTTPS   ┌─────────────────────────┐  HTTPS  ┌─────────┐
│ Room DB: books→text chunks (FTS),  │ ────────► │ FastAPI: device-token    │ ──────► │ Claude  │
│ question bank, notes, flashcards,  │  token    │ auth, rate limit, input  │ API key │ Opus 5.5│
│ exams, results  (all offline)      │ ◄──────── │ validation, JSON schemas │ ◄────── │         │
└────────────────────────────────────┘           └─────────────────────────┘         └─────────┘
```
- **Upload once, study offline.** A PDF or photo is sent once. Claude splits it into study sections and pulls out every exam question. Only that text is stored on the phone, so a 15 MB book becomes about 300 KB.
- **On-device retrieval.** When he asks for an exam on "Logarithms", the phone's full-text index finds the right pages and sends just those to the AI. That's cheaper and more accurate.
- **Offline mode.** Flashcard review, saved notes, and exams drawn from uploaded past papers all work with no internet.
- **Grading.** MCQ and numeric answers are graded instantly on the phone. Written answers and photographed graphs are graded by the AI, with method marks.

## 4. Security checklist (implemented)
- The AI key is **never in the APK**. It lives only on the server. Each phone gets its own revocable device token.
- The token is encrypted with AES-256-GCM, using a key held in the **Android Keystore**.
- **HTTPS only** (network security config). Cleartext is allowed only for the emulator during development.
- No cloud backup of app data (`allowBackup=false` plus data-extraction rules).
- **No storage or camera permissions.** Files come through the system picker and photos through the camera app. The only permission is INTERNET.
- The server checks tokens in constant time, rate-limits per device, limits body size (20 MB), and validates every field (subjects, grades and lengths are whitelisted). API docs endpoints are off, and responses are `no-store`.
- The prompt tells the AI to treat text inside uploaded documents as material, not instructions (prompt-injection guard).
- Full-text search queries are sanitised before reaching SQLite.
- The release build uses R8 shrinking and obfuscation.

## 5. Suggested roadmap (step by step)

**Step 1 – get it running for your brother (next session)**
1. Deploy the server: a small VPS or Fly.io/Render, with Caddy for automatic HTTPS. Create his device token.
2. Run a real test with your API key: upload one unit of the Grade 12 Maths textbook and one past paper, then check the quality of the questions.
3. Add a release signing key. Install with `adb install` or share the APK.

**Step 2 – match the real exam more closely**
4. A "Full national mock" mode: all 6 papers of his stream, each timed like the real exam, with a final score out of 600.
5. Proper math rendering (fractions, roots, powers) in questions and notes.
6. An Amharic interface (the notes can already be generated in Amharic).
7. Upload several page photos at once, as "a chapter".
8. Move long uploads to WorkManager, so they finish even if the app is closed.

**Step 3 – make it smarter**
9. A **mistake notebook** (the Chinese 错题本 method). Every wrong answer becomes a flashcard automatically and comes back in later exams.
10. A study planner with a countdown to the exam date, and daily targets per subject.
11. A topic-mastery map against the official curriculum units, for grades 9–12.
12. An "Explain this question" chat on any question, in English or Amharic.

**Step 4 – cost and scale (your decision)**
13. Prompt caching of book context, and overnight Batch generation (50% cheaper) for large question packs.
14. A cheaper model for flashcards and notes (Claude Sonnet 5.5 or Haiku 5.5), keeping Opus for exams and grading.
15. Downloadable offline question packs, so classmates can share without each one paying for AI.

**Step 5 – hardening**
16. Encrypt the local database with SQLCipher (adds about 3 MB to the APK).
17. Certificate pinning, and Play Integrity if you ever publish more widely.
18. A TalkBack and large-font accessibility pass on a real low-end phone.
19. A focus lock: screen pinning during an exam for self-discipline (the same idea as China's gaokao rule).

**Content note:** Ministry of Education textbooks are distributed free, and using past papers for personal study is fine. Don't redistribute other apps' question banks.

## 6. Hugging Face models – should Tibeb use them?

**Caveat:** huggingface.co is blocked from the build environment, so the model cards could not be opened directly. The figures below come from search results and the linked papers. Verify them on the model page before integrating anything.

| Model (Hugging Face) | What it is | Fit for Tibeb |
|---|---|---|
| **rasyosef/embedding-amharic-base** (RoBERTa, ~110M) and a ~42M "medium" variant | Amharic text-embedding models (ACL 2025 Findings). Reported +17.6% MRR@10 over the best multilingual baseline (Arctic Embed 2.0, 568M) on Amharic retrieval, with the medium model "13× smaller" yet competitive. [paper](https://aclanthology.org/2025.findings-acl.543.pdf), [code](https://github.com/kidist-amde/amharic-ir-benchmarks) | ✅ **Best candidate.** Smarter on-device search when he writes topics or notes in Amharic. Make it an optional download, since it adds tens of MB. |
| **Qwen/Qwen3-1.7B** (Apache-2.0) | Small multilingual model with an optional "thinking" mode; often named the best small all-rounder for phones ([guide](https://www.llmtrust.com/best/small-llms)) | 🟡 Optional **offline tutor** ("explain this question") on phones with ≥6 GB RAM. About a 1 GB download. Not good enough to write or grade national-exam papers. |
| **Gemma 3 1B** | The only small model reported to fit 4 GB phones; limited reasoning ([guide](https://www.promptquorum.com/power-local-llm/mobile-llm-models-phi4-gemma-smollm)) | 🟡 Same role as Qwen3 on low-RAM phones. Weaker at maths. |
| **rasyosef Llama 3.2 Amharic** (180M–1B) | Llama 3.2 trained on Amharic text ([collection](https://huggingface.co/collections/rasyosef/llama-32-amharic)) | 🔴 Interesting for Amharic chat. No exam-level reasoning. |
| **fidelizML/AmhariXLM-0.5B** | 70% English / 30% Amharic. Self-reported 45–80% on single-step arithmetic and 37–55% GSM8K ([card](https://huggingface.co/fidelizML/AmhariXLM-0.5B)) | 🔴 Too weak for hard maths questions. |
| **EthioNLP/Amharic-LLaMA** | LLaMA-2 + LoRA on Amharic tasks. The card notes verbose or imprecise output ([card](https://huggingface.co/EthioNLP/Amharic-LLAMA-all-data)) | 🔴 Older; not for exams. |
| **b1n1yam/gemma-2-27b-amharic-cpt** | 27B Gemma 2, continued pre-training on Amharic. No published benchmarks ([models](https://huggingface.co/models?other=amharic&p=0&sort=trending)) | 🔴 Too big for phones. Self-hosting needs a GPU, which costs more than the API for one student. |
| Ethiopian exam datasets | Searched; **none found** on Hugging Face | Tibeb's own uploaded past papers are the dataset. |

**Recommendation:** keep Claude for writing exams, grading photos and making notes; the quality on hard national-exam questions matters most. Use Hugging Face models only where they add something Claude can't: (1) the **Amharic embedding model** for better offline search, and (2) an **optional offline tutor** (Qwen3-1.7B or Gemma 3 1B) for students with no data. Both are optional downloads, so the base app stays 1.5 MB. To build either, `huggingface.co` must be added to the environment's allowed hosts.

## 7. Step 2 progress
- ✅ **Full national mock**: 6 papers of the stream, each out of 100, total out of 600, with the 300 pass line, a projected score and the weakest paper. Lengths 30, 60 or 100 questions per paper, with estimated per-subject timings. Papers are AI-planned across grades 9–12 (new curriculum), or built offline from saved past papers. Papers are generated when started, or all at once with "Prepare all" so the mock can be sat offline.
- ✅ **Math display**: simple LaTeX (`x^2`, `\frac{}{}`, `\sqrt{}`, `\lim_{x\to3}`, Greek letters, `H_2O`) shows as real superscripts, subscripts and symbols, with no library.
- ✅ Database migration v1→v2 (keeps all existing data), with a test.
- ✅ **Amharic interface (አማርኛ)**: all 232 interface strings and every error message in English and Amharic. Settings → App language: Phone default / English / አማርኛ, working from Android 7, and on Android 13+ also listed under the system's App languages. Dates use the **Ethiopian calendar** in Amharic (e.g. 28 ጥቅምት 2019 ዓ.ም.). Exam questions stay in English, like the real national exam. Wording lives in `android/tools/strings_table.py`; run `python3 tools/gen_strings.py` after editing. **A native-speaker review is recommended.**
- ✅ **Background uploads** (WorkManager): a book keeps uploading after the app is closed, waits for a connection, and retries automatically when the signal drops; real errors show a Retry button.
- ✅ **Chapter from photos**: photograph up to 30 pages (or pick them from the gallery), reorder or remove them, and upload them as one chapter. Each page is compressed to ~300 KB, and the AI reads them in order.
- ✅ **Signed releases**: one permanent download link, an in-app update notice, and updates that keep the student's data (see `docs/RELEASING.md`).

**Step 2 is complete (v0.5.0).**

## 8. Step 3 progress (v0.6.0)
- ✅ **Mistake notebook** (错题本): every wrong answer becomes a flashcard; the notebook keeps a question until the latest attempt is fully correct; "Retry" builds an offline exam from current mistakes.
- ✅ **Study planner + exam countdown**: exam date with days left (Ethiopian calendar in Amharic), today's plan from real data (due cards, mistakes, weakest subject, weekly mock in the last 60 days), day streak.
- ✅ **Topic mastery map**: every topic from books and exams as a tile (not started / weak / developing / strong, written out, not colour-only); tap a tile to practise that topic.
- ✅ **"Explain this question" chat**: ask follow-up questions about any reviewed question or mistake, in English or Amharic. Online only; chats are not stored.

**Step 3 is complete.**

## 9. Step 4 progress (v0.7.0): AI cost
- ✅ **Usage tracking + daily budget per device**: every AI call is recorded with tokens and estimated cost (device stored as a hash). `DAILY_BUDGET_USD` caps each phone, Settings shows today's use, and `usage_report.py` shows the owner's costs.
- ✅ **Per-task models** (owner's choice): Claude Haiku 5.5 for flashcards and notes (about 40x cheaper per input token), with a retry on Opus 5.5 if Haiku declines; Opus 5.5 for everything else.
- ✅ **Prompt caching**: the system prompt, the shared mock-paper prefix (the first section streams first, then the rest read the cache) and the tutor chat history. Cache reads cost 5% of input on Opus 5.5.
- ✅ **Overnight question packs**: 50–200 new questions written through the Message Batches API at **half price**, collected in the background and added to the offline bank for offline exams and mocks.

**Step 4 is complete.** Next (chosen by the owner): **Supabase** (accounts, cloud backup, shared packs for classmates, progress dashboard), then an **on-phone offline tutor**, then Step 5 (hardening: encrypted database, certificate pinning, accessibility pass on a low-end phone, exam focus lock).

## 10. Admin app and deployment (v0.8.0)
- **Progress sharing** (phone → server): a summary of results, mock scores, topic mastery, open mistakes, streak and book titles, every 6 hours and after each exam. The student is told on Home and in Settings. Notes and tutor chats are never shared.
- **Admin API** on the server, protected by its own `ADMIN_TOKEN` (phones never have it), with a lockout after 10 wrong keys. Keeps one snapshot per day for 400 days.
- **Tibeb Admin for Windows** (MSI): overview, charts over time, topics, exams and mocks, study calendar, AI cost per day and per feature against the budget, books. The key is stored with Windows DPAPI.
- **Deploy kit:** Docker + Caddy (automatic HTTPS), a locked-down container, backups, a smoke test that makes one real call per AI feature and prints the cost. Guide: [`DEPLOY.md`](DEPLOY.md).

Part of the Supabase plan (a progress dashboard) is now covered by the admin app on your own server. Supabase remains the option for accounts, cloud backup and sharing packs with classmates.

## 11. Encrypted cloud backup (Supabase)
The owner's choices: backup + restore first; the phone talks to Supabase directly; encrypted on the phone; Frankfurt region.
- The phone signs in with a user the owner creates (public sign-up off). It gets the Supabase address and public key from the Tibeb server.
- A random 25-character recovery code → PBKDF2-HMAC-SHA256 (200,000 rounds) → AES-256-GCM. The header is authenticated too. Supabase, the server and the admin can't read the backup.
- Once a day, and only when something changed; optional Wi-Fi only. Restore swaps the database in at the next start, before Room opens it, and runs migrations for older backups.
- No Supabase SDK (about 50 KB added to the APK). Storage rules are tested in CI; the phone side is tested against a local stand-in with the same rules.
- Guide: [`SUPABASE.md`](SUPABASE.md). Next: the on-phone offline tutor, then Step 5 hardening.

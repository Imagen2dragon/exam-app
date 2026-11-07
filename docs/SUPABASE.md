# Encrypted cloud backup with Supabase

If your brother's phone is lost, stolen or reset, everything he built up (books, flashcards, exams,
mistakes, mock results) comes back on a new phone. The backup is **encrypted on the phone** with his
recovery code before it is uploaded, so Supabase, your server and you can only see that a backup
exists, when it was made and how big it is. Nobody can read what's inside.

```
phone ──(encrypted file, his own login)──> Supabase Storage: backups/<his user id>/tibeb.backup
  └── gets the Supabase address + public key from your Tibeb server (/v1/config)
```

## How it works

| | |
|---|---|
| **Login** | You create one Supabase user for him (email + password). Public sign-up stays **off**, so the public key in the app can't be used by strangers to create accounts and fill your storage. |
| **Encryption** | When he turns backup on, the phone creates a **recovery code** (25 characters, e.g. `K7Q2M-9XD4T-PAHW3-N8CRE-6YB2F`). The code is turned into an AES-256 key on the phone (PBKDF2, 200,000 rounds). The code never leaves the phone. He writes it down once. |
| **Backups** | Once a day while online, and only if something changed since the last one. The phone compresses its study database, encrypts it and replaces the previous backup. A **Wi-Fi only** switch saves mobile data. |
| **Restore** | On a new phone: connect to your server, sign in to backup, type the recovery code. Without the code the backup cannot be opened. That's the point, so keep the code safe. |
| **Who can read the file** | Storage rules (`supabase/setup.sql`) let a signed-in user reach only the folder named after their own user id. Tested in CI. |

## Setup (about 15 minutes)

1. **Create the project.** Go to [supabase.com](https://supabase.com), create an account, then **New project**:
   - Name: `tibeb`
   - Region: **Central EU (Frankfurt)**, i.e. `eu-central-1`
   - Database password: generate a strong one and keep it in your password manager. The app never needs it.
   - The free plan is enough. A backup is usually 1–10 MB.
2. **Create the storage rules.** In the dashboard open **SQL Editor → New query**, paste all of [`supabase/setup.sql`](../supabase/setup.sql) and press **Run**. It creates the private `backups` bucket (50 MB per file) and its rules. It's safe to run again.
3. **Close public sign-up.** Open **Authentication → Sign In / Providers** and turn **off** "Allow new users to sign up". Leave the **Email** provider enabled.
4. **Create his user.** Open **Authentication → Users → Add user → Create new user**. Enter his email (any address he can remember) and a password of at least 10 characters, and tick **Auto Confirm User**. Give him the email and password; he types them once on the phone.
5. **Tell your server.** Open **Project Settings → API Keys** and copy:
   - the **Project URL** (`https://<something>.supabase.co`)
   - the **Publishable key** (`sb_publishable_…`). If your project only shows legacy keys, use the `anon` `public` one.

   **Never** use the *secret* / `service_role` key. It bypasses all the rules. The server refuses to
   hand it to the phone even if you put it in by mistake, but keep it out of `.env` anyway.

   On the server, add the two values to `server/.env`:
   ```
   SUPABASE_URL=https://<something>.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
   ```
   Then restart: `cd /opt/exam-app/deploy && sudo docker compose up -d`.
   The smoke test's *Config for the phone* line should now show your Supabase address.
6. **Turn it on in the phone.** In the app open **Settings → Cloud backup → Sign in**. Then:
   - **write down the recovery code** it shows (on paper, kept at home)
   - tap **I wrote it down**
   - wait for the first backup to finish

   From then on the admin app shows "Cloud backup: … ago". If no backup arrives for 3 days, it shows a red warning.

## Good to know

- **Free-plan pause:** Supabase pauses free projects after a week with no activity. The daily backups count as activity. If he stops using the phone for a long time and the project pauses, press **Restore project** in the dashboard.
- **Lost recovery code, phone still working:** he can show the code again in **Settings → Cloud backup**, or start a new backup with a new code. The old backup is then replaced.
- **Lost recovery code and lost phone:** the backup cannot be opened. That's the trade-off for nobody else being able to read it.
- **Forgotten password:** reset it in **Authentication → Users** in the dashboard.
- **Remove everything:** in **Storage → backups** delete his folder, and in **Authentication → Users** delete the user.

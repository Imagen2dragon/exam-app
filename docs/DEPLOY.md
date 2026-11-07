# Deploying the Tibeb server

The phone app and the Windows admin app both talk to **your own server**. The server holds the
Anthropic API key, checks each phone's device token, limits spending and stores the progress
history that the admin app shows. This guide gets it running on a small rented server with
HTTPS in about 30 minutes.

```
phone (Tibeb app) ──HTTPS──┐
                           ├──> Caddy (HTTPS certificate) ──> Tibeb API ──> Claude API
PC (Tibeb Admin) ───HTTPS──┘                                   │
                                                               └── /data/usage.db (usage, progress, packs)
```

## What you need

| | Notes | Typical cost |
|---|---|---|
| Anthropic API key | [console.anthropic.com](https://console.anthropic.com) → API keys. **Set a monthly spend limit** there too (Billing → Limits) as a second safety net on top of the server's daily budget. | pay per use |
| A small Linux server (VPS) | Ubuntu 24.04, 1 vCPU, 1–2 GB RAM, 20 GB disk is plenty. Pick a **Europe** region (e.g. Frankfurt, Amsterdam or Paris): it's closer to Addis Ababa than the US. Any provider works (Hetzner, DigitalOcean, Vultr, Linode, …). | ~US$4–6 / month |
| A domain name pointing to the server | Needed for the HTTPS certificate. A subdomain of a domain you own (`tibeb.yourname.com`), or a free one from [DuckDNS](https://www.duckdns.org) (`yourname.duckdns.org`). | free – $12 / year |

## 1. Prepare the server

Point the domain's **A record** at the server's IP address first (on DuckDNS: type the IP and press *update ip*). Then, logged in to the server over SSH:

```bash
# Docker (official install script) and a firewall that only lets in SSH and web traffic
curl -fsSL https://get.docker.com | sudo sh
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443 && sudo ufw --force enable

# The code
sudo git clone https://github.com/Imagen2dragon/exam-app.git /opt/exam-app
cd /opt/exam-app
```

## 2. Secrets: `server/.env`

```bash
sudo cp server/.env.example server/.env
sudo chmod 600 server/.env
# Two different random keys: one for the phone, one for you (the admin app)
python3 -c "import secrets; print('DEVICE_TOKENS=' + secrets.token_urlsafe(32))"
python3 -c "import secrets; print('ADMIN_TOKEN=' + secrets.token_urlsafe(32))"
sudo nano server/.env
```

In `server/.env` set:

- `ANTHROPIC_API_KEY=sk-ant-…` – your key. It never leaves this file: not in the app, not in the repo.
- `DEVICE_TOKENS=…` – the first generated value. For a second phone add another token after a comma.
- `ADMIN_TOKEN=…` – the second generated value. Only you get this one.
- `DAILY_BUDGET_USD=5` – the most the AI may cost per phone per day (resets at midnight Addis Ababa time).

Keep a copy of the two tokens in a password manager. `server/.env` is git-ignored; never commit it.

## 3. Start it

```bash
cd /opt/exam-app/deploy
echo "TIBEB_DOMAIN=tibeb.yourname.com" | sudo tee .env     # your domain, so later restarts remember it
sudo docker compose up -d --build
sudo docker compose ps                                      # api should become "healthy"
curl https://tibeb.yourname.com/healthz                     # {"ok":true}
```

Caddy gets the HTTPS certificate on the first request (this can take ~30 seconds) and renews it by itself.
The API container runs as a non-root user on a read-only filesystem and is not reachable from the
internet except through Caddy.

## 4. Smoke test (real Claude calls, roughly US$0.50–1.50)

From the server or your own computer (Python 3.10+, nothing to install):

```bash
cd /opt/exam-app/server
export TIBEB_DEVICE_TOKEN='the device token'
export TIBEB_ADMIN_TOKEN='the admin token'
python3 smoke_test.py https://tibeb.yourname.com
```

It checks HTTPS and token checks, then calls each AI feature once (notes in English and Amharic,
flashcards, a hard exam, the tutor chat, grading typed work and a photographed graph, reading a PDF),
then the admin API. At the end it prints what the run cost. Add `--full` to also generate a
10-question national mock paper and queue an overnight question pack. Every line should say `PASS`;
the troubleshooting table below covers the usual failures.

## 5. Connect the phone

1. On the phone, install the app from the [download link](https://github.com/Imagen2dragon/exam-app/releases/latest/download/tibeb.apk).
2. Open **Settings** (gear on Home) → server address `https://tibeb.yourname.com` → device token → **Save & test**.
3. Tell your student that progress sharing is on and what it covers. The app also explains it: a notice on Home the first time, and a *Progress sharing* card in Settings that shows when it was last shared.

## 6. Connect the admin app (Windows)

Install [TibebAdmin.msi](https://github.com/Imagen2dragon/exam-app/releases/latest/download/TibebAdmin.msi).
Sign in with the server address and the **admin** token. The phone sends a progress summary every
6 hours while it's online, and right after each finished exam, so the first one shows up once the phone
has been connected for a while or has finished an exam. AI use shows up straight away.

## Day-to-day

| Task | Command (in `/opt/exam-app/deploy`) |
|---|---|
| Update to the latest version | `sudo git -C .. pull && sudo docker compose up -d --build` |
| Spend per day and device | `sudo docker compose exec api python usage_report.py 7` |
| Logs | `sudo docker compose logs --tail 100 api` |
| Back up the database | `sudo ./backup.sh` (keeps 30 days in `deploy/backups/`). Daily: `sudo crontab -e` → `0 3 * * * cd /opt/exam-app/deploy && ./backup.sh` |
| Block a lost phone | remove its token from `DEVICE_TOKENS` in `server/.env`, then `sudo docker compose up -d` |
| Change the admin key | new `ADMIN_TOKEN` in `server/.env`, `sudo docker compose up -d`, sign in again in the admin app |
| Change the budget or models | edit `server/.env`, then `sudo docker compose up -d` |

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `curl` to `https://…` hangs or the certificate fails | The domain doesn't point at this server yet (check with `ping tibeb.yourname.com`), or ports 80/443 are closed in the provider's firewall as well as `ufw`. `sudo docker compose logs caddy` shows why. |
| Smoke test: `Accepts this phone's token` FAIL (401) | The token doesn't match `DEVICE_TOKENS` exactly, or it's shorter than 24 characters. After editing `.env` run `sudo docker compose up -d`. |
| AI checks FAIL with `HTTP 502` | The server can't use Claude: check `ANTHROPIC_API_KEY` and that your Anthropic account has credit. `sudo docker compose logs api` has the detail. |
| AI checks FAIL with `HTTP 402` | Today's budget for that phone is used up. Raise `DAILY_BUDGET_USD` or wait for midnight (Addis Ababa time). |
| Admin app: "Admin key rejected" | Wrong `ADMIN_TOKEN`, or it's shorter than 32 characters (the admin API stays off with a weak key). |
| Admin app: "Too many attempts" | 10 wrong admin keys from one address: the admin side is locked for 15 minutes. |

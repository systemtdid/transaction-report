# Running the Transaction Report project with Docker

This stack runs **everything the project needs** in containers — you do **not**
need to install Java, Maven, MySQL, or Thai fonts on your PC. The only thing you
install once is **Docker** itself.

```
┌─────────────────────────┐        ┌──────────────────────────┐
│  txreport-app           │        │  txreport-db             │
│  Spring Boot + JRE 17   │  --->  │  MySQL 8.0               │
│  Thai fonts (Noto+TLWG) │        │  fmsv_db + sample data   │
│  http://localhost:8080  │        │  localhost:3306          │
└─────────────────────────┘        └──────────────────────────┘
```

---

## 1. Install Docker (one time)

You are on **WSL2 (Ubuntu)**. Pick one:

### Option A — Docker Desktop for Windows (recommended)
1. Download & install: https://www.docker.com/products/docker-desktop/
2. In Docker Desktop → **Settings → Resources → WSL Integration**, enable
   integration for your Ubuntu distro.
3. Reopen this terminal; `docker --version` should now work.

### Option B — Docker Engine directly inside WSL (needs sudo)
Run these in your terminal (the `!` prefix runs them in this session):

```bash
! curl -fsSL https://get.docker.com | sudo sh
! sudo usermod -aG docker $USER
! sudo service docker start
```

Then **close and reopen the terminal** (so your shell picks up the `docker`
group) and verify:

```bash
! docker --version && docker compose version
```

---

## 2. Run the stack

From the project root (the folder containing `docker-compose.yml`):

```bash
docker compose up --build
```

First run takes a few minutes (downloads base images, builds the app, loads
sample data). When you see `Started TransactionReportApplication`, open:

> **http://localhost:8080**

Pick an organization, choose **Daily** or **Monthly**, and click **Download PDF**.

Stop with `Ctrl+C`, or run detached / tear down:

```bash
docker compose up --build -d     # run in background
docker compose logs -f app       # follow app logs
docker compose down              # stop & remove containers (keeps data volume)
docker compose down -v           # also delete the database volume (fresh start)
```

---

## 3. What's inside

| Service | Image | Purpose |
|---------|-------|---------|
| `app`   | built from `Dockerfile` | Spring Boot web app + Thai PDF rendering |
| `db`    | `mysql:8.0` | `fmsv_db` with the `transactions` table + sample data |

- **Sample data** (`docker/mysql/init/01-schema-and-sample-data.sql`) is loaded
  automatically on first start, covering Jan–Jun 2026 for GSB, DHIP, KTB, TMC so
  reports are non-empty immediately. It is clearly *development* data.
- The **Noto Sans Thai** font is bundled (`docker/fonts/`) and used as the
  primary shaping font; `fonts-thai-tlwg` is also installed for broad coverage.

### Try it
- **Daily** → org *Government Saving Bank* → defaults to the current month.
- **Monthly** → org *DHIP* → defaults to the previous month. Because the sample
  data volume is below the 720,000 contractual minimum, the monthly report
  correctly shows *"Number of usage transaction has not exceeded the minimum
  limit"* and a billed fee of 0.00. Load more data to see tiered fees.

---

## 4. Configuration / overrides

All settings have working defaults baked into `docker-compose.yml`. To override,
copy `.env.example` to `.env` and edit:

```bash
cp .env.example .env
```

| Variable | Default | Meaning |
|----------|---------|---------|
| `APP_PORT` | `8080` | Host port for the web UI |
| `DB_PORT` | `3306` | Host port exposing MySQL |
| `DB_NAME` | `fmsv_db` | Database name |
| `DB_USER` / `DB_PASSWORD` | `reporting_ro` / `txreport_dev_pw` | App DB credentials |
| `MYSQL_ROOT_PASSWORD` | `rootpw_dev` | MySQL root password |

### Point at a real (external) database instead of the bundled MySQL
Remove/ignore the `db` service and set, in `.env`:

```bash
DB_HOST=your.db.host      # also set in app environment of docker-compose.yml
DB_USER=...
DB_PASSWORD=...
```

(The app reads `DB_HOST`, `DB_USER`, `DB_PASSWORD`; see `application.yml`.)

---

## 5. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Cannot connect to the Docker daemon` | Start Docker Desktop, or `sudo service docker start` (Option B). |
| `port is already allocated` | Another process uses 8080/3306. Set `APP_PORT`/`DB_PORT` in `.env`. |
| App restarts / `Could not resolve placeholder 'DB_USER'` | `DB_USER`/`DB_PASSWORD` not passed — they are set by compose; ensure you launched via `docker compose`, not `docker run`. |
| Thai text looks wrong in the PDF | Confirm the build copied `docker/fonts/NotoSansThai-Regular.ttf`; rebuild with `docker compose build --no-cache app`. |
| Changed sample SQL but data is unchanged | Init scripts run only on an empty volume. Run `docker compose down -v` then `up`. |

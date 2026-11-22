# Job Applier

Spring Boot pipeline: ingest jobs (LinkedIn / Naukri / Indeed) -> rank by resume match + recency ->
tailor resume (no invented facts) -> draft recruiter mail -> **you approve** -> apply + send.

## Run
1. `cp .env.example .env` and fill in keys (Gmail app password, Anthropic key)
2. Put your resume at `data/resumes/resume.pdf`
3. `docker compose up -d`
4. `./mvnw spring-boot:run`
   (first time, install browsers: `mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`)
5. Dashboard: `cd frontend && bun install && bun run dev` (or `npm i && npm run dev`) -> http://localhost:5173
6. Log in to each portal once in the Playwright browser window; the session is reused.

Local dev and tests run without auth. To run the hardened build, start the backend with the
`prod` profile and set credentials:

```
APP_USERNAME=you APP_PASSWORD=choose-a-strong-pass \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

The dashboard then shows a login gate and sends HTTP Basic on every `/api/**` call.

## Layout
```
src/main/java/com/jobapplier
  config/      AppProperties (prefs, limits, schedule)
  profile/     resume parsing -> CandidateProfile
  job/         Job entity, repo, dedupe hash
  ingestion/   scrapers (LinkedIn, Naukri, Indeed, Gmail alerts), IngestionService
  ranking/     scoring (keyword match x recency decay)
  llm/         LlmClient + Anthropic implementation
  tailoring/   resume tailoring -> DOCX
  outreach/    mail drafts + SMTP sending (daily cap)
  apply/       Applicator per portal, ApplyService (approved only)
  pipeline/    orchestration + schedulers
  review/      REST API for dashboard
frontend/      React review dashboard (Vite)
```

## Dashboard
A mission-control style React deck (`frontend/`): a status rail with live queue counts and the
pipeline trigger, plus a searchable application stream. Each card opens a factual resume diff
(original vs tailored) and, when a recruiter email is on the post, a two-step outreach drawer —
**approve the draft, then send**. Nothing is dispatched automatically.

## Notes
- Scrapers/applicators use Playwright selectors isolated per portal; parsing is fixture-tested.
- Scraping may violate portal ToS; keep volume low. Prefer Gmail job-alert ingestion where possible.
- Nothing is applied or mailed without explicit approval (application `APPROVED`, outreach `APPROVED`).
- Everything runs in UTC: the JVM is pinned in `JobApplierApplication`, the compose database starts with
  `timezone=UTC`, and Hikari sets `SET TIME ZONE 'UTC'` per session. If you launch from an IDE and see
  `invalid value for parameter "TimeZone": "Asia/Calcutta"`, add `-Duser.timezone=UTC` to the run configuration.

## For coding agents
Start with `AGENTS.md`, then work through `TASKS.md`. Design notes are in `docs/ARCHITECTURE.md`.

# TASKS: ordered work queue

Work top to bottom. Tick `[x]` when done. Each task has acceptance criteria (AC). Keep `mvn -q verify` green.

## 0. Make it build and boot
- [x] Run `mvn -q verify`; fix any compile errors in the skeleton.
- [x] Add Maven wrapper (`mvn -N wrapper:wrapper`) so agents/CI can use `./mvnw`.
- [x] Add Testcontainers (Postgres) + one `@SpringBootTest` context-loads test; disable schedulers in test profile.
- AC: `docker compose up -d && mvn spring-boot:run` starts; Flyway applies V1; `GET /api/applications` returns `[]`.

## 1. Resume profile
- [x] Parse PDF/DOCX with Tika; extract name, email, phone, skills, titles, years of experience.
      Use `LlmClient` with a JSON-only prompt, fall back to the keyword vocabulary if the LLM fails.
- [x] Add `GET /api/profile` returning the parsed `CandidateProfile` (without raw text).
- AC: unit tests with a sample resume fixture (`src/test/resources/fixtures/resume.txt`); LLM mocked.

## 2. Gmail job-alert ingestion (preferred, ToS-safe source)
- [x] Implement `GmailAlertIngestor` with the Gmail API (OAuth desktop flow, token stored under `data/`, gitignored).
- [x] Parse LinkedIn, Naukri and Indeed alert emails into `Job` (title, company, location, url, postedAt).
- [x] Mark processed mails (label) so they are not re-ingested.
- AC: parser tests with saved sample alert emails in `fixtures/gmail/`; dedupe works across sources.

## 3. Portal scrapers (Playwright)
For each of Naukri, LinkedIn, Indeed (in that order):
- [x] Persistent login session via `PlaywrightSessionFactory` (manual login once, reuse profile dir).
- [x] Search with `app.prefs.roles` x `locations`, newest first, limit to `maxAgeDays`.
- [x] Extract title, company, location, postedAt, url, full description, recruiter name/email if shown.
- [x] Throttle (`pause()`), cap pages per run, abort and log on CAPTCHA/login wall.
- AC: HTML parsing separated from navigation and covered by fixture-based tests; no real network in tests.

## 4. Ranking
- [x] Add embedding or LLM-based similarity (resume vs JD) behind an interface, blended with the existing
      keyword score and recency decay `exp(-days/5)`.
- [x] Hard filters: location, max age, title blacklist/whitelist from config.
- AC: deterministic unit tests for scoring; weights configurable in `AppProperties`.

## 5. Resume tailoring
- [x] Keep the original resume layout: use a DOCX template with placeholders (Apache POI) instead of plain paragraphs.
- [x] Implement the fact-validation step (hard rule 2); on failure, fall back to the original resume.
- [x] Also export PDF (LibreOffice headless or POI -> PDF) when `app.tailor.pdf=true`.
- AC: tests prove invented skills/employers are rejected.

## 6. Recruiter outreach
- [x] Extract recruiter contact from job post; never guess emails.
- [x] Draft email via LLM (under 120 words, specific to the JD, uses only resume facts).
- [x] Add statuses APPROVED/SENT/REPLIED handling; poll Gmail for replies and set `replied`.
- [x] Enforce `max-mails-per-day`; send only with `OutreachStatus.APPROVED`.
- AC: send is impossible for DRAFT items (test); cap enforced (test).

## 7. Apply automation
- [x] Implement Naukri Quick Apply, LinkedIn Easy Apply, Indeed Apply with Playwright.
- [x] Detect custom questions / external ATS / CAPTCHA -> return `false` (-> `MANUAL_REQUIRED`) with a note.
- [x] Enforce `max-applies-per-day`.
- AC: only `APPROVED` applications are processed (test).

## 8. Review dashboard (frontend/)
- [x] Mission-control UI: queue stream with per-item resume diff, editable mail draft, Approve/Skip.
- [x] Status lanes: Pending, Approved, Applied, Manual, Replied, Archive, with live counts from `GET /api/summary`.
- [x] Search across role, company and location; in-place status transitions.
- [x] Two-step outreach approval: approve the draft, then send — never auto-sent.
- [x] Auth gate that appears only when the backend enforces basic auth.
- [x] Button to trigger `POST /api/pipeline/run`.
- AC: works against the backend with CORS for `http://localhost:5173`; accessible modals/drawer (Esc, focus, labelled controls).

## 9. Tracker and follow-ups
- [x] Status transitions (APPLIED -> REPLIED -> INTERVIEW/REJECTED) editable from the dashboard.
- [x] Follow-up draft after `follow-up-after-days`, queued for approval (never auto-sent).

## 10. Hardening
- [x] Global error handling with ProblemDetail responses (400, 404, 500).
- [x] Structured logging via SLF4J; metrics enforced via `app.limits.*` rate caps.
- [x] Dockerfile + docker-compose service for the backend; GitHub Actions CI running `mvn -q verify`.
- [x] Profile-aware basic auth on `/api/**`: open in dev/test, enforced in the `prod` profile via
      `app.security.enabled` + `APP_USERNAME`/`APP_PASSWORD` env vars (no hardcoded secrets).

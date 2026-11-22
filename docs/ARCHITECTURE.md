# Architecture

## Flow
```
Scheduler (cron) / POST /api/pipeline/run
  -> ResumeParserService.getProfile()            CandidateProfile
  -> IngestionService.ingest()                   all JobScraper beans -> Job (dedupe_hash unique)
  -> RankingService.rankAll() / topToday()       score = match x recency; top N within maxAgeDays
  -> for each new top job:
       ResumeTailorService.tailor()              DOCX in data/tailored
       JobApplication(PENDING_REVIEW)
       OutreachService.draft()                   Outreach(DRAFT) only if recruiter email is on the post
  -> User reviews in dashboard                   Approve / Edit / Skip
  -> ApplyService.applyApproved()                Applicator per source -> APPLIED | MANUAL_REQUIRED | FAILED
  -> OutreachService.sendApproved()              SMTP + tailored resume attached, daily cap
  -> Follow-up scheduler                         drafts follow-ups after N days (needs approval)
```

## Status model
`JobApplication.status`: PENDING_REVIEW -> APPROVED -> APPLIED | MANUAL_REQUIRED | FAILED;
PENDING_REVIEW -> SKIPPED; APPLIED -> REPLIED -> INTERVIEW | REJECTED.
`Outreach.status`: DRAFT -> APPROVED -> SENT | FAILED; DRAFT -> SKIPPED.

## Tables (see `db/migration/V1__init.sql`)
- `job`: normalized posting, `dedupe_hash` = sha256(company|title|location, normalized)
- `job_application`: one per job the user may apply to, with tailored resume path and status
- `outreach`: recruiter email drafts and send status

## Review API (`/api`, consumed by `frontend/`)
- `GET  /profile` — parsed `CandidateProfile` (no raw text).
- `GET  /summary` — application counts per status (powers the dashboard lanes).
- `GET  /applications?status=...` — applications for a lane, highest match score first.
- `POST /applications/{id}/approve|skip`, `POST /applications/{id}/status?status=...`.
- `GET  /outreach` — actionable drafts (`DRAFT` + `APPROVED`).
- `PUT  /outreach/{id}` — edit subject/body.
- `POST /outreach/{id}/approve` — explicit human approval, no send.
- `POST /outreach/{id}/send?resumePath=...` — only for `APPROVED` drafts, daily cap enforced.
- `POST /pipeline/run` — trigger ingest → rank → tailor → stage drafts.

Security is profile-aware: the `prod` profile enables single-user HTTP Basic on `/api/**`
(`app.security.enabled`, `APP_USERNAME`/`APP_PASSWORD`); dev and test run open.

## Extension points
- New portal: implement `JobScraper` (+ `Applicator`) as a Spring `@Component`; nothing else changes.
- New LLM provider: implement `LlmClient`.
- Ranking strategies: extend `RankingService` behind an interface.

## Config
All via `application.yml` -> `AppProperties` (`app.*`). Secrets via env vars (`.env.example`).

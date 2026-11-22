# AGENTS.md: instructions for any coding agent

Read this file first, then `TASKS.md` (work queue) and `docs/ARCHITECTURE.md` (design).

## Project
Job Applier: a Spring Boot app that ingests recent jobs from LinkedIn, Naukri and Indeed, ranks them against the
user's resume, tailors the resume per job, drafts recruiter emails, and applies or sends **only after the user
approves** in a React dashboard.

## Stack
Java 17, Spring Boot 3.3.x, Maven, Spring Data JPA, PostgreSQL 16, Flyway, Playwright for Java,
Apache Tika (resume parsing), Apache POI (DOCX), Spring Mail (SMTP), React 18 + Vite (frontend/).
Base package: `com.jobapplier`.

## Commands
| Action | Command |
|---|---|
| Start DB | `docker compose up -d` |
| Build + tests | `mvn -q verify` |
| Run backend | `mvn spring-boot:run` |
| Install Playwright browsers (once) | `mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"` |
| Frontend | `cd frontend && npm i && npm run dev` |

Always run `mvn -q verify` before declaring a task done.

## Hard rules (do not violate)
1. **Human approval gate.** No application submission and no recruiter email may happen without an explicit
   approval state (`ApplicationStatus.APPROVED`, outreach approved). Never bypass or auto-approve.
2. **No invented resume facts.** Tailoring may reorder and rephrase only what exists in the resume. Keep the guardrail
   in `ResumeTailorService.SYSTEM`, and add a validation step that rejects tailored output containing skills,
   employers or numbers not in the source resume.
3. **Never guess or scrape recruiter email addresses.** Only use emails present on the job post or provided by the user.
4. **Respect rate caps** in `app.limits.*` and add random delays via `PlaywrightSessionFactory.pause()`.
   Stop a portal's run on CAPTCHA/login wall and log it. Never try to bypass CAPTCHAs or bot detection.
5. **No secrets in code or git.** Use env vars (`.env.example` lists them). Never commit resumes or `data/` contents.
6. Schema changes go through new Flyway files `V<n>__desc.sql`; never edit applied migrations.
   `spring.jpa.hibernate.ddl-auto` stays `validate`.
7. Scraping may violate portal ToS: keep volumes low and keep Gmail job-alert ingestion as the preferred, safer path.

## Conventions
- Constructor injection via Lombok `@RequiredArgsConstructor`; no field `@Autowired`.
- Config comes only through `AppProperties`; do not hardcode values.
- Each portal implements `JobScraper` and `Applicator`; add new portals by adding beans, not by editing the services.
- Every scraper/applicator must fail soft: catch, log, return empty or `false` (-> `MANUAL_REQUIRED`).
- Keep scraping logic (selectors) isolated per portal class; parsing helpers must be unit-testable with saved HTML fixtures
  in `src/test/resources/fixtures/<portal>/`.
- Tests: JUnit 5; use Testcontainers (Postgres) for repository/integration tests; never hit real portals or the real
  LLM in tests (mock `LlmClient`).
- Small focused commits; update `TASKS.md` checkboxes as you finish items.

## Definition of done (per task)
Compiles, `mvn -q verify` passes, tests added for new logic, README/TASKS updated, hard rules above still hold.

## Known state
The initial skeleton was generated without being compiled. **Task 0 in TASKS.md is to make it build and boot.**
Scrapers, applicators and the Gmail ingestor are stubs marked `TODO`.

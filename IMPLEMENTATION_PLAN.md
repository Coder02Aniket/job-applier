# Implementation Plan: Job Applier

## 1. Executive Summary & Ground Truth Analysis

### 1.1 Context & Current State
The Job Applier is a Spring Boot 3.3.x and React 18 application designed to automate job discovery, match ranking, resume tailoring, recruiter outreach, and application submissions across LinkedIn, Naukri, and Indeed. 

**Current Codebase Verification:**
- The Java skeleton compiles successfully (`mvn compile` passed, 34 source files).
- The baseline test suite (`mvn test`) executes cleanly (`JobNormalizerTest` passes).
- Core abstractions (`JobScraper`, `Applicator`, `LlmClient`, entities, repositories) are structurally defined.
- Scrapers, applicators, Gmail ingestors, and LLM integrations are currently stubbed.
- The previous implementation plan is obsolete and does not address the actual engineering steps, verification gates, or hard constraints.

### 1.2 Non-Negotiable Hard Rules
1. **Strict Human Approval Gate:** No job application submission and no outreach email may ever occur without explicit user approval (`ApplicationStatus.APPROVED`, `OutreachStatus.APPROVED`).
2. **Zero Hallucinated Resume Facts:** Resume tailoring must never invent skills, metrics, dates, or employers. A strict programmatic verification step must validate all tailored content against the candidate profile before storage.
3. **No Email Guessing/Scraping:** Outbound emails may only target recruiter emails explicitly present on the job listing or entered manually by the user.
4. **Rate Caps & Fail-Soft Scraping:** Enforce limits defined in `AppProperties.limits`. Stop portal runs immediately upon encountering CAPTCHA, Cloudflare, or login walls without attempting circumvention. Mark jobs as `MANUAL_REQUIRED`.
5. **Security & Clean Migrations:** Zero committed secrets; all credentials via environment variables. Flyway migrations are append-only (`V2__...`).

---

## 2. Phase-by-Phase Implementation Roadmap

```
Phase 1: Build & Test Infrastructure (Testcontainers, Maven Wrapper)
   │
Phase 2: Resume Intelligence & Factual Verification Engine
   │
Phase 3: Safe Gmail Alert Ingestion (Preferred Ingest Source)
   │
Phase 4: Multi-Factor Job Ranking & Candidate Matching
   │
Phase 5: Recruiter Outreach & Follow-up Lifecycle
   │
Phase 6: Playwright Scraping & Application Automation (Fail-soft)
   │
Phase 7: Modern Review Dashboard (React Frontend + Diff/Editor)
   │
Phase 8: Production Hardening, Security & CI/CD
```

---

### Phase 1: Build, Test Infrastructure & Database Validation
**Goal:** Establish reproducible builds, isolated integration testing, and verify migration integrity.

#### Tasks:
1. **Maven Wrapper Setup:**
   - Generate Maven wrapper (`mvn -N wrapper:wrapper`) to provide `mvnw` and `mvnw.cmd`.
2. **Testcontainers Integration:**
   - Add `testcontainers` and `junit-jupiter` PostgreSQL container dependencies to `pom.xml`.
   - Create `AbstractIntegrationTest` with dynamic property configuration (`@ServiceConnection` or `@DynamicPropertySource`) pointing to a temporary PostgreSQL container.
   - Implement `@SpringBootTest` context load test verifying Flyway `V1__init.sql` execution and all JPA repositories.
   - Configure `application-test.yml` disabling pipeline schedulers (`@Scheduled` tasks disabled in tests).
3. **Verification Gate:**
   - `mvn verify` runs cleanly without requiring an external PostgreSQL instance running on localhost.

---

### Phase 2: Resume Intelligence & Anti-Hallucination Tailoring
**Goal:** Ingest candidate background and implement document tailoring with strict factual guardrails.

#### Tasks:
1. **Resume Ingestion (`ResumeParserService`):**
   - Extract raw text from PDF and DOCX files using Apache Tika.
   - Define structured `CandidateProfile`: name, contact, skills (normalized set), target roles, work history (company, title, dates, bullet achievements), education, and raw text.
   - Integrate `LlmClient` to parse extracted text into the structured profile, with fallback to vocabulary heuristics if LLM parsing fails.
   - Expose `GET /api/profile` returning structured candidate information (omitting sensitive raw dump).
2. **Document Tailoring Engine (`ResumeTailorService`):**
   - Implement template-aware DOCX rewriting using Apache POI:
     - Preserve typography, font hierarchies, headings, and bullet styles from a base resume template (`data/templates/resume-template.docx`).
     - Reorganize sections and rephrase bullet points to emphasize competencies aligned with the target Job Description.
     - Optional PDF generation (LibreOffice CLI or POI/PDFBox bridge) controlled by `app.tailor.pdf`.
3. **Anti-Hallucination Guardrail (`ResumeFactualValidator`):**
   - Implement validation algorithm comparing tailored output against `CandidateProfile`:
     - Token/Entity extraction (employers, dates, numbers/metrics, technical skills).
     - If any entity or metric in the tailored text is missing from the original resume, reject tailoring.
     - Fallback: On validation failure, log a warning and use the original unmodified resume.
4. **Verification Gate:**
   - Unit tests with mock resumes verifying that synthetic/unapproved skills are rejected and valid reorganizations pass.

---

### Phase 3: Safe Job Ingestion (Gmail Alert Ingestor)
**Goal:** Implement the primary, ToS-compliant source for job postings via email job alerts.

#### Tasks:
1. **Gmail API OAuth Integration:**
   - Configure Google API client library with OAuth2 desktop flow.
   - Store access/refresh tokens in `data/tokens/gmail.json` (gitignored).
   - Handle token expiration and automatic refreshing.
2. **Alert Parsing (`GmailAlertIngestor`):**
   - Poll Gmail inbox using targeted query: `from:(linkedin.com OR naukri.com OR indeed.com) is:unread`.
   - Parse MIME message bodies (multipart HTML/plain text).
   - Implement portal-specific regex/DOM extractors:
     - **LinkedIn Alerts:** extract job title, company name, location, and canonical direct job URL (stripping tracking query parameters).
     - **Naukri Alerts:** extract role, hiring organization, experience band, location, and apply link.
     - **Indeed Alerts:** extract job title, employer, city/state/remote, and redirect link.
3. **Normalization & Deduplication:**
   - Clean strings via `JobNormalizer`.
   - Compute deterministic `dedupe_hash = sha256(company|title|location)`.
   - Persist to `job` table; ignore duplicate hash collisions cleanly.
   - Apply Gmail label `JobApplier/Processed` to prevent reprocessing.
4. **Verification Gate:**
   - Unit tests covering saved HTML fixtures from LinkedIn, Naukri, and Indeed alert emails (`src/test/resources/fixtures/gmail/`).

---

### Phase 4: Multi-Factor Job Ranking & Matching
**Goal:** Prioritize high-signal opportunities matching candidate preferences and recency.

#### Tasks:
1. **Hard Filtering Rules:**
   - Location filtering: match candidate `locations` (including "Remote" semantics).
   - Recency filter: drop jobs older than `app.prefs.maxAgeDays`.
   - Title exclusion: filter out blacklisted keywords (e.g., "Senior Lead", "Architect", "Intern" depending on prefs).
2. **Scoring Model (`RankingService`):**
   - **Keyword Match Score (0.0 to 1.0):** Jaccard/cosine similarity over required skills vs candidate skills.
   - **Semantic LLM/Embedding Match Score (0.0 to 1.0):** Semantic relevance of job description against candidate experience summary.
   - **Recency Decay Factor:** `decay = exp(-daysOld / 5.0)`.
   - **Composite Formula:** `score = (w_keyword * score_kw + w_semantic * score_sem) * decay`.
3. **Queue Selection:**
   - Rank unapplied jobs; select top `app.limits.topNPerDay` exceeding `app.prefs.minScore`.
   - Create `JobApplication` entries in `PENDING_REVIEW` state.
4. **Verification Gate:**
   - Unit tests validating score ordering, weight configuration, and hard filter boundaries.

---

### Phase 5: Recruiter Outreach & Follow-Up Lifecycle
**Goal:** Draft and send tailored recruiter emails with complete human oversight and reply tracking.

#### Tasks:
1. **Recruiter Contact Discovery:**
   - Parse recruiter name and email only when explicitly available on the posting.
   - Never generate, guess, or synthesize email addresses.
2. **Outreach Drafting (`OutreachService`):**
   - Prompt LLM for high-impact, concise cold outreach:
     - Under 120 words.
     - Specific to the target role and company.
     - Grounded strictly in validated resume achievements.
   - Create `Outreach` record in `DRAFT` status.
3. **Human Approval & SMTP Dispatch:**
   - Expose endpoints to edit subject/body, approve, and send.
   - `sendApproved()` verifies `status == OutreachStatus.APPROVED` before invoking JavaMailSender.
   - Attach the tailored resume DOCX/PDF.
   - Enforce daily limit: `app.limits.maxMailsPerDay`.
4. **Follow-Up System (`FollowUpScheduler`):**
   - Check applications in `APPLIED` state where `appliedAt` >= `app.limits.followUpAfterDays` and no reply detected.
   - Draft polite follow-up in `Outreach` with status `DRAFT` (requires explicit user approval).
   - Optional: Poll Gmail for incoming replies from the recruiter and transition status to `REPLIED`.
5. **Verification Gate:**
   - Integration tests ensuring emails cannot be sent when `status != APPROVED` and daily limits block excess sends.

---

### Phase 6: Direct Portal Scraping & Application Automation (Playwright)
**Goal:** Complement Gmail alerts with portal browsing and automate repetitive "Easy Apply" flows safely.

#### Tasks:
1. **Persistent Browser Session Management (`PlaywrightSessionFactory`):**
   - Persistent user data directory under `data/playwright-session/{portal}` to preserve cookies, sessions, and multi-factor auth across runs.
   - Random jitter delay injection: `Thread.sleep(randomBetween(delayMsMin, delayMsMax))`.
2. **Portal Scrapers (`JobScraper` implementations):**
   - `LinkedInScraper`: Search role + location, filter by past 24h / week, scrape job cards and descriptions.
   - `NaukriScraper`: Search criteria, parse job tiles and detail view.
   - `IndeedScraper`: Search query, extract job listings.
   - **Bot Detection & Fail-Soft:** Detect CAPTCHA, Cloudflare challenges, or login prompts -> immediately abort the portal's run, log the event, and avoid account restrictions.
3. **Automated Application (`Applicator` implementations):**
   - Support standard flows: LinkedIn Easy Apply, Naukri Quick Apply, Indeed Apply.
   - Pre-fill standard candidate fields: name, phone, resume upload.
   - **Complex Question Handling:** If an application asks custom questions, external ATS redirects, or complex questionnaires -> mark `ApplicationStatus.MANUAL_REQUIRED` with descriptive notes.
   - Strictly check that `status == ApplicationStatus.APPROVED` before invoking any applicator.
   - Enforce daily limit: `app.limits.maxAppliesPerDay`.
4. **Verification Gate:**
   - Mocked HTML fixtures for selector parsing tests; test that applicator stops on unapproved applications.

---

### Phase 7: Review Dashboard (Frontend UI)
**Goal:** Provide an ergonomic control panel for approving applications, editing outreach, and monitoring pipelines.

#### Tasks:
1. **Queue Management & Filtering:**
   - Tabs: `Pending Review`, `Approved`, `Applied`, `Manual Required`, `Outreach Drafts`, `Archived/Skipped`.
   - Search by role, company, match score, or source.
2. **Resume Diff Viewer:**
   - Split-view or unified diff comparing original candidate resume text vs tailored resume text.
   - Highlight rephrased sections and reordered skills.
3. **Outreach Email Editor:**
   - In-place rich text/plain text editor for outreach subject and body.
   - Recruiter information display and direct "Approve & Send" action.
4. **Pipeline Controller & Observability:**
   - Trigger manual pipeline execution (`POST /api/pipeline/run`).
   - Real-time status indicators, daily apply/email count meters against daily caps.
5. **Verification Gate:**
   - End-to-end user flow: view queue -> inspect diff -> edit outreach draft -> approve application -> observe status transition.

---

### Phase 8: Production Hardening, Security & Deployment
**Goal:** Secure the application and prepare for dependable local or self-hosted deployment.

#### Tasks:
1. **Security & Authentication:**
   - Add Spring Security with single-user HTTP Basic Auth or session auth for `/api/**`.
   - Prevent unauthorized local network access to application controls.
2. **Observability & Error Handling:**
   - Global `@RestControllerAdvice` returning structured problem details (RFC 7807).
   - Log pipeline metrics: ingestion counts, match rate distribution, applicator outcomes.
3. **Containerization & CI:**
   - Multi-stage `Dockerfile` for Spring Boot backend and Vite frontend build.
   - Update `docker-compose.yml` to orchestrate PostgreSQL and application containers.
   - GitHub Actions workflow running `mvn -q verify` with Testcontainers.

---

## 3. Immediate Next Steps & Execution Order
1. Complete **Phase 1**: Add Maven wrapper, configure Testcontainers for PostgreSQL, and add test profile.
2. Complete **Phase 2**: Implement resume parsing and POI tailoring with anti-hallucination validation.
3. Progress sequentially through Phases 3 through 8.

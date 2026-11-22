CREATE TABLE job (
  id BIGSERIAL PRIMARY KEY,
  source VARCHAR(20) NOT NULL,
  external_id VARCHAR(255),
  title VARCHAR(500) NOT NULL,
  company VARCHAR(255) NOT NULL,
  location VARCHAR(255),
  posted_at TIMESTAMPTZ,
  url TEXT NOT NULL,
  description TEXT,
  recruiter_name VARCHAR(255),
  recruiter_email VARCHAR(255),
  dedupe_hash VARCHAR(64) NOT NULL UNIQUE,
  score DOUBLE PRECISION DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_job_posted_at ON job(posted_at DESC);

CREATE TABLE job_application (
  id BIGSERIAL PRIMARY KEY,
  job_id BIGINT NOT NULL REFERENCES job(id),
  status VARCHAR(30) NOT NULL,
  resume_path TEXT,
  applied_at TIMESTAMPTZ,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outreach (
  id BIGSERIAL PRIMARY KEY,
  job_id BIGINT NOT NULL REFERENCES job(id),
  recipient VARCHAR(255),
  subject VARCHAR(500),
  body TEXT,
  status VARCHAR(30) NOT NULL,
  sent_at TIMESTAMPTZ,
  replied BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

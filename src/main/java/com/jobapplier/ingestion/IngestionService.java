package com.jobapplier.ingestion;

import com.jobapplier.job.Job;
import com.jobapplier.job.JobNormalizer;
import com.jobapplier.job.JobRepository;
import com.jobapplier.profile.CandidateProfile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {
    private final List<JobScraper> scrapers;
    private final JobRepository jobs;

    public int ingest(CandidateProfile profile) {
        int saved = 0;
        for (JobScraper s : scrapers) {
            try {
                for (Job j : s.fetch(profile)) {
                    j.setDedupeHash(JobNormalizer.dedupeHash(j.getCompany(), j.getTitle(), j.getLocation()));
                    if (jobs.findByDedupeHash(j.getDedupeHash()).isEmpty()) { jobs.save(j); saved++; }
                }
            } catch (Exception e) {
                log.warn("Scraper {} failed: {}", s.source(), e.getMessage());
            }
        }
        log.info("Ingested {} new jobs", saved);
        return saved;
    }
}

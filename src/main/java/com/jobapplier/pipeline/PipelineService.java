package com.jobapplier.pipeline;

import com.jobapplier.apply.ApplicationStatus;
import com.jobapplier.apply.JobApplication;
import com.jobapplier.apply.JobApplicationRepository;
import com.jobapplier.ingestion.IngestionService;
import com.jobapplier.job.Job;
import com.jobapplier.outreach.OutreachService;
import com.jobapplier.profile.CandidateProfile;
import com.jobapplier.profile.ResumeParserService;
import com.jobapplier.ranking.RankingService;
import com.jobapplier.tailoring.ResumeTailorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** ingest -> rank -> tailor resume + draft mail -> PENDING_REVIEW (human approves next). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {
    private final ResumeParserService parser;
    private final IngestionService ingestion;
    private final RankingService ranking;
    private final ResumeTailorService tailor;
    private final OutreachService outreach;
    private final JobApplicationRepository applications;

    public void run() {
        CandidateProfile profile = parser.getProfile();
        ingestion.ingest(profile);
        ranking.rankAll(profile);
        for (Job job : ranking.topToday()) {
            if (applications.existsByJobId(job.getId())) continue;
            JobApplication a = new JobApplication();
            a.setJob(job);
            a.setResumePath(tailor.tailor(profile, job).toString());
            a.setStatus(ApplicationStatus.PENDING_REVIEW);
            applications.save(a);
            outreach.draft(job, profile);
        }
        log.info("Pipeline run complete");
    }
}

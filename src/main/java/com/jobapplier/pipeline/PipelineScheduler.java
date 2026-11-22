package com.jobapplier.pipeline;

import com.jobapplier.apply.ApplicationStatus;
import com.jobapplier.apply.ApplyService;
import com.jobapplier.apply.JobApplication;
import com.jobapplier.apply.JobApplicationRepository;
import com.jobapplier.config.AppProperties;
import com.jobapplier.llm.LlmClient;
import com.jobapplier.outreach.Outreach;
import com.jobapplier.outreach.OutreachRepository;
import com.jobapplier.outreach.OutreachStatus;
import com.jobapplier.profile.CandidateProfile;
import com.jobapplier.profile.ResumeParserService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineScheduler {

    private static final String FOLLOW_UP_SYSTEM = """
        Write a short (under 80 words), courteous follow-up email inquiring about the status of a job application.
        Reference the candidate name, role, and company. Do not invent any new facts or claims.""";

    private final PipelineService pipeline;
    private final ApplyService applyService;
    private final JobApplicationRepository applications;
    private final OutreachRepository outreachRepo;
    private final ResumeParserService resumeParser;
    private final LlmClient llm;
    private final AppProperties props;

    @Scheduled(cron = "${app.schedule.ingest-cron}")
    public void ingestAndPrepare() {
        pipeline.run();
        applyService.applyApproved();
    }

    @Scheduled(cron = "${app.schedule.follow-up-cron}")
    public void followUps() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(props.limits().followUpAfterDays()));
        List<JobApplication> dueApplications = applications.findByStatusAndAppliedAtBefore(ApplicationStatus.APPLIED, cutoff);

        CandidateProfile profile = resumeParser.getProfile();

        for (JobApplication a : dueApplications) {
            if (a.getJob() == null || a.getJob().getRecruiterEmail() == null) {
                continue;
            }

            // Check if follow-up draft already exists for this job
            boolean exists = outreachRepo.findByStatus(OutreachStatus.DRAFT).stream()
                .anyMatch(o -> o.getJob() != null && o.getJob().getId().equals(a.getJob().getId()));

            if (exists) continue;

            log.info("Generating follow-up draft for: {} @ {}", a.getJob().getTitle(), a.getJob().getCompany());
            String userPrompt = "CANDIDATE: " + (profile != null ? profile.name() : "Applicant") +
                                "\nJOB: " + a.getJob().getTitle() + " at " + a.getJob().getCompany();

            String followUpBody;
            try {
                followUpBody = llm.complete(FOLLOW_UP_SYSTEM, userPrompt);
            } catch (Exception e) {
                followUpBody = "Hello,\n\nI wanted to respectfully follow up regarding my application for the " +
                               a.getJob().getTitle() + " position. Please let me know if you need any additional information.\n\nBest regards,\n" +
                               (profile != null ? profile.name() : "");
            }

            Outreach followUp = new Outreach();
            followUp.setJob(a.getJob());
            followUp.setRecipient(a.getJob().getRecruiterEmail());
            followUp.setSubject("Follow-up: Application for " + a.getJob().getTitle());
            followUp.setBody(followUpBody);
            followUp.setStatus(OutreachStatus.DRAFT); // NEVER auto-sent; placed into review queue
            outreachRepo.save(followUp);
        }
    }
}

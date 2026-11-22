package com.jobapplier.outreach;

import com.jobapplier.config.AppProperties;
import com.jobapplier.job.Job;
import com.jobapplier.llm.LlmClient;
import com.jobapplier.profile.CandidateProfile;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutreachService {

    private static final String DRAFT_SYSTEM_PROMPT = """
        Write a short (under 120 words), professional, highly specific recruiter email.
        HARD RULES: Use ONLY facts from the candidate resume. Never invent experience, skills or metrics.
        Keep it direct, polite, concise.""";

    private final OutreachRepository repo;
    private final LlmClient llm;
    private final JavaMailSender mailSender;
    private final AppProperties props;

    /** Draft only. Nothing is sent until the user approves in the dashboard. */
    public Outreach draft(Job job, CandidateProfile profile) {
        if (job == null || job.getRecruiterEmail() == null || job.getRecruiterEmail().isBlank()) {
            log.debug("No recruiter email present on job id={}. Skipping outreach draft.", job != null ? job.getId() : null);
            return null; // Never guess emails
        }

        String userPrompt = "RESUME:\n" + profile.rawText() + "\n\nJOB: " + job.getTitle() + " @ " + job.getCompany() + "\n" + job.getDescription();
        String body;
        try {
            body = llm.complete(DRAFT_SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            log.warn("LLM draft generation failed for job id={}: {}. Using template fallback.", job.getId(), e.getMessage());
            body = "Hello,\n\nI am writing to express my interest in the " + job.getTitle() + " position at " + job.getCompany() +
                   ". My background in " + String.join(", ", profile.skills().subList(0, Math.min(3, profile.skills().size()))) +
                   " aligns well with your requirements. Please find my resume attached.\n\nBest regards,\n" + (profile.name() != null ? profile.name() : "");
        }

        Outreach o = new Outreach();
        o.setJob(job);
        o.setRecipient(job.getRecruiterEmail());
        o.setSubject("Application: " + job.getTitle() + " - " + (profile.name() != null ? profile.name() : "Candidate"));
        o.setBody(body);
        o.setStatus(OutreachStatus.DRAFT);
        return repo.save(o);
    }

    public void sendApproved(Long id, Path resume) throws Exception {
        Outreach o = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Outreach draft not found: " + id));

        // Hard Rule 1: Must be in APPROVED status
        if (o.getStatus() != OutreachStatus.APPROVED) {
            log.error("Attempted to send unapproved outreach draft id={} with status={}", id, o.getStatus());
            throw new IllegalStateException("Cannot send outreach draft that is not in APPROVED status. Current status: " + o.getStatus());
        }

        // Hard Rule 4: Respect daily rate caps
        int maxMails = props.limits() != null ? props.limits().maxMailsPerDay() : 12;
        long todayCount = repo.countByStatusAndSentAtAfter(OutreachStatus.SENT, Instant.now().minus(Duration.ofDays(1)));
        if (todayCount >= maxMails) {
            log.warn("Daily mail cap reached ({} / max {}). Blocking outreach send.", todayCount, maxMails);
            throw new IllegalStateException("Daily mail limit reached (" + maxMails + " mails/day). Try again tomorrow.");
        }

        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, true);
        h.setTo(o.getRecipient());
        h.setSubject(o.getSubject());
        h.setText(o.getBody());

        if (resume != null && resume.toFile().exists()) {
            h.addAttachment(resume.getFileName().toString(), new FileSystemResource(resume));
        }

        mailSender.send(msg);
        o.setStatus(OutreachStatus.SENT);
        o.setSentAt(Instant.now());
        repo.save(o);
        log.info("Successfully sent outreach email to '{}' for job '{}'", o.getRecipient(), o.getJob().getTitle());
    }
}

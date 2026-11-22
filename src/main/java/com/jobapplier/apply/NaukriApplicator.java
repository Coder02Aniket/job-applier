package com.jobapplier.apply;

import com.jobapplier.ingestion.PlaywrightSessionFactory;
import com.jobapplier.job.Job;
import com.jobapplier.job.JobSource;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaukriApplicator implements Applicator {

    private final PlaywrightSessionFactory sessions;

    @Override
    public JobSource source() {
        return JobSource.NAUKRI;
    }

    @Override
    public boolean apply(JobApplication application) {
        Job job = application.getJob();
        if (job == null || job.getUrl() == null) {
            application.setNotes("Invalid job URL");
            return false;
        }

        try (Playwright pw = sessions.create()) {
            BrowserContext context = sessions.open(pw, "naukri");
            Page page = context.newPage();

            page.navigate(job.getUrl());
            sessions.pause();

            String content = page.content().toLowerCase();
            if (content.contains("security check") || content.contains("captcha") || content.contains("access denied")) {
                application.setNotes("Naukri security check or CAPTCHA encountered");
                log.warn("Naukri security check/CAPTCHA encountered for job id={}", job.getId());
                return false;
            }

            // Look for "Apply" or "Quick Apply" button
            ElementHandle applyBtn = page.querySelector("button#apply-button, .apply-button, button:has-text('Apply')");
            if (applyBtn == null) {
                application.setNotes("No quick apply button found on Naukri posting");
                return false;
            }

            applyBtn.click();
            sessions.pause();

            // Check for file upload or questionnaire modal
            ElementHandle fileInput = page.querySelector("input[type=file]");
            if (fileInput != null && application.getResumePath() != null) {
                Path resumePath = Path.of(application.getResumePath());
                if (resumePath.toFile().exists()) {
                    fileInput.setInputFiles(resumePath);
                    sessions.pause();
                }
            }

            // Check if questionnaires/custom questions appeared
            if (page.querySelector("form.questionnaire, .custom-questions") != null) {
                application.setNotes("Naukri custom questionnaire required");
                return false;
            }

            log.info("Successfully submitted Naukri Quick Apply for job id={}", job.getId());
            return true;
        } catch (Exception e) {
            log.error("Naukri Quick Apply failed for job id={}: {}", job.getId(), e.getMessage());
            application.setNotes("Playwright application error: " + e.getMessage());
            return false;
        }
    }
}

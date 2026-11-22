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
public class LinkedInApplicator implements Applicator {

    private final PlaywrightSessionFactory sessions;

    @Override
    public JobSource source() {
        return JobSource.LINKEDIN;
    }

    @Override
    public boolean apply(JobApplication application) {
        Job job = application.getJob();
        if (job == null || job.getUrl() == null) {
            application.setNotes("Invalid job URL");
            return false;
        }

        try (Playwright pw = sessions.create()) {
            BrowserContext context = sessions.open(pw, "linkedin");
            Page page = context.newPage();

            page.navigate(job.getUrl());
            sessions.pause();

            String content = page.content().toLowerCase();
            if (content.contains("authwall") || content.contains("captcha") || content.contains("security verification")) {
                application.setNotes("LinkedIn login wall or CAPTCHA encountered");
                log.warn("LinkedIn login wall/CAPTCHA encountered for job id={}", job.getId());
                return false;
            }

            // Look for "Easy Apply" button
            ElementHandle easyApplyBtn = page.querySelector("button.jobs-apply-button");
            if (easyApplyBtn == null) {
                application.setNotes("No Easy Apply button found (external company site or already applied)");
                return false;
            }

            easyApplyBtn.click();
            sessions.pause();

            // Check if modal popped up
            ElementHandle fileInput = page.querySelector("input[type=file]");
            if (fileInput != null && application.getResumePath() != null) {
                Path resumePath = Path.of(application.getResumePath());
                if (resumePath.toFile().exists()) {
                    fileInput.setInputFiles(resumePath);
                    sessions.pause();
                }
            }

            // Check if there are complex question steps
            ElementHandle nextOrSubmitBtn = page.querySelector("button[aria-label*='Submit'], button[aria-label*='Next']");
            if (nextOrSubmitBtn == null) {
                application.setNotes("Easy Apply flow contains complex questionnaire or external step");
                return false;
            }

            String btnText = nextOrSubmitBtn.innerText().toLowerCase();
            if (btnText.contains("next")) {
                // Multi-step custom question form -> requires manual review
                application.setNotes("Multi-step custom questionnaire required");
                return false;
            }

            // Click submit
            nextOrSubmitBtn.click();
            sessions.pause();

            log.info("Successfully submitted LinkedIn Easy Apply for job id={}", job.getId());
            return true;
        } catch (Exception e) {
            log.error("LinkedIn Easy Apply failed for job id={}: {}", job.getId(), e.getMessage());
            application.setNotes("Playwright application error: " + e.getMessage());
            return false;
        }
    }
}

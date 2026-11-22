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
public class IndeedApplicator implements Applicator {

    private final PlaywrightSessionFactory sessions;

    @Override
    public JobSource source() {
        return JobSource.INDEED;
    }

    @Override
    public boolean apply(JobApplication application) {
        Job job = application.getJob();
        if (job == null || job.getUrl() == null) {
            application.setNotes("Invalid job URL");
            return false;
        }

        try (Playwright pw = sessions.create()) {
            BrowserContext context = sessions.open(pw, "indeed");
            Page page = context.newPage();

            page.navigate(job.getUrl());
            sessions.pause();

            String content = page.content().toLowerCase();
            if (content.contains("cloudflare") || content.contains("cf-challenge") || content.contains("captcha")) {
                application.setNotes("Indeed Cloudflare challenge or CAPTCHA encountered");
                log.warn("Indeed Cloudflare/CAPTCHA encountered for job id={}", job.getId());
                return false;
            }

            // Look for "Indeed Apply" or "Apply now" button
            ElementHandle applyBtn = page.querySelector("#indeedApplyButton, button:has-text('Apply now'), .indeed-apply-button");
            if (applyBtn == null) {
                application.setNotes("No Indeed Apply button found (external company site redirect)");
                return false;
            }

            applyBtn.click();
            sessions.pause();

            // Check if file upload or questionnaire iframe/modal appeared
            ElementHandle fileInput = page.querySelector("input[type=file]");
            if (fileInput != null && application.getResumePath() != null) {
                Path resumePath = Path.of(application.getResumePath());
                if (resumePath.toFile().exists()) {
                    fileInput.setInputFiles(resumePath);
                    sessions.pause();
                }
            }

            if (page.querySelector("div.ia-Questions, form.ia-Form") != null) {
                application.setNotes("Indeed custom question form required");
                return false;
            }

            log.info("Successfully submitted Indeed Apply for job id={}", job.getId());
            return true;
        } catch (Exception e) {
            log.error("Indeed Apply failed for job id={}: {}", job.getId(), e.getMessage());
            application.setNotes("Playwright application error: " + e.getMessage());
            return false;
        }
    }
}

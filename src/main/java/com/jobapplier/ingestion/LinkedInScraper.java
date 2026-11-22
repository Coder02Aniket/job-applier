package com.jobapplier.ingestion;

import com.jobapplier.config.AppProperties;
import com.jobapplier.job.Job;
import com.jobapplier.job.JobSource;
import com.jobapplier.profile.CandidateProfile;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkedInScraper implements JobScraper {

    private final PlaywrightSessionFactory sessions;
    private final AppProperties props;
    private final LinkedInHtmlParser parser;

    @Override
    public JobSource source() {
        return JobSource.LINKEDIN;
    }

    @Override
    public List<Job> fetch(CandidateProfile profile) {
        List<Job> allJobs = new ArrayList<>();
        List<String> roles = props.prefs().roles();
        List<String> locations = props.prefs().locations();

        if (roles == null || roles.isEmpty()) return allJobs;

        try (Playwright pw = sessions.create()) {
            BrowserContext context = sessions.open(pw, "linkedin");
            Page page = context.newPage();

            for (String role : roles) {
                for (String loc : locations) {
                    try {
                        String url = "https://www.linkedin.com/jobs/search/?keywords="
                            + URLEncoder.encode(role, StandardCharsets.UTF_8)
                            + "&location=" + URLEncoder.encode(loc, StandardCharsets.UTF_8)
                            + "&f_TPR=r604800"; // past 7 days

                        page.navigate(url);
                        sessions.pause();

                        String html = page.content();
                        if (parser.isBotOrLoginWall(html)) {
                            log.warn("LinkedIn authwall/CAPTCHA detected. Aborting LinkedIn scrape.");
                            return allJobs;
                        }

                        List<Job> scraped = parser.parse(html, Instant.now());
                        allJobs.addAll(scraped);
                        log.info("LinkedIn scraped {} jobs for role='{}', loc='{}'", scraped.size(), role, loc);
                    } catch (Exception e) {
                        log.error("Failed scraping LinkedIn for role='{}', loc='{}': {}", role, loc, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error opening LinkedIn Playwright session: {}", e.getMessage(), e);
        }

        return allJobs;
    }
}

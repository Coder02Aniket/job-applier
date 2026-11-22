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
public class IndeedScraper implements JobScraper {

    private final PlaywrightSessionFactory sessions;
    private final AppProperties props;
    private final IndeedHtmlParser parser;

    @Override
    public JobSource source() {
        return JobSource.INDEED;
    }

    @Override
    public List<Job> fetch(CandidateProfile profile) {
        List<Job> allJobs = new ArrayList<>();
        List<String> roles = props.prefs().roles();
        List<String> locations = props.prefs().locations();

        if (roles == null || roles.isEmpty()) return allJobs;

        try (Playwright pw = sessions.create()) {
            BrowserContext context = sessions.open(pw, "indeed");
            Page page = context.newPage();

            for (String role : roles) {
                for (String loc : locations) {
                    try {
                        String url = "https://www.indeed.com/jobs?q="
                            + URLEncoder.encode(role, StandardCharsets.UTF_8)
                            + "&l=" + URLEncoder.encode(loc, StandardCharsets.UTF_8)
                            + "&fromage=7"; // past 7 days

                        page.navigate(url);
                        sessions.pause();

                        String html = page.content();
                        if (parser.isBotOrLoginWall(html)) {
                            log.warn("Indeed Cloudflare/CAPTCHA detected. Aborting Indeed scrape.");
                            return allJobs;
                        }

                        List<Job> scraped = parser.parse(html, Instant.now());
                        allJobs.addAll(scraped);
                        log.info("Indeed scraped {} jobs for role='{}', loc='{}'", scraped.size(), role, loc);
                    } catch (Exception e) {
                        log.error("Failed scraping Indeed for role='{}', loc='{}': {}", role, loc, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error opening Indeed Playwright session: {}", e.getMessage(), e);
        }

        return allJobs;
    }
}

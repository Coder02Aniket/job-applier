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
public class NaukriScraper implements JobScraper {

    private final PlaywrightSessionFactory sessions;
    private final AppProperties props;
    private final NaukriHtmlParser parser;

    @Override
    public JobSource source() {
        return JobSource.NAUKRI;
    }

    @Override
    public List<Job> fetch(CandidateProfile profile) {
        List<Job> allJobs = new ArrayList<>();
        List<String> roles = props.prefs().roles();
        List<String> locations = props.prefs().locations();

        if (roles == null || roles.isEmpty()) return allJobs;

        try (Playwright pw = sessions.create()) {
            BrowserContext context = sessions.open(pw, "naukri");
            Page page = context.newPage();

            for (String role : roles) {
                for (String loc : locations) {
                    try {
                        String query = role + " " + loc;
                        String url = "https://www.naukri.com/"
                            + URLEncoder.encode(role.toLowerCase().replace(' ', '-'), StandardCharsets.UTF_8)
                            + "-jobs-in-" + URLEncoder.encode(loc.toLowerCase().replace(' ', '-'), StandardCharsets.UTF_8);

                        page.navigate(url);
                        sessions.pause();

                        String html = page.content();
                        if (parser.isBotOrLoginWall(html)) {
                            log.warn("Naukri bot/login wall or CAPTCHA detected. Aborting Naukri scrape.");
                            return allJobs;
                        }

                        List<Job> scraped = parser.parse(html, Instant.now());
                        allJobs.addAll(scraped);
                        log.info("Naukri scraped {} jobs for query '{}'", scraped.size(), query);
                    } catch (Exception e) {
                        log.error("Failed scraping Naukri for role='{}', loc='{}': {}", role, loc, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error opening Naukri Playwright session: {}", e.getMessage(), e);
        }

        return allJobs;
    }
}

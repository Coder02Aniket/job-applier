package com.jobapplier.ingestion;

import com.jobapplier.job.Job;
import com.jobapplier.job.JobNormalizer;
import com.jobapplier.job.JobSource;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IndeedHtmlParser {

    public boolean isBotOrLoginWall(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase();
        return lower.contains("cloudflare") || lower.contains("cf-challenge")
            || lower.contains("enable cookies") || lower.contains("security check")
            || lower.contains("additional verification required") || lower.contains("captcha");
    }

    public List<Job> parse(String html, Instant postedAt) {
        if (html == null || html.isBlank() || isBotOrLoginWall(html)) {
            return List.of();
        }

        List<Job> jobs = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements cards = doc.select(".cardOutline, .job_seen_beacon, .result");

        for (Element card : cards) {
            Element titleLink = card.selectFirst("a.jcs-JobTitle, h2.jobTitle a");
            if (titleLink == null) continue;

            String title = titleLink.text().trim();
            String rawUrl = titleLink.attr("href");
            String cleanUrl = cleanIndeedUrl(rawUrl);

            Element compEl = card.selectFirst(".companyName, [data-testid=company-name], .company_location .companyName");
            String company = compEl != null ? compEl.text().trim() : "Unknown Company";

            Element locEl = card.selectFirst(".companyLocation, [data-testid=text-location]");
            String location = locEl != null ? locEl.text().trim() : "Remote / Unspecified";

            Element descEl = card.selectFirst(".job-snippet, .underCardSummary");
            String description = descEl != null ? descEl.text().trim() : "";

            Element recNameEl = card.selectFirst(".recruiter");
            String recruiterName = recNameEl != null ? recNameEl.text().trim() : null;

            Element recEmailEl = card.selectFirst(".recruiter-email");
            String recruiterEmail = recEmailEl != null ? recEmailEl.text().trim() : null;

            Job job = new Job();
            job.setSource(JobSource.INDEED);
            job.setTitle(title);
            job.setCompany(company);
            job.setLocation(location);
            job.setUrl(cleanUrl);
            job.setDescription(description);
            job.setRecruiterName(recruiterName);
            job.setRecruiterEmail(recruiterEmail);
            job.setPostedAt(postedAt != null ? postedAt : Instant.now());
            job.setDedupeHash(JobNormalizer.dedupeHash(company, title, location));

            jobs.add(job);
        }

        return jobs;
    }

    private String cleanIndeedUrl(String rawUrl) {
        try {
            if (rawUrl.startsWith("/")) {
                rawUrl = "https://www.indeed.com" + rawUrl;
            }
            URI uri = URI.create(rawUrl);
            String query = uri.getQuery();
            if (query != null && query.contains("jk=")) {
                for (String param : query.split("&")) {
                    if (param.startsWith("jk=")) {
                        return "https://www.indeed.com/viewjob?" + param;
                    }
                }
            }
            return "https://www.indeed.com" + uri.getPath();
        } catch (Exception e) {
            int q = rawUrl.indexOf('?');
            return q != -1 ? rawUrl.substring(0, q) : rawUrl;
        }
    }
}

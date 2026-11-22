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
public class LinkedInHtmlParser {

    public boolean isBotOrLoginWall(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase();
        return lower.contains("authwall") || lower.contains("checkpoint/challenge")
            || lower.contains("security verification") || lower.contains("please sign in to continue")
            || lower.contains("captcha");
    }

    public List<Job> parse(String html, Instant postedAt) {
        if (html == null || html.isBlank() || isBotOrLoginWall(html)) {
            return List.of();
        }

        List<Job> jobs = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements cards = doc.select(".base-card, .job-search-card, li.jobs-search-results__list-item");

        for (Element card : cards) {
            Element titleLink = card.selectFirst("a.base-card__full-link, a[data-tracking-control-name*=job-card], h3 a");
            if (titleLink == null) continue;

            String title = titleLink.text().trim();
            String rawUrl = titleLink.attr("href");
            String cleanUrl = cleanLinkedInUrl(rawUrl);

            Element compEl = card.selectFirst(".base-search-card__subtitle, h4 a, .job-search-card__company-name");
            String company = compEl != null ? compEl.text().trim() : "Unknown Company";

            Element locEl = card.selectFirst(".job-search-card__location, .base-search-card__metadata span");
            String location = locEl != null ? locEl.text().trim() : "Remote / Unspecified";

            Element descEl = card.selectFirst(".job-card-list__description, p");
            String description = descEl != null ? descEl.text().trim() : "";

            Element recNameEl = card.selectFirst(".job-poster__name");
            String recruiterName = recNameEl != null ? recNameEl.text().trim() : null;

            Element recEmailEl = card.selectFirst(".job-poster__email");
            String recruiterEmail = recEmailEl != null ? recEmailEl.text().trim() : null;

            Job job = new Job();
            job.setSource(JobSource.LINKEDIN);
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

    private String cleanLinkedInUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            return "https://www.linkedin.com" + uri.getPath();
        } catch (Exception e) {
            int q = rawUrl.indexOf('?');
            return q != -1 ? rawUrl.substring(0, q) : rawUrl;
        }
    }
}

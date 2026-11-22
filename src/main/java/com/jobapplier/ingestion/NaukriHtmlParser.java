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
public class NaukriHtmlParser {

    public boolean isBotOrLoginWall(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase();
        return lower.contains("security check") || lower.contains("captcha")
            || lower.contains("please verify you are a human") || lower.contains("access denied")
            || lower.contains("unusual activity");
    }

    public List<Job> parse(String html, Instant postedAt) {
        if (html == null || html.isBlank() || isBotOrLoginWall(html)) {
            return List.of();
        }

        List<Job> jobs = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements cards = doc.select(".srp-jobtuple-wrapper, .jobTuple");

        for (Element card : cards) {
            Element titleLink = card.selectFirst("a.title");
            if (titleLink == null) continue;

            String title = titleLink.text().trim();
            String rawUrl = titleLink.attr("href");
            String cleanUrl = cleanUrlQuery(rawUrl);

            Element compEl = card.selectFirst(".comp-name, .company, a[title]");
            String company = compEl != null ? compEl.text().trim() : "Unknown Company";

            Element locEl = card.selectFirst(".locWdth, .loc-wrap, .location");
            String location = locEl != null ? locEl.text().trim() : "Pune / Remote";

            Element descEl = card.selectFirst(".job-desc, .desc");
            String description = descEl != null ? descEl.text().trim() : "";

            Element recNameEl = card.selectFirst(".recruiter-name");
            String recruiterName = recNameEl != null ? recNameEl.text().trim() : null;

            Element recEmailEl = card.selectFirst(".recruiter-email");
            String recruiterEmail = recEmailEl != null ? recEmailEl.text().trim() : null;

            Job job = new Job();
            job.setSource(JobSource.NAUKRI);
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

    private String cleanUrlQuery(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            return uri.getScheme() + "://" + uri.getHost() + uri.getPath();
        } catch (Exception e) {
            int q = rawUrl.indexOf('?');
            return q != -1 ? rawUrl.substring(0, q) : rawUrl;
        }
    }
}

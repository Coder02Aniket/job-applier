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
public class GmailAlertParser {

    public List<Job> parseEmail(String from, String subject, String bodyHtml, Instant receivedAt) {
        if (bodyHtml == null || bodyHtml.isBlank()) {
            return List.of();
        }

        String lowerFrom = from == null ? "" : from.toLowerCase();
        String lowerSubject = subject == null ? "" : subject.toLowerCase();
        String lowerBody = bodyHtml.toLowerCase();

        if (lowerFrom.contains("linkedin") || lowerSubject.contains("linkedin") || lowerBody.contains("linkedin.com/jobs/view")) {
            return parseLinkedInAlert(bodyHtml, receivedAt);
        } else if (lowerFrom.contains("naukri") || lowerSubject.contains("naukri") || lowerBody.contains("naukri.com/job-listings")) {
            return parseNaukriAlert(bodyHtml, receivedAt);
        } else if (lowerFrom.contains("indeed") || lowerSubject.contains("indeed") || lowerBody.contains("indeed.com/viewjob")) {
            return parseIndeedAlert(bodyHtml, receivedAt);
        }

        log.debug("Unknown alert email from='{}', subject='{}'", from, subject);
        return List.of();
    }

    public List<Job> parseLinkedInAlert(String html, Instant receivedAt) {
        List<Job> jobs = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Elements links = doc.select("a[href*=/jobs/view/]");
        for (Element link : links) {
            String title = link.text().trim();
            if (title.isEmpty()) continue;

            String rawUrl = link.attr("href");
            String cleanUrl = cleanLinkedInUrl(rawUrl);

            Element container = findCardContainer(link);
            String company = extractText(container, ".company-name, .job-company, .company, span:matchesOwn((?i)company)");
            if (company.isEmpty()) {
                Element next = link.nextElementSibling();
                if (next != null) company = next.text().trim();
            }
            if (company.isEmpty()) company = "Unknown Company";

            String location = extractText(container, ".job-location, .location, span:matchesOwn((?i)location)");
            String description = extractText(container, ".job-snippet, .snippet, p");

            Job job = buildJob(title, company, location, cleanUrl, description, receivedAt);
            jobs.add(job);
        }
        return jobs;
    }

    public List<Job> parseNaukriAlert(String html, Instant receivedAt) {
        List<Job> jobs = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Elements links = doc.select("a[href*=naukri.com/job-listings]");
        for (Element link : links) {
            String title = link.text().trim();
            if (title.isEmpty()) continue;

            String rawUrl = link.attr("href");
            String cleanUrl = cleanUrlQuery(rawUrl);

            Element container = findCardContainer(link);
            String company = extractText(container, ".company, .org, .company-name");
            if (company.isEmpty()) company = "Unknown Company";

            String location = extractText(container, ".location, .loc, .city");
            String description = extractText(container, ".desc, .job-desc, .description, p");

            Job job = buildJob(title, company, location, cleanUrl, description, receivedAt);
            jobs.add(job);
        }
        return jobs;
    }

    public List<Job> parseIndeedAlert(String html, Instant receivedAt) {
        List<Job> jobs = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Elements links = doc.select("a[href*=indeed.com/viewjob], a[href*=indeed.com/rc/clk]");
        for (Element link : links) {
            String title = link.text().trim();
            if (title.isEmpty()) continue;

            String rawUrl = link.attr("href");
            String cleanUrl = cleanIndeedUrl(rawUrl);

            Element container = findCardContainer(link);
            String company = extractText(container, ".company, .companyName");
            if (company.isEmpty()) company = "Unknown Company";

            String location = extractText(container, ".location, .companyLocation");
            String description = extractText(container, ".summary, .jobSnippet, div.summary");

            Job job = buildJob(title, company, location, cleanUrl, description, receivedAt);
            jobs.add(job);
        }
        return jobs;
    }

    private Job buildJob(String title, String company, String location, String url, String description, Instant postedAt) {
        Job job = new Job();
        job.setSource(JobSource.GMAIL_ALERT);
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(location.isEmpty() ? "Remote / Unspecified" : location);
        job.setUrl(url);
        job.setDescription(description);
        job.setPostedAt(postedAt != null ? postedAt : Instant.now());
        job.setDedupeHash(JobNormalizer.dedupeHash(company, title, job.getLocation()));
        return job;
    }

    private Element findCardContainer(Element link) {
        Element curr = link.parent();
        for (int i = 0; i < 4 && curr != null; i++) {
            if (curr.hasClass("job-card") || curr.hasClass("job-tuple") || curr.hasClass("job-result")
                || curr.tagName().equalsIgnoreCase("tr") || curr.tagName().equalsIgnoreCase("li")) {
                return curr;
            }
            if (curr.parent() != null && curr.parent().childrenSize() > 1) {
                return curr;
            }
            curr = curr.parent();
        }
        return link.parent() != null ? link.parent() : link;
    }

    private String extractText(Element container, String cssQuery) {
        if (container == null) return "";
        Elements found = container.select(cssQuery);
        if (!found.isEmpty()) {
            return found.first().text().trim();
        }
        return "";
    }

    private String cleanLinkedInUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String path = uri.getPath();
            return "https://www.linkedin.com" + path;
        } catch (Exception e) {
            int q = rawUrl.indexOf('?');
            return q != -1 ? rawUrl.substring(0, q) : rawUrl;
        }
    }

    private String cleanIndeedUrl(String rawUrl) {
        try {
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
            return cleanUrlQuery(rawUrl);
        }
    }

    private String cleanUrlQuery(String rawUrl) {
        int q = rawUrl.indexOf('?');
        return q != -1 ? rawUrl.substring(0, q) : rawUrl;
    }
}

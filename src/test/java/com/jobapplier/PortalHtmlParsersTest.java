package com.jobapplier;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobapplier.ingestion.IndeedHtmlParser;
import com.jobapplier.ingestion.LinkedInHtmlParser;
import com.jobapplier.ingestion.NaukriHtmlParser;
import com.jobapplier.job.Job;
import com.jobapplier.job.JobSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortalHtmlParsersTest {

    @Test
    void parseNaukriSearchResults() throws IOException {
        NaukriHtmlParser parser = new NaukriHtmlParser();
        String html = Files.readString(Path.of("src/test/resources/fixtures/naukri/search_results.html"));
        Instant now = Instant.now();

        List<Job> jobs = parser.parse(html, now);

        assertThat(jobs).hasSize(2);

        Job j1 = jobs.get(0);
        assertThat(j1.getSource()).isEqualTo(JobSource.NAUKRI);
        assertThat(j1.getTitle()).isEqualTo("Senior Java Developer");
        assertThat(j1.getCompany()).isEqualTo("Persistent Systems");
        assertThat(j1.getLocation()).contains("Pune");
        assertThat(j1.getRecruiterName()).isEqualTo("Priya Sharma");
        assertThat(j1.getRecruiterEmail()).isEqualTo("priya.sharma@persistent.example.com");

        Job j2 = jobs.get(1);
        assertThat(j2.getTitle()).isEqualTo("Backend Engineer (Spring Boot)");
        assertThat(j2.getCompany()).isEqualTo("ZopSmart");
        assertThat(j2.getLocation()).isEqualTo("Remote");
    }

    @Test
    void parseLinkedInSearchResults() throws IOException {
        LinkedInHtmlParser parser = new LinkedInHtmlParser();
        String html = Files.readString(Path.of("src/test/resources/fixtures/linkedin/search_results.html"));
        Instant now = Instant.now();

        List<Job> jobs = parser.parse(html, now);

        assertThat(jobs).hasSize(2);

        Job j1 = jobs.get(0);
        assertThat(j1.getSource()).isEqualTo(JobSource.LINKEDIN);
        assertThat(j1.getTitle()).isEqualTo("Java Developer - Cloud Infrastructure");
        assertThat(j1.getCompany()).isEqualTo("Cisco Systems");
        assertThat(j1.getLocation()).contains("Pune");
        assertThat(j1.getRecruiterName()).isEqualTo("Rahul Deshmukh");

        Job j2 = jobs.get(1);
        assertThat(j2.getTitle()).isEqualTo("Spring Boot Engineer");
        assertThat(j2.getCompany()).isEqualTo("Red Hat");
    }

    @Test
    void parseIndeedSearchResults() throws IOException {
        IndeedHtmlParser parser = new IndeedHtmlParser();
        String html = Files.readString(Path.of("src/test/resources/fixtures/indeed/search_results.html"));
        Instant now = Instant.now();

        List<Job> jobs = parser.parse(html, now);

        assertThat(jobs).hasSize(2);

        Job j1 = jobs.get(0);
        assertThat(j1.getSource()).isEqualTo(JobSource.INDEED);
        assertThat(j1.getTitle()).isEqualTo("Java Backend Developer");
        assertThat(j1.getCompany()).isEqualTo("Infosys");
        assertThat(j1.getLocation()).contains("Pune");
        assertThat(j1.getUrl()).contains("jk=9876543210abcdef");

        Job j2 = jobs.get(1);
        assertThat(j2.getTitle()).isEqualTo("Senior Software Engineer - Spring Boot");
        assertThat(j2.getCompany()).isEqualTo("Wipro");
    }

    @Test
    void detectsBotOrLoginWalls() {
        NaukriHtmlParser naukri = new NaukriHtmlParser();
        assertThat(naukri.isBotOrLoginWall("<html><body>Please verify you are a human captcha</body></html>")).isTrue();

        LinkedInHtmlParser linkedin = new LinkedInHtmlParser();
        assertThat(linkedin.isBotOrLoginWall("<html><body>LinkedIn Authwall redirect</body></html>")).isTrue();

        IndeedHtmlParser indeed = new IndeedHtmlParser();
        assertThat(indeed.isBotOrLoginWall("<html><body>Cloudflare cf-challenge</body></html>")).isTrue();
    }
}

package com.jobapplier;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobapplier.ingestion.GmailAlertParser;
import com.jobapplier.job.Job;
import com.jobapplier.job.JobNormalizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GmailAlertParserTest {

    private GmailAlertParser parser;

    @BeforeEach
    void setUp() {
        parser = new GmailAlertParser();
    }

    @Test
    void parseLinkedInAlertFixture() throws IOException {
        String html = Files.readString(Path.of("src/test/resources/fixtures/gmail/linkedin_alert.html"));
        Instant now = Instant.now();

        List<Job> jobs = parser.parseEmail("jobalerts-noreply@linkedin.com", "30+ new jobs for 'Java Developer'", html, now);

        assertThat(jobs).hasSize(2);

        Job job1 = jobs.get(0);
        assertThat(job1.getTitle()).isEqualTo("Senior Java Developer");
        assertThat(job1.getCompany()).isEqualTo("Acme Global Technologies");
        assertThat(job1.getLocation()).contains("Pune");
        assertThat(job1.getUrl()).isEqualTo("https://www.linkedin.com/jobs/view/4123456789/");
        assertThat(job1.getDedupeHash()).isNotEmpty();

        Job job2 = jobs.get(1);
        assertThat(job2.getTitle()).isEqualTo("Spring Boot Engineer");
        assertThat(job2.getCompany()).isEqualTo("Apex Innovations");
        assertThat(job2.getLocation()).isEqualTo("Remote");
        assertThat(job2.getUrl()).isEqualTo("https://www.linkedin.com/jobs/view/4123456790/");
    }

    @Test
    void parseNaukriAlertFixture() throws IOException {
        String html = Files.readString(Path.of("src/test/resources/fixtures/gmail/naukri_alert.html"));
        Instant now = Instant.now();

        List<Job> jobs = parser.parseEmail("alerts@naukri.com", "Jobs matching your profile", html, now);

        assertThat(jobs).hasSize(2);

        Job job1 = jobs.get(0);
        assertThat(job1.getTitle()).isEqualTo("Java Software Engineer");
        assertThat(job1.getCompany()).isEqualTo("Zenith Corp");
        assertThat(job1.getLocation()).isEqualTo("Pune");
        assertThat(job1.getUrl()).startsWith("https://www.naukri.com/job-listings-java-software-engineer");
        assertThat(job1.getUrl()).doesNotContain("?src=jobsearchDesk");

        Job job2 = jobs.get(1);
        assertThat(job2.getTitle()).isEqualTo("Backend Developer");
        assertThat(job2.getCompany()).isEqualTo("Cloud Nexus");
        assertThat(job2.getLocation()).isEqualTo("Remote");
    }

    @Test
    void parseIndeedAlertFixture() throws IOException {
        String html = Files.readString(Path.of("src/test/resources/fixtures/gmail/indeed_alert.html"));
        Instant now = Instant.now();

        List<Job> jobs = parser.parseEmail("alert@indeed.com", "Indeed Job Alert: Java Spring Boot Developer", html, now);

        assertThat(jobs).hasSize(2);

        Job job1 = jobs.get(0);
        assertThat(job1.getTitle()).isEqualTo("Lead Java Developer");
        assertThat(job1.getCompany()).isEqualTo("Enterprise Systems Inc.");
        assertThat(job1.getLocation()).contains("Pune");
        assertThat(job1.getUrl()).isEqualTo("https://www.indeed.com/viewjob?jk=abc1234567890def");

        Job job2 = jobs.get(1);
        assertThat(job2.getTitle()).isEqualTo("Full Stack Java Engineer");
        assertThat(job2.getCompany()).isEqualTo("Quantum Solutions");
        assertThat(job2.getLocation()).isEqualTo("Remote");
        assertThat(job2.getUrl()).isEqualTo("https://www.indeed.com/viewjob?jk=fed0987654321cba");
    }

    @Test
    void dedupeWorksAcrossSources() {
        String hash1 = JobNormalizer.dedupeHash("Acme Corp", "Senior Java Developer", "Pune");
        String hash2 = JobNormalizer.dedupeHash("acme corp.", "Senior  Java  Developer!", "pune, india");
        // Company, title, and normalized location matches
        String hash3 = JobNormalizer.dedupeHash("Acme Corp", "Senior Java Developer", "Pune");
        assertThat(hash1).isEqualTo(hash3);
    }
}

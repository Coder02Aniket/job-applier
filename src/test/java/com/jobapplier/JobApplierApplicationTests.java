package com.jobapplier;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobapplier.apply.ApplicationStatus;
import com.jobapplier.apply.JobApplication;
import com.jobapplier.apply.JobApplicationRepository;
import com.jobapplier.job.Job;
import com.jobapplier.job.JobRepository;
import com.jobapplier.job.JobSource;
import com.jobapplier.llm.LlmClient;
import com.jobapplier.outreach.Outreach;
import com.jobapplier.outreach.OutreachRepository;
import com.jobapplier.outreach.OutreachStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class JobApplierApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private LlmClient llmClient;

    @Autowired
    private JobApplicationRepository applicationRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private OutreachRepository outreachRepository;

    @Autowired
    private org.springframework.boot.test.web.client.TestRestTemplate restTemplate;

    @Test
    void contextLoadsAndFlywayAppliesMigrations() {
        assertThat(applicationRepository.findByStatus(ApplicationStatus.PENDING_REVIEW)).isEmpty();
    }

    @Test
    void getProfileReturnsProfileWithoutRawText() {
        org.springframework.http.ResponseEntity<com.jobapplier.profile.ProfileResponse> response =
            restTemplate.getForEntity("/api/profile", com.jobapplier.profile.ProfileResponse.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Jane Doe");
        assertThat(response.getBody().email()).isEqualTo("jane.doe@example.com");
    }

    @Test
    void summaryCountsApplicationsAndOutreachApprovalMarksApproved() {
        Job job = new Job();
        job.setSource(JobSource.LINKEDIN);
        job.setTitle("Backend Engineer");
        job.setCompany("Acme");
        job.setLocation("Remote");
        job.setUrl("https://example.com/jobs/backend-engineer");
        job.setDedupeHash("test-" + UUID.randomUUID());
        job = jobRepository.save(job);

        JobApplication app = new JobApplication();
        app.setJob(job);
        app = applicationRepository.save(app);

        Outreach outreach = new Outreach();
        outreach.setJob(job);
        outreach.setRecipient("recruiter@acme.example.com");
        outreach.setSubject("Hello");
        outreach.setBody("Body");
        outreach = outreachRepository.save(outreach);
        Long outreachId = outreach.getId();

        try {
            org.springframework.http.ResponseEntity<Map> summary =
                restTemplate.getForEntity("/api/summary", Map.class);
            assertThat(summary.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(summary.getBody()).isNotNull();
            assertThat(((Number) summary.getBody().get("PENDING_REVIEW")).longValue())
                .isGreaterThanOrEqualTo(1);

            // Explicit approval moves the draft to APPROVED but never sends it.
            org.springframework.http.ResponseEntity<Outreach> approved = restTemplate.postForEntity(
                "/api/outreach/" + outreachId + "/approve", null, Outreach.class);
            assertThat(approved.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(outreachRepository.findById(outreachId).orElseThrow().getStatus())
                .isEqualTo(OutreachStatus.APPROVED);
        } finally {
            outreachRepository.deleteById(outreachId);
            applicationRepository.deleteById(app.getId());
            jobRepository.deleteById(job.getId());
        }
    }
}

package com.jobapplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.jobapplier.config.AppProperties;
import com.jobapplier.job.Job;
import com.jobapplier.llm.LlmClient;
import com.jobapplier.profile.CandidateProfile;
import com.jobapplier.tailoring.ResumeFactualValidator;
import com.jobapplier.tailoring.ResumeTailorService;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeTailorServiceTest {

    @Mock
    private LlmClient llmClient;

    private AppProperties props;
    private ResumeFactualValidator validator;
    private ResumeTailorService tailorService;
    private CandidateProfile profile;
    private Job job;

    @BeforeEach
    void setUp() {
        props = new AppProperties(null, "target/test-tailored", null, null, null, null, null);
        validator = new ResumeFactualValidator();
        tailorService = new ResumeTailorService(llmClient, props, validator);

        profile = new CandidateProfile(
            "Jane Doe",
            "jane@example.com",
            "+1 555-0199",
            List.of("java", "spring boot", "postgresql", "docker", "kubernetes", "aws"),
            List.of("Senior Software Engineer"),
            5,
            "Jane Doe\nExperienced Java Spring Boot developer with 5 years experience handling 2M requests/day."
        );

        job = new Job();
        job.setId(101L);
        job.setTitle("Senior Java Developer");
        job.setCompany("Acme Corp");
        job.setDescription("Spring Boot, Docker, Microservices");
    }

    @Test
    void acceptsValidTailoredResumeAndWritesDocx() throws Exception {
        String validTailoredText = "Jane Doe\n5 years experience in Java and Spring Boot handling 2M requests/day.\nSkills: Java, Spring Boot, Docker.";
        when(llmClient.complete(anyString(), anyString())).thenReturn(validTailoredText);

        Path docxPath = tailorService.tailor(profile, job);

        assertThat(docxPath).isNotNull();
        assertThat(Files.exists(docxPath)).isTrue();

        try (InputStream in = Files.newInputStream(docxPath); XWPFDocument doc = new XWPFDocument(in)) {
            List<String> lines = doc.getParagraphs().stream().map(XWPFParagraph::getText).toList();
            assertThat(lines).contains("Jane Doe");
            assertThat(lines).contains("Skills: Java, Spring Boot, Docker.");
        }
    }

    @Test
    void rejectsTailoredResumeWithInventedNumberAndFallsBackToOriginal() throws Exception {
        // 99% is not in original raw text
        String hallucinatedMetric = "Jane Doe\n5 years experience in Java and Spring Boot achieving 99% uptime.";
        when(llmClient.complete(anyString(), anyString())).thenReturn(hallucinatedMetric);

        Path docxPath = tailorService.tailor(profile, job);

        assertThat(docxPath).isNotNull();
        try (InputStream in = Files.newInputStream(docxPath); XWPFDocument doc = new XWPFDocument(in)) {
            String fullText = String.join("\n", doc.getParagraphs().stream().map(XWPFParagraph::getText).toList());
            assertThat(fullText).contains("2M requests/day"); // fell back to original text
            assertThat(fullText).doesNotContain("99% uptime");
        }
    }

    @Test
    void rejectsTailoredResumeWithInventedSkillAndFallsBackToOriginal() throws Exception {
        // Python / Snowflake not in profile
        String hallucinatedSkill = "Jane Doe\n5 years experience in Java, Spring Boot, Python, and Snowflake.";
        when(llmClient.complete(anyString(), anyString())).thenReturn(hallucinatedSkill);

        Path docxPath = tailorService.tailor(profile, job);

        assertThat(docxPath).isNotNull();
        try (InputStream in = Files.newInputStream(docxPath); XWPFDocument doc = new XWPFDocument(in)) {
            String fullText = String.join("\n", doc.getParagraphs().stream().map(XWPFParagraph::getText).toList());
            assertThat(fullText).doesNotContain("Snowflake");
        }
    }
}

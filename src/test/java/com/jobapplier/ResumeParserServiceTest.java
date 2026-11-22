package com.jobapplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.jobapplier.config.AppProperties;
import com.jobapplier.llm.LlmClient;
import com.jobapplier.profile.CandidateProfile;
import com.jobapplier.profile.ProfileResponse;
import com.jobapplier.profile.ResumeParserService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeParserServiceTest {

    @Mock
    private LlmClient llmClient;

    private AppProperties props;
    private ResumeParserService service;

    @BeforeEach
    void setUp() {
        AppProperties.Prefs prefs = new AppProperties.Prefs(
            List.of("Java Developer", "Backend Engineer"),
            List.of("Pune", "Remote"),
            7,
            0.35
        );
        props = new AppProperties(
            "src/test/resources/fixtures/resume.txt",
            "target/test-tailored",
            prefs,
            null,
            null,
            null,
            null
        );
        service = new ResumeParserService(props, llmClient);
    }

    @Test
    void parseWithLlmSuccess() {
        String mockJsonResponse = """
            {
              "name": "Jane Doe",
              "email": "jane.doe@example.com",
              "phone": "+1 555-0199",
              "skills": ["Java", "Spring Boot", "PostgreSQL", "Docker", "Kubernetes"],
              "titles": ["Senior Software Engineer", "Software Engineer"],
              "yearsExperience": 5
            }
            """;
        when(llmClient.complete(anyString(), anyString())).thenReturn(mockJsonResponse);

        CandidateProfile profile = service.parse(Path.of("src/test/resources/fixtures/resume.txt"));

        assertThat(profile.name()).isEqualTo("Jane Doe");
        assertThat(profile.email()).isEqualTo("jane.doe@example.com");
        assertThat(profile.phone()).isEqualTo("+1 555-0199");
        assertThat(profile.skills()).contains("Java", "Spring Boot", "PostgreSQL");
        assertThat(profile.titles()).contains("Senior Software Engineer");
        assertThat(profile.yearsExperience()).isEqualTo(5);
        assertThat(profile.rawText()).contains("Jane Doe");

        ProfileResponse response = ProfileResponse.from(profile);
        assertThat(response.name()).isEqualTo("Jane Doe");
        assertThat(response.email()).isEqualTo("jane.doe@example.com");
    }

    @Test
    void parseWithLlmMarkdownCodeFences() {
        String mockResponseWithMarkdown = """
            ```json
            {
              "name": "Jane Doe",
              "email": "jane.doe@example.com",
              "phone": "+1 555-0199",
              "skills": ["Java", "Spring Boot"],
              "titles": ["Backend Engineer"],
              "yearsExperience": 5
            }
            ```
            """;
        when(llmClient.complete(anyString(), anyString())).thenReturn(mockResponseWithMarkdown);

        CandidateProfile profile = service.parse(Path.of("src/test/resources/fixtures/resume.txt"));

        assertThat(profile.name()).isEqualTo("Jane Doe");
        assertThat(profile.skills()).contains("Java", "Spring Boot");
        assertThat(profile.yearsExperience()).isEqualTo(5);
    }

    @Test
    void parseFallbackWhenLlmFails() {
        when(llmClient.complete(anyString(), anyString())).thenThrow(new RuntimeException("LLM service unavailable"));

        CandidateProfile profile = service.parse(Path.of("src/test/resources/fixtures/resume.txt"));

        assertThat(profile.name()).isEqualTo("Jane Doe");
        assertThat(profile.email()).isEqualTo("jane.doe@example.com");
        assertThat(profile.phone()).contains("555-0199");
        assertThat(profile.skills()).contains("java", "spring boot", "postgresql", "docker");
        assertThat(profile.titles()).contains("Java Developer");
        assertThat(profile.yearsExperience()).isEqualTo(0);
    }
}

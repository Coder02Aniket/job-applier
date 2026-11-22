package com.jobapplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import com.jobapplier.config.AppProperties;
import com.jobapplier.job.Job;
import com.jobapplier.job.JobRepository;
import com.jobapplier.profile.CandidateProfile;
import com.jobapplier.ranking.RankingService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private JobRepository jobRepository;

    private AppProperties props;
    private RankingService rankingService;
    private CandidateProfile profile;

    @BeforeEach
    void setUp() {
        AppProperties.Prefs prefs = new AppProperties.Prefs(
            List.of("Java Developer", "Spring Boot Developer"),
            List.of("Pune", "Remote"),
            7,
            0.35
        );
        AppProperties.Limits limits = new AppProperties.Limits(10, 10, 10, 6);
        props = new AppProperties(null, null, prefs, limits, null, null, null);

        rankingService = new RankingService(jobRepository, props);

        profile = new CandidateProfile(
            "Jane Doe",
            "jane@example.com",
            "+15550199",
            List.of("java", "spring boot", "postgresql", "docker", "kubernetes", "aws"),
            List.of("Senior Software Engineer"),
            5,
            "Experienced Java Spring Boot developer skilled in PostgreSQL, Docker, Kubernetes, AWS, REST APIs."
        );
    }

    @Test
    void scoreHighMatchingRecentJob() {
        Job job = new Job();
        job.setTitle("Senior Java Spring Boot Developer");
        job.setCompany("Acme Corp");
        job.setLocation("Pune");
        job.setDescription("Looking for Java, Spring Boot, PostgreSQL, Docker, Kubernetes engineer");
        job.setPostedAt(Instant.now().minus(2, ChronoUnit.HOURS));

        double score = rankingService.score(job, profile);

        assertThat(score).isGreaterThan(0.5);
    }

    @Test
    void scoreZeroForBlacklistedTitle() {
        Job job = new Job();
        job.setTitle("Java Developer Intern");
        job.setCompany("Acme Corp");
        job.setLocation("Pune");
        job.setDescription("Java intern position");
        job.setPostedAt(Instant.now());

        double score = rankingService.score(job, profile);

        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void scoreZeroForDisallowedLocation() {
        Job job = new Job();
        job.setTitle("Java Developer");
        job.setCompany("Acme Corp");
        job.setLocation("London, UK");
        job.setDescription("Java Spring Boot");
        job.setPostedAt(Instant.now());

        double score = rankingService.score(job, profile);

        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void scoreZeroForOldJob() {
        Job job = new Job();
        job.setTitle("Java Developer");
        job.setCompany("Acme Corp");
        job.setLocation("Pune");
        job.setDescription("Java Spring Boot");
        job.setPostedAt(Instant.now().minus(10, ChronoUnit.DAYS));

        double score = rankingService.score(job, profile);

        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void topTodayQueriesRepositoryWithCutoffAndMinScore() {
        Job j = new Job();
        j.setTitle("Java Developer");
        j.setScore(0.8);

        when(jobRepository.findByPostedAtAfterAndScoreGreaterThanOrderByScoreDesc(any(Instant.class), anyDouble(), any(Pageable.class)))
            .thenReturn(List.of(j));

        List<Job> top = rankingService.topToday();

        assertThat(top).hasSize(1);
        assertThat(top.get(0).getTitle()).isEqualTo("Java Developer");
    }
}

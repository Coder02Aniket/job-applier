package com.jobapplier.ranking;

import com.jobapplier.config.AppProperties;
import com.jobapplier.job.Job;
import com.jobapplier.job.JobRepository;
import com.jobapplier.profile.CandidateProfile;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private static final List<String> DEFAULT_TITLE_BLACKLIST = List.of("intern", "trainee", "director", "vp", "vice president", "cto");

    private final JobRepository jobs;
    private final AppProperties props;

    public double score(Job job, CandidateProfile profile) {
        if (job == null || profile == null) return 0.0;

        String title = job.getTitle() == null ? "" : job.getTitle().toLowerCase();
        String location = job.getLocation() == null ? "" : job.getLocation().toLowerCase();
        String description = job.getDescription() == null ? "" : job.getDescription().toLowerCase();

        // 1. Hard Filter: Title Blacklist
        for (String blacklisted : DEFAULT_TITLE_BLACKLIST) {
            if (title.contains(blacklisted)) {
                log.debug("Job id={} title '{}' matches blacklist '{}'", job.getId(), job.getTitle(), blacklisted);
                return 0.0;
            }
        }

        // 2. Hard Filter: Location Match
        List<String> allowedLocations = props.prefs() != null ? props.prefs().locations() : List.of();
        if (allowedLocations != null && !allowedLocations.isEmpty() && !location.isEmpty()) {
            boolean locMatch = allowedLocations.stream()
                .map(String::toLowerCase)
                .anyMatch(loc -> location.contains(loc) || loc.contains("remote") && location.contains("remote"));
            if (!locMatch) {
                log.debug("Job id={} location '{}' does not match allowed locations {}", job.getId(), job.getLocation(), allowedLocations);
                return 0.0;
            }
        }

        // 3. Hard Filter: Max Age
        Instant postedAt = job.getPostedAt() != null ? job.getPostedAt() : Instant.now();
        double ageDays = Math.max(0, Duration.between(postedAt, Instant.now()).toHours() / 24.0);
        int maxAge = props.prefs() != null ? props.prefs().maxAgeDays() : 7;
        if (ageDays > maxAge) {
            return 0.0;
        }

        // 4. Keyword Match Score
        List<String> candidateSkills = profile.skills() != null ? profile.skills() : List.of();
        double keywordScore = 0.0;
        if (!candidateSkills.isEmpty()) {
            String fullJdText = title + " " + description;
            long matches = candidateSkills.stream()
                .filter(skill -> fullJdText.contains(skill.toLowerCase()))
                .count();
            keywordScore = (double) matches / candidateSkills.size();
        }

        // 5. Semantic / Role Relevance Score (Cosine Jaccard Overlap of Tokens)
        double semanticScore = calculateTokenSimilarity(title + " " + description, profile.rawText());

        // Composite match score (70% keyword, 30% semantic token overlap)
        double matchScore = (0.7 * keywordScore) + (0.3 * semanticScore);

        // 6. Recency Decay Factor: exp(-days / 5.0)
        double recencyDecay = Math.exp(-ageDays / 5.0);

        return matchScore * recencyDecay;
    }

    public void rankAll(CandidateProfile profile) {
        jobs.findAll().forEach(j -> {
            j.setScore(score(j, profile));
            jobs.save(j);
        });
    }

    public List<Job> topToday() {
        int maxAge = props.prefs() != null ? props.prefs().maxAgeDays() : 7;
        double minScore = props.prefs() != null ? props.prefs().minScore() : 0.35;
        int topN = props.limits() != null ? props.limits().topNPerDay() : 15;

        Instant cutoff = Instant.now().minus(Duration.ofDays(maxAge));
        return jobs.findByPostedAtAfterAndScoreGreaterThanOrderByScoreDesc(
            cutoff, minScore, PageRequest.of(0, topN));
    }

    private double calculateTokenSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isBlank() || text2.isBlank()) {
            return 0.0;
        }
        Set<String> set1 = tokenize(text1);
        Set<String> set2 = tokenize(text2);
        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
            .filter(t -> t.length() > 2)
            .collect(Collectors.toSet());
    }
}

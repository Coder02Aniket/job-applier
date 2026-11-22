package com.jobapplier.tailoring;

import com.jobapplier.profile.CandidateProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResumeFactualValidator {

    private static final Pattern NUMBERS = Pattern.compile("\\b\\d+(?:\\.\\d+)?%?\\b|\\b\\d+[kKMmBb]\\b");
    private static final Pattern WORDS = Pattern.compile("\\b[a-zA-Z]{3,}\\b");

    /**
     * Validates that the tailored text contains no invented metrics, numbers, or unapproved skills/entities.
     * Returns true if valid, false if hallucinated facts were detected.
     */
    public boolean validate(String tailoredText, CandidateProfile profile) {
        if (tailoredText == null || profile == null) return false;
        String rawOriginal = profile.rawText() == null ? "" : profile.rawText();

        // 1. Validate Numbers & Metrics
        List<String> tailoredNumbers = extractNumbers(tailoredText);
        Set<String> originalNumbers = new HashSet<>(extractNumbers(rawOriginal));

        for (String num : tailoredNumbers) {
            if (!originalNumbers.contains(num)) {
                log.warn("Fact validation failed: Invented metric/number '{}' found in tailored resume.", num);
                return false;
            }
        }

        // 2. Validate Tech Skills / Specific Keywords
        // Any skill declared in tailored text that is not in profile skills and not in original rawText
        Set<String> originalWordsLower = new HashSet<>(extractWordsLower(rawOriginal));
        if (profile.skills() != null) {
            profile.skills().forEach(s -> originalWordsLower.add(s.toLowerCase()));
        }

        // Check for common tech keywords in tailored output
        List<String> tailoredWords = extractWordsLower(tailoredText);
        for (String word : tailoredWords) {
            // Check if it's a tech skill / noun token that is completely novel
            if (isTechSkillCandidate(word) && !originalWordsLower.contains(word)) {
                log.warn("Fact validation failed: Invented skill/keyword '{}' found in tailored resume.", word);
                return false;
            }
        }

        return true;
    }

    private List<String> extractNumbers(String text) {
        List<String> list = new ArrayList<>();
        Matcher m = NUMBERS.matcher(text);
        while (m.find()) {
            list.add(m.group());
        }
        return list;
    }

    private List<String> extractWordsLower(String text) {
        List<String> list = new ArrayList<>();
        Matcher m = WORDS.matcher(text.toLowerCase());
        while (m.find()) {
            list.add(m.group());
        }
        return list;
    }

    private boolean isTechSkillCandidate(String word) {
        // High-signal technical terms often hallucinated
        return Set.of(
            "aws", "azure", "gcp", "docker", "kubernetes", "kafka", "redis", "elasticsearch",
            "react", "angular", "vue", "python", "golang", "ruby", "csharp", "graphql",
            "terraform", "jenkins", "spark", "hadoop", "snowflake"
        ).contains(word);
    }
}

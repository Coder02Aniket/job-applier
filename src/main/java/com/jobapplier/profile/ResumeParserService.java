package com.jobapplier.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobapplier.config.AppProperties;
import com.jobapplier.llm.LlmClient;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

/** Parses the resume into a CandidateProfile. Source of truth for tailoring (no invented facts). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParserService {

    private static final List<String> SKILL_VOCAB = List.of(
        "java", "spring boot", "spring", "hibernate", "jpa", "react", "rest", "microservices",
        "kafka", "docker", "kubernetes", "aws", "postgresql", "mysql", "sql", "junit", "maven", "git");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern PHONE = Pattern.compile("(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{2,4}\\)?[-.\\s]?\\d{3,4}(?:[-.\\s]?\\d{3,4})?");

    private static final String JSON_EXTRACTION_SYSTEM = """
        You are a resume information extraction system. Extract candidate details from the provided resume text into a strict JSON object with these exact keys:
        "name": string (full name of the candidate, or null),
        "email": string (email address, or null),
        "phone": string (phone number, or null),
        "skills": array of strings (technical skills found in the resume),
        "titles": array of strings (job titles held by the candidate),
        "yearsExperience": integer (estimated total years of professional experience, or 0)

        Return ONLY the raw JSON object without markdown fences, explanation, or extra characters.""";

    private final AppProperties props;
    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Tika tika = new Tika();
    private volatile CandidateProfile cached;

    public CandidateProfile getProfile() {
        if (cached == null) cached = parse(Path.of(props.resumePath()));
        return cached;
    }

    public CandidateProfile parse(Path resume) {
        try (InputStream in = Files.newInputStream(resume)) {
            String text = tika.parseToString(in);
            return extract(text);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse resume at " + resume, e);
        }
    }

    public CandidateProfile extract(String text) {
        String lower = text.toLowerCase();
        List<String> vocabSkills = SKILL_VOCAB.stream().filter(lower::contains).toList();
        Matcher emailMatcher = EMAIL.matcher(text);
        String fallbackEmail = emailMatcher.find() ? emailMatcher.group() : null;
        Matcher phoneMatcher = PHONE.matcher(text);
        String fallbackPhone = phoneMatcher.find() ? phoneMatcher.group() : null;

        try {
            String response = llm.complete(JSON_EXTRACTION_SYSTEM, text);
            if (response != null && !response.isBlank()) {
                String cleanJson = stripMarkdown(response.trim());
                JsonNode root = mapper.readTree(cleanJson);
                String name = root.hasNonNull("name") ? root.get("name").asText() : extractFirstLine(text);
                String email = root.hasNonNull("email") ? root.get("email").asText() : fallbackEmail;
                String phone = root.hasNonNull("phone") ? root.get("phone").asText() : fallbackPhone;
                int years = root.hasNonNull("yearsExperience") ? root.get("yearsExperience").asInt(0) : 0;

                List<String> skills = new ArrayList<>();
                if (root.has("skills") && root.get("skills").isArray()) {
                    for (JsonNode s : root.get("skills")) {
                        skills.add(s.asText());
                    }
                }
                if (skills.isEmpty()) {
                    skills = vocabSkills;
                }

                List<String> titles = new ArrayList<>();
                if (root.has("titles") && root.get("titles").isArray()) {
                    for (JsonNode t : root.get("titles")) {
                        titles.add(t.asText());
                    }
                }
                if (titles.isEmpty() && props.prefs() != null && props.prefs().roles() != null) {
                    titles = props.prefs().roles();
                }

                return new CandidateProfile(name, email, phone, skills, titles, years, text);
            }
        } catch (Exception e) {
            log.warn("LLM extraction failed, using heuristic vocabulary fallback: {}", e.getMessage());
        }

        List<String> defaultRoles = props.prefs() != null && props.prefs().roles() != null
            ? props.prefs().roles() : Collections.emptyList();
        return new CandidateProfile(extractFirstLine(text), fallbackEmail, fallbackPhone, vocabSkills, defaultRoles, 0, text);
    }

    private String stripMarkdown(String raw) {
        if (raw.startsWith("```")) {
            int firstNewline = raw.indexOf('\n');
            int lastBackticks = raw.lastIndexOf("```");
            if (firstNewline != -1 && lastBackticks > firstNewline) {
                return raw.substring(firstNewline + 1, lastBackticks).trim();
            }
        }
        return raw;
    }

    private String extractFirstLine(String text) {
        if (text == null) return null;
        for (String line : text.split("\r?\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) return trimmed;
        }
        return null;
    }
}
